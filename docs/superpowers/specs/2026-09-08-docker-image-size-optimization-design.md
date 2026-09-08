# Docker image size optimization (tbmq, tbmq-node, tbmq-integration-executor)

Date: 2026-09-08
Status: spec — awaiting review

## What / Why

**Problem.** The three published TBMQ images are large:

```
thingsboard/tbmq-node:2.4.0                    934MB
thingsboard/tbmq:2.4.0                         778MB
thingsboard/tbmq-integration-executor:2.4.0    664MB
```

Measured layer-by-layer (`docker history`, amd64), the size breaks down as:

| Layer | tbmq | tbmq-node | tbmq-ie |
|---|---|---|---|
| `debian:trixie-slim` | 78.6 | 78.6 | 78.6 |
| base image user/`procps` layer | 9.8 | 9.8 | 9.8 |
| `openjdk-25-jdk-headless` | 356 | 356 | 356 |
| `COPY *.deb` | 141 | 141 | 86.8 |
| `apt-get update` + `curl` | *(folded below)* | 36.2 | 36.2 |
| `dpkg -i` (the real payload) | 192 | 156 | 96.1 |
| `chmod 555` rewriting the 149MB jar | — | 156 | — |
| **total** | **778** | **934** | **664** |

Four independent sources of waste, of which this spec addresses three:

1. **The `.deb` is paid for twice** — once as its own `COPY` layer, then again when
   `dpkg` extracts it. The `rm /tmp/*.deb` in a later `RUN` reclaims nothing, because
   image layers are additive.
2. **`tbmq-node` splits one logical install across five `RUN` layers**
   (`msa/mqtt-broker/docker/Dockerfile:21-35`). The trailing
   `chmod 555 .../thingsboard-mqtt-broker.jar` rewrites all 149MB of the jar into a
   fresh layer, so `tbmq-node` carries the payload **three** times.
3. **`apt-get update` lists are never cleaned** and `curl` is installed without
   `--no-install-recommends`, costing 36MB per image instead of ~4MB.
4. **The JDK is shipped where a JRE would run the app** — 356MB, of which `jmods`
   (86MB), `ct.sym` (11MB) and the `javac`/`jshell`/`javadoc` toolchain are never used
   at runtime. **Explicitly out of scope** — see *Rejected alternatives*.

**Reference point.** ThingsBoard builds the same kind of images from the same base
(`docker.base.image = thingsboard/openjdk25:trixie-slim`, `msa/pom.xml:39` there vs
`msa/pom.xml:43` here). Across its 13 Dockerfiles it consistently:

- collapses the whole install into **one** `RUN`;
- **never installs `curl`** (zero occurrences);
- always `rm -rf /var/lib/apt/lists/*` where `apt` is used, with `--no-install-recommends`;
- does **not** use multi-stage builds (so it carries the same duplicated `.deb` layer).

Items 2 and 3 above are therefore pure drift from ThingsBoard's own convention. Item 1
is a departure from it, but is a layer-topology change with no runtime effect and is the
single largest non-JDK win available.

## Goal

Reduce the three images as far as possible **without changing the Java runtime** and
without altering the contents or permissions of what ships inside them.

Expected landing:

| Image | Before | After | Delta |
|---|---|---|---|
| `tbmq` | 778MB | ~597MB | −23% |
| `tbmq-node` | 934MB | ~597MB | −36% |
| `tbmq-integration-executor` | 664MB | ~539MB | −19% |

Derivation, from the measured layer table above. `tbmq` and `tbmq-node` install the same
package, so both converge on the same payload (156MB extracted) over the same 444MB base:

- `tbmq`: −141 (`.deb` layer) −36 (apt lists + `curl`, folded into its single `RUN`, i.e.
  192 − 156) −4.25 (jar trim) = **−181**
- `tbmq-node`: −141 (`.deb` layer) −36.2 (apt layer) −156 (`chmod` duplicate) −4.25 (jar
  trim) = **−337**
- `tbmq-integration-executor`: −86.8 (`.deb` layer) −36.2 (apt layer) −2.0 (jar trim, see
  below) = **−125**

The IE jar carries only `lombok` of the two trimmed dependencies — it does not declare
`tink` — so its jar saving is 1.95MB, not 4.25MB.

The residual ~444MB (78.6 Debian + 9.8 base user layer + 356 JDK) is base image left
untouched by design.

## Scope

In scope:

1. Restructure all three Dockerfiles as two-stage builds (see *Design*).
2. Drop `curl` and the `apt-get` invocations from all three Dockerfiles.
3. Trim two dead runtime dependencies out of the fat jars.

Out of scope:

- Any change to the JVM, the base image, or `docker.base.image`.
- `thingsboard/tbmq-pe` (a separate repo, same Dockerfile shape and the same dead
  `tink` dependency — worth a follow-up).
- Aggressive dependency removal that trades a feature for size (see *Rejected
  alternatives*).
- Replacing `io.netty:netty-all` with the specific netty modules — dropped for being worth
  only ~0.5MB against the only silent failure mode in the audit (see *Rejected
  alternatives*).

## Design

### Dockerfile pattern

Two stages. The **builder** stage runs exactly today's `dpkg -i`, so the install tree is
produced by the package's own logic rather than reconstructed by hand. The **runtime**
stage copies the tree that `dpkg` produced. The `.deb` never enters the final image, so
it is paid for once instead of twice, and there is no intermediate cruft to clean up.

Shown for `tbmq`; the other two differ only as noted under *Per-image specifics*.

```dockerfile
FROM ${docker.base.image} AS builder

COPY logback.xml ${pkg.name}.conf ${pkg.name}.deb /tmp/

RUN dpkg -i /tmp/${pkg.name}.deb \
    && mv /tmp/logback.xml ${pkg.installFolder}/conf \
    && mv /tmp/${pkg.name}.conf ${pkg.installFolder}/conf \
    && chmod 555 ${pkg.installFolder}/bin/${pkg.name}.jar

FROM ${docker.base.image}

ENV DATA_FOLDER=/data

COPY start-tbmq.sh install-tbmq.sh upgrade-tbmq.sh /usr/bin/
COPY --from=builder ${pkg.installFolder} ${pkg.installFolder}

RUN chmod a+x /usr/bin/start-tbmq.sh /usr/bin/install-tbmq.sh /usr/bin/upgrade-tbmq.sh \
    && chown ${pkg.user}:${pkg.user} ${pkg.installFolder} \
    && mkdir -p /etc/${pkg.name} \
    && ln -s ${pkg.installFolder}/conf /etc/${pkg.name}/conf \
    && mkdir -p $DATA_FOLDER ${pkg.unixLogFolder} \
    && chown -R ${pkg.user}:${pkg.user} $DATA_FOLDER ${pkg.unixLogFolder} /tmp

USER ${pkg.user}

VOLUME ["/data"]

CMD ["start-tbmq.sh"]
```

### Constraints this design respects

Each was verified experimentally, not assumed:

- **No BuildKit-only syntax.** Local builds go through the Spotify
  `dockerfile-maven-plugin` (extension declared at `msa/pom.xml:116`, configured per
  module e.g. `msa/tbmq/pom.xml:104`), which posts to the Docker daemon's legacy build
  API. Release builds go through `docker buildx build
  --platform=linux/amd64,linux/arm64` (`msa/pom.xml:74` and `:95`). Multi-stage is
  supported by both; `RUN --mount=type=bind` — the other way to avoid the `.deb` layer —
  is **BuildKit-only** and would silently work in the release path while breaking local
  builds. Verified: the pattern above builds with `DOCKER_BUILDKIT=0`.
- **`COPY --from` sets the destination's top directory to `root:root`** while preserving
  uid/gid and modes of everything beneath it. Hence the explicit
  `chown ${pkg.user}:${pkg.user} ${pkg.installFolder}` — omitting it silently changes
  the install folder's owner from `thingsboard` to `root`.
- **`chown -R` in the builder is wrong.** Today the two config files moved in from
  `/tmp` (`logback.xml`, `${pkg.name}.conf`) stay `root:root` mode `-rw-rw-r--`. A
  `chown -R` over the install folder flips them to `thingsboard:thingsboard`, making
  them group-writable by the process that reads them. Applying only the `chmod 555` and
  letting `COPY` preserve everything else reproduces today's ownership exactly.
- **Absolute symlinks inside the install tree are preserved.** All three packages ship
  `bin/${pkg.name}.yml -> ${pkg.installFolder}/conf/${pkg.name}.yml`.
- **`/var/log/${pkg.name}` must be created explicitly.** Today it comes from the
  package's postinst, which no longer runs in the final image. `tbmq`'s
  `logback.xml:24` writes to `/var/log/thingsboard-mqtt-broker/`, so this is load-bearing.

### What is dropped from the final image, and why it is safe

- **The `.deb` file.** Nothing reads it after installation.
- **`/lib/systemd/system/${pkg.name}.service`.** Already disabled at build time by the
  current `systemctl --no-reload disable` line, and inert in a container.
- **The `dpkg` database entry** for the package. Nothing in the repo or in the
  `tbmq-pe-k8s` / `tbmq-pe-docker-compose` deployments queries it.
- **`curl`.** ThingsBoard installs it in none of its Dockerfiles; nothing in `tbmq`,
  `tbmq-pe-k8s` or `tbmq-pe-docker-compose` invokes it, and every k8s probe there is
  `tcpSocket`, not HTTP. Dropping it is also what removes the `apt` layer entirely
  rather than merely shrinking it.

`dpkg -L` confirms the package installs only 34 paths: the `${pkg.installFolder}` tree,
the `/etc/${pkg.name}/conf` symlink into it, and the systemd unit. The symlink is
recreated above for fidelity even though nothing in the repo references it.

### Per-image specifics

| Dockerfile | Package | Notes |
|---|---|---|
| `msa/tbmq/docker/Dockerfile` | `thingsboard-mqtt-broker` | As shown. Copies `logback.xml` + `.conf` overrides and three `/usr/bin` scripts. |
| `msa/mqtt-broker/docker/Dockerfile` | `thingsboard-mqtt-broker` | No config overrides; one script (`start-tb-mqtt-broker.sh`). Keeps `/data` + `VOLUME`. Collapsing its five `RUN`s is what removes the 156MB duplicate. |
| `msa/integration/executor/docker/Dockerfile` | `tbmq-integration-executor` | No config overrides; one script. **No `DATA_FOLDER`, no `VOLUME`** — do not add them. Log folder is `/var/log/tbmq-integration-executor`. |

### Dependency trimming

Audit of the 149MB fat jar (201 nested jars) found two items trimmable with no functional
change:

| Change | File | Saving |
|---|---|---|
| Remove `com.google.crypto.tink:tink` | `application/pom.xml:130`, plus `dependencyManagement` at `pom.xml:954` and the `google-crypto-tink.version` property at `pom.xml:74` | 2.30MB |
| Exclude `lombok` from the Boot jar | `spring-boot-maven-plugin` config, `pom.xml:415` | 1.95MB |

Rationale:

- **`tink`** has zero references anywhere in the codebase — an inherited leftover from
  ThingsBoard, which carries the same dead declaration.
- **`lombok`** is correctly scoped `provided` (`pom.xml:790`) but
  `spring-boot-maven-plugin` packages `provided`-scope dependencies into the fat jar by
  default, so it ships to production in **both** the application jar and the
  integration-executor jar. This is a defect independent of image size.

Kept, despite looking like candidates:

- **`protobuf-java`** (1.79MB) — `dao` and `common/queue` both declare it directly, and
  `prometheus-metrics-exposition-formats` shades its own separate copy.
- **`netty-transport-native-epoll`** — no code references `Epoll` directly, but Reactor
  Netty auto-detects and uses it when present on the classpath.
- **`io.netty:netty-all`** — see *Rejected alternatives*.

## Verification

Per image:

1. Build old and new from an identical context; record both sizes.
2. Diff the filesystems: `find ${pkg.installFolder} -printf "%M %u:%g %s %p\n" | sort`,
   plus `ls -ld` on `/data`, `/var/log/${pkg.name}`, `/tmp`, the `/etc/${pkg.name}`
   symlink, `ls -l /usr/bin/*`, `env`, and `java -version`. Expect **no differences
   other than mtimes**.
3. Boot the image and confirm it reaches the same startup point as the old one.

Then, once across the set:

4. `msa/tbmq/configs/docker-compose.yml` brought up against the rebuilt `tbmq` monolith
   image (Postgres + Kafka + Valkey), driven through an MQTT connect/publish/subscribe.
5. `docker/docker-compose.yml` brought up against the rebuilt `tbmq-node` and
   `tbmq-integration-executor` images (two-node cluster + two IE instances).
6. One `docker buildx build --platform=linux/amd64,linux/arm64` to confirm the
   multi-arch release path (`msa/pom.xml:74`, `:95`) still works.

`msa/black-box-tests` is **not** usable for this: the module has no `src` directory and is
not registered in any `<modules>` list — only a stale `target/black-box-tests-2.0.1-SNAPSHOT.jar`
remains. The compose bring-ups above are the real end-to-end coverage available.

### Evidence from the prototype

The pattern was prototyped against the real `.deb` in
`msa/tbmq/target/docker/`, built with `DOCKER_BUILDKIT=0`:

```
proto-tbmq-base   764MB    (current Dockerfile)
proto-tbmq-new    589MB    (-175MB, -23%)
```

Filesystem diff between the two: **mtimes only**. Both boot identically — Spring starts,
finds 18 JPA repositories, then fails on the absent Postgres with the same
`JDBCConnectionException`.

## Rejected alternatives

**Replace the JDK with a JRE or a `jlink` runtime (−265MB, the largest single win).**
Rejected: the runtime must not change. Measured for the record:

- A `jlink` image containing every `java.*`/`jdk.*` runtime module, `zip-6` compressed,
  is 91MB against the JDK's 331MB. It keeps `jcmd`/`jstack`/`jmap`/`jfr` and drops only
  the compiler toolchain.
- Debian's `openjdk-25-jre-headless` is 234MB. Purging `openjdk-25-jdk-headless` leaves
  a byte-identical `java` and `libjvm.so` (md5-verified) — but its `bin/` then holds
  only `java`, `keytool`, `jpackage`, `rmiregistry`. **No `jcmd`, `jstack`, `jmap`,
  `jfr`**, i.e. no thread or heap dumps in production. This is the likely reason
  ThingsBoard ships the JDK image, and a good reason to keep it.

Either would also require moving the runtime stage off `thingsboard/openjdk25:trixie-slim`
(purging in a later layer reclaims nothing), which means re-creating the `thingsboard`
user at uid/gid 799 and re-applying the base image's `networkaddress.cache.ttl=60` patch
to `java.security` — `jlink` silently drops it, while correctly resolving Debian's
`cacerts` symlink into a real 134KB truststore.

If this is ever revisited, the right place is a `-jre` variant of the
`thingsboard/openjdk25` base image in its own repo, so ThingsBoard benefits too and no
Dockerfile here changes its base.

**Replacing `io.netty:netty-all` with the specific netty modules (~0.5MB).** Rejected on
cost/benefit: the smallest win in the audit paired with the only silent failure mode in it.

`application/pom.xml:55` declares `netty-all` alongside the three specific modules it
duplicates (`netty-handler`, `netty-codec-mqtt`, `netty-transport-native-epoll` at
`application/pom.xml:47-66`), and main-source imports cover only `bootstrap`, `buffer`,
`channel{,.nio,.socket}`, `handler.{codec,ipfilter,ssl,timeout}` and `util{,.concurrent}` —
all satisfied by those three. Replacing it would drop ~12 modules unusable in a Linux
container: `netty-transport-native-epoll:linux-riscv64`, `netty-transport-native-kqueue`
(osx-x86_64, osx-aarch_64), `netty-resolver-dns-native-macos` (both osx classifiers) plus
`netty-resolver-dns-classes-macos`, `netty-transport-{sctp,udt,rxtx}`,
`netty-codec-{memcache,stomp,smtp,xml,haproxy}` and `netty-handler-ssl-ocsp`.
`netty-codec-http`/`http2`/`socks`/`resolver-dns` would remain via
`spring-boot-starter-webflux` -> `reactor-netty-http`.

**The trap, recorded for whoever revisits this:** `netty-all` is a dependency-only
aggregator jar with no classes of its own, and it is the *only* source of
`netty-transport-native-epoll:linux-aarch_64` in the fat jar —
`application/pom.xml:47-51` pins the `linux-x86_64` classifier explicitly and nothing else
brings the arm64 native. Since releases publish `linux/arm64` images and Reactor Netty
picks epoll up by *auto-detection*, deleting `netty-all` without also adding an explicit
`linux-aarch_64` classifier would push ARM deployments back to NIO **silently** rather than
failing. `common/integration/integration-api/pom.xml:73` also declares `netty-all`, but at
`provided` scope, so that one never reaches a runtime classpath.

Separately observed and also out of scope: the **IE jar already ships only
`netty-transport-native-epoll:linux-x86_64`** and no arm64 native at all, so
`tbmq-integration-executor` has this gap on ARM today, independently of this spec.

**Aggressive dependency removal (~18MB).** Each item trades a feature for size:
`zstd-jni` + `snappy-java` + `lz4-java` (9.4MB) are non-optional `kafka-clients`
dependencies and removing them restricts `compression.type`; `springdoc-swagger-ui`
(4.2MB) is the interactive API docs page; `prometheus-metrics-exposition-formats` (2.0MB)
is the protobuf/OpenMetrics scrape format; `oshi-core` + `jna` + `jna-platform` (4.0MB)
back system CPU/memory metrics used by `common/util`.

**Shrinking the jar by better packaging.** No headroom: Spring Boot stores nested jars
uncompressed, but each nested jar's own entries are already deflated. That is also why
the 149MB jar yields a 141MB `.deb` — only 5% compression. The only lever is deleting
libraries.

**`RUN --mount=type=bind` instead of a builder stage.** Achieves the same `.deb` saving
with one stage, but is BuildKit-only and would break the local Spotify-plugin build path.

**Copying an exploded install tree into the image instead of running `dpkg`.** Avoids
`dpkg` in the build entirely, but shifts responsibility for directory layout, file modes
and ownership from the package's own logic into hand-written Dockerfile steps — more
invasive on the build side and easier to get subtly wrong, for no additional saving over
the builder stage.

## Risks

- **Legacy builder deprecation.** Docker 29 prints
  `DEPRECATED: The legacy builder is deprecated and will be removed in a future release`.
  This spec does not depend on it — the pattern works on both builders — but the
  `dockerfile-maven-plugin` path will eventually need attention regardless of this work.
- **Loss of the `dpkg` database entry** could surprise anyone shelling in to check the
  installed version with `dpkg -l`. The version remains visible in the jar manifest
  (`Implementation-Version`, `pom.xml:409`) and in the startup banner.
- **Removing `curl`** may inconvenience operators who use it interactively. Reversible
  in one line if it proves unpopular.
