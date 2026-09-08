# Docker Image Size Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Shrink the three published TBMQ Docker images (`tbmq` 778MB, `tbmq-node` 934MB, `tbmq-integration-executor` 664MB) by ~19-36% without changing the Java runtime or anything the images contain at runtime.

**Architecture:** Convert each of the three Dockerfiles into a two-stage build. The builder stage runs today's `dpkg -i` unchanged; the runtime stage copies the install tree that `dpkg` produced. This removes the duplicated `.deb` layer, the `chmod`-rewrites-the-jar layer, and the uncleaned `apt` layer. Separately, two dead dependencies are trimmed out of the Spring Boot fat jars. The base image (`thingsboard/openjdk25:trixie-slim`) and the JDK inside it are deliberately untouched.

**Tech Stack:** Docker (multi-stage, must work on both the legacy builder and BuildKit/buildx), Maven, Spotify `dockerfile-maven-plugin`, Netflix Nebula `ospackage` (Gradle) for the `.deb`, Spring Boot Maven plugin.

**Spec:** `docs/superpowers/specs/2026-09-08-docker-image-size-optimization-design.md`

**Branch:** `chore/docker-image-size-optimization` (already created, off `main`)

---

## Orientation for the implementer

Read this before Task 1. It is the context that makes the rest of the plan make sense.

### How these images are built

There is no CI workflow for images. They are built locally by Maven:

```bash
mvn clean install -DskipTests -Ddockerfile.skip=false
```

`dockerfile.skip` defaults to `true` (`msa/pom.xml:44`), so a normal build produces no
images. With it set to `false`, the Spotify `dockerfile-maven-plugin` builds each image at
the `pre-integration-test` phase (e.g. `msa/tbmq/pom.xml:106-121`) and tags it
`thingsboard/<name>:2.4.1-SNAPSHOT` plus `latest`.

The `docker` directory of each `msa` submodule is copied to `target/docker` with Maven
property substitution applied (`pom.xml:357-370`). So `${pkg.name}`, `${pkg.user}`,
`${pkg.installFolder}`, `${docker.base.image}` and friends in the Dockerfiles are resolved
at build time — **you edit the templates under `msa/*/docker/`, and the resolved output
lands in `msa/*/target/docker/`.** Substituted values for this repo:

| Property | `tbmq` / `tbmq-node` | `tbmq-integration-executor` |
|---|---|---|
| `${docker.base.image}` | `thingsboard/openjdk25:trixie-slim` | same |
| `${pkg.name}` | `thingsboard-mqtt-broker` | `tbmq-integration-executor` |
| `${pkg.user}` | `thingsboard` | `thingsboard` |
| `${pkg.installFolder}` | `/usr/share/thingsboard-mqtt-broker` | `/usr/share/tbmq-integration-executor` |
| `${pkg.unixLogFolder}` | `/var/log/thingsboard-mqtt-broker` | `/var/log/tbmq-integration-executor` |

### Two hard constraints

1. **No BuildKit-only Dockerfile syntax.** Local builds go through the Spotify plugin,
   which posts to the Docker daemon's *legacy* build API. Release builds go through
   `docker buildx build` (`msa/pom.xml:74`, `:95`). Multi-stage works on both.
   `RUN --mount=type=bind` and `COPY --chmod=` are **BuildKit-only** and would work in the
   release path while breaking local builds. Do not use them.
2. **Nothing inside the image may change.** Every task that touches a Dockerfile is
   verified by diffing the old and new image filesystems. The only acceptable difference
   is file mtimes.

### Two traps that were hit while prototyping

- **`COPY --from` sets the destination's top directory to `root:root`**, while preserving
  uid/gid and modes of everything beneath it. So
  `COPY --from=builder /usr/share/x /usr/share/x` leaves `/usr/share/x` itself owned by
  root. Every runtime stage therefore needs an explicit
  `chown ${pkg.user}:${pkg.user} ${pkg.installFolder}`.
- **Do not `chown -R` the install folder in the builder.** Today the two config files
  moved in from `/tmp` (`logback.xml`, `${pkg.name}.conf`) stay `root:root` mode
  `-rw-rw-r--`. A `chown -R` flips them to `thingsboard:thingsboard`, making them
  group-writable by the process that reads them. Apply only the `chmod 555` to the jar and
  let `COPY` preserve everything else.

### Working directory for scratch files

Use `/tmp/claude-1000/-home-dlandiak-projects-tbmq/1b744669-a6b5-402f-835e-1372902431bb/scratchpad`
for the verification harness and captured manifests. Nothing from it gets committed — the
repo diff for this whole plan is 3 Dockerfiles + 2 pom files.

Every task below refers to this directory as `$SP`. Shell state does **not** persist
between tool calls, so **re-run this export at the start of every task** (and in any new
shell) before using `$SP`:

```bash
export SP=/tmp/claude-1000/-home-dlandiak-projects-tbmq/1b744669-a6b5-402f-835e-1372902431bb/scratchpad
```

---

## File Structure

**Modified — Dockerfiles (the substance of the change):**

| File | Responsibility | Current shape |
|---|---|---|
| `msa/tbmq/docker/Dockerfile` | `thingsboard/tbmq` monolith image | 1 `RUN`, installs `curl`, ships config overrides + 3 `/usr/bin` scripts |
| `msa/mqtt-broker/docker/Dockerfile` | `thingsboard/tbmq-node` cluster node image | **5 `RUN`s**, installs `curl`, 1 script |
| `msa/integration/executor/docker/Dockerfile` | `thingsboard/tbmq-integration-executor` image | 2 `RUN`s, installs `curl`, 1 script, no `/data` |

**Modified — poms (dependency trimming):**

| File | Responsibility | Change |
|---|---|---|
| `pom.xml` | root: `dependencyManagement`, shared plugin config | drop `tink` version property + managed dep; add `lombok` exclude to `spring-boot-maven-plugin` |
| `application/pom.xml` | the `thingsboard-mqtt-broker` app | drop the dead `tink` dependency |

**Created — scratch only, never committed:**

| File | Responsibility |
|---|---|
| `$SP/image-manifest.sh` | Capture a filesystem manifest of one image (paths, modes, owners, sizes, env, `java -version`) |
| `$SP/manifests/*.txt` | Captured baseline and post-change manifests |
| `$SP/jar-inventory.py` | List the nested jars inside a Spring Boot fat jar with sizes |

**Not touched (and why):**

- `io.netty:netty-all` (`application/pom.xml:55`) stays. Replacing it with the specific
  modules was considered and **dropped from scope** — it saves only ~0.5MB and carries the
  only silent failure mode in the whole audit. See follow-up 2 in the closing notes.
- `integration/executor/pom.xml` declares no `tink`. Its jar benefits only from the
  `lombok` exclude, which is inherited from the root pom.
- `msa/pom.xml` — no change needed. `docker.base.image` stays as-is.

---

## Task 1: Build the verification harness and capture the baseline

Nothing can be verified without a golden record of what the current images contain. This
task builds that record. It is the "failing test" for every Dockerfile task that follows:
Tasks 2-4 are only complete when the new image's manifest matches the baseline captured
here, modulo mtimes.

**Files:**
- Create: `$SP/image-manifest.sh`
- Create: `$SP/jar-inventory.py`
- Create: `$SP/manifests/` (captured output)

- [ ] **Step 1: Confirm you are on the right branch with a clean tree**

```bash
cd /home/dlandiak/projects/tbmq
git rev-parse --abbrev-ref HEAD
git status --short
```

Expected: `chore/docker-image-size-optimization`, and no output from `git status --short`
(the spec under `docs/` is invisible to git — `/docs/` is ignored at `.gitignore:39`).

- [ ] **Step 2: Write the manifest capture script**

**Already written and validated** at `$SP/image-manifest.sh` during planning — it was run
against the published `thingsboard/tbmq:2.4.0` image and against both prototype images
(`proto-tbmq-base`, the current Dockerfile, and `proto-tbmq-new`, the Task 2 pattern),
producing byte-identical output for the pair. If the file is still present, verify it and
skip to Step 3; the reference output is at `$SP/manifests/proto-before.txt` and
`$SP/manifests/proto-after.txt`. Recreate it from the listing below if it is gone.

This is the verification tool. It prints everything about an image that must not change.
`%M` is the symbolic mode, `%u:%g` the owner, `%s` the size, `%p` the path, and `%l` the
symlink target — deliberately *not* mtime, which legitimately differs between builds.

```bash
export SP=/tmp/claude-1000/-home-dlandiak-projects-tbmq/1b744669-a6b5-402f-835e-1372902431bb/scratchpad
mkdir -p "$SP/manifests"
cat > "$SP/image-manifest.sh" <<'SCRIPT'
#!/usr/bin/env bash
# Usage: image-manifest.sh <image> <install-folder> <log-folder>
# Prints a stable, mtime-free manifest of everything that must not change.
set -euo pipefail
IMAGE="$1"; INSTALL="$2"; LOGDIR="$3"
docker run --rm --entrypoint sh "$IMAGE" -c "
  echo '## id'
  id
  echo '## install tree'
  find '$INSTALL' -printf '%M %u:%g %s %p -> %l\n' | sort -k4
  echo '## install tree file count'
  find '$INSTALL' -type f | wc -l
  echo '## dirs of interest'
  for d in '$LOGDIR' /data /tmp /etc/\$(basename '$INSTALL'); do
    [ -e \"\$d\" ] && stat -c '%A %U:%G %n' \"\$d\" || echo \"ABSENT \$d\"
  done
  echo '## /etc symlink'
  find /etc/\$(basename '$INSTALL') -maxdepth 1 -printf '%M %u:%g %p -> %l\n' 2>/dev/null | sort || echo 'ABSENT'
  echo '## /usr/bin scripts'
  ls -l /usr/bin/ | grep -iE 'tbmq|mqtt-broker' | awk '{print \$1, \$3\":\"\$4, \$9}' | sort || echo 'NONE'
  echo '## java'
  java -version 2>&1
  echo '## env'
  env | grep -vE '^(HOSTNAME|PWD|_)=' | sort
"
SCRIPT
chmod +x "$SP/image-manifest.sh"
```

- [ ] **Step 3: Write the fat-jar inventory script**

Used by Tasks 5-7 to prove a dependency actually left the jar.

```bash
cat > "$SP/jar-inventory.py" <<'SCRIPT'
#!/usr/bin/env python3
# Usage: jar-inventory.py <fat-jar>   -> "<MB> <jar-name>" per line, largest first, plus a total
import sys, zipfile
z = zipfile.ZipFile(sys.argv[1])
jars = [(i.compress_size, i.filename.split("/")[-1])
        for i in z.infolist() if i.filename.endswith(".jar")]
print(f"TOTAL {len(jars)} nested jars, {sum(s for s, _ in jars)/1048576:.2f} MB")
for s, n in sorted(jars, reverse=True):
    print(f"{s/1048576:8.2f} {n}")
SCRIPT
chmod +x "$SP/jar-inventory.py"
```

- [ ] **Step 4: Build the current images to establish the baseline**

This is a full build and takes roughly 10-20 minutes. It must run on the *unmodified*
Dockerfiles, which is why it is Task 1.

```bash
cd /home/dlandiak/projects/tbmq
mvn clean install -DskipTests -Ddockerfile.skip=false 2>&1 | tail -40
```

Expected: `BUILD SUCCESS`, and three freshly built images.

- [ ] **Step 5: Record the baseline sizes and re-tag the images**

Re-tagging is essential — the next build will overwrite the `2.4.1-SNAPSHOT` tags.

```bash
docker images --format '{{.Repository}}:{{.Tag}} {{.Size}}' | grep 2.4.1-SNAPSHOT | tee "$SP/manifests/sizes-baseline.txt"
for n in tbmq tbmq-node tbmq-integration-executor; do
  docker tag "thingsboard/$n:2.4.1-SNAPSHOT" "baseline/$n:before"
done
docker images --format '{{.Repository}}:{{.Tag}} {{.Size}}' | grep '^baseline/'
```

Expected: three `baseline/*:before` images, sizes near 778MB / 934MB / 664MB (they will
differ slightly from the published `2.4.0` images because the jar has moved on).

- [ ] **Step 6: Capture the three baseline manifests**

```bash
"$SP/image-manifest.sh" baseline/tbmq:before \
    /usr/share/thingsboard-mqtt-broker /var/log/thingsboard-mqtt-broker \
    > "$SP/manifests/tbmq-before.txt"
"$SP/image-manifest.sh" baseline/tbmq-node:before \
    /usr/share/thingsboard-mqtt-broker /var/log/thingsboard-mqtt-broker \
    > "$SP/manifests/tbmq-node-before.txt"
"$SP/image-manifest.sh" baseline/tbmq-integration-executor:before \
    /usr/share/tbmq-integration-executor /var/log/tbmq-integration-executor \
    > "$SP/manifests/tbmq-ie-before.txt"
wc -l "$SP/manifests/"*-before.txt
```

Expected: three non-empty manifests, each ~40-60 lines.

- [ ] **Step 7: Sanity-check a baseline manifest by eye**

```bash
head -20 "$SP/manifests/tbmq-before.txt"
```

Expected: `uid=799(thingsboard) gid=799(thingsboard)`, then install-tree lines showing
`/usr/share/thingsboard-mqtt-broker` owned by `thingsboard:thingsboard`, the jar at mode
`-r-xr-xr-x` (555), and `conf/logback.xml` + `conf/thingsboard-mqtt-broker.conf` owned by
**`root:root`**. If those two config files are already `thingsboard:thingsboard`, stop and
re-read the "traps" section — the baseline is not what this plan assumes.

- [ ] **Step 8: Capture the baseline jar inventories**

```bash
CID=$(docker create baseline/tbmq:before)
docker cp "$CID:/usr/share/thingsboard-mqtt-broker/bin/thingsboard-mqtt-broker.jar" "$SP/manifests/app-before.jar"
docker rm -f "$CID" >/dev/null
CID=$(docker create baseline/tbmq-integration-executor:before)
docker cp "$CID:/usr/share/tbmq-integration-executor/bin/tbmq-integration-executor.jar" "$SP/manifests/ie-before.jar"
docker rm -f "$CID" >/dev/null
"$SP/jar-inventory.py" "$SP/manifests/app-before.jar" > "$SP/manifests/jars-app-before.txt"
"$SP/jar-inventory.py" "$SP/manifests/ie-before.jar"  > "$SP/manifests/jars-ie-before.txt"
head -1 "$SP/manifests/jars-app-before.txt" "$SP/manifests/jars-ie-before.txt"
grep -E 'tink|lombok' "$SP/manifests/jars-app-before.txt"
```

Expected: app jar ~201 nested jars / ~147MB; IE jar ~132 / ~91MB. The `grep` must show
`tink-*.jar` (~2.30MB) and `lombok-*.jar` (~1.95MB) — the two dependencies Tasks 5 and 6
remove. These `-before.txt` files are the baseline those tasks diff against.

- [ ] **Step 9: Commit the harness reference**

There is nothing to commit — the harness lives in scratch and `/docs/` is gitignored.
Confirm the tree is still clean so later tasks start from a known state:

```bash
git status --short
```

Expected: no output.

---

## Task 2: Convert `msa/tbmq/docker/Dockerfile` to two stages

This is the image the prototype was validated against, so it is the safest one to do
first. Expected: ~−182MB.

**Files:**
- Modify: `msa/tbmq/docker/Dockerfile:17-44` (everything after the license header)

- [ ] **Step 1: Read the current file so you know exactly what you are replacing**

```bash
sed -n '17,44p' msa/tbmq/docker/Dockerfile
```

Expected: a single `FROM`, a `COPY` of six files into `/tmp/`, one long `RUN` ending in
`apt-get install -y curl`, then `USER`, `VOLUME`, `CMD`.

- [ ] **Step 2: Replace the body, keeping the license header untouched**

Keep lines 1-16 (the Apache header) exactly as they are. Replace from line 17 to the end
with:

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

Note what deliberately disappeared and why:
- `apt-get update` and `apt-get install -y curl` — `curl` is used by nothing (see spec).
- `rm /tmp/${pkg.name}.deb` — the `.deb` is now only in the builder stage.
- `systemctl --no-reload disable` — the systemd unit is no longer in the final image.
- `chown -R ${pkg.user}:${pkg.user} /var/log/${pkg.name}` became `mkdir -p` + `chown`,
  because the package's postinst no longer runs in the final image.

- [ ] **Step 3: Verify the license header survived**

The repo enforces license headers via `.github/workflows/license-header-format.yml`.

```bash
head -16 msa/tbmq/docker/Dockerfile
grep -c "Apache License" msa/tbmq/docker/Dockerfile
```

Expected: the full comment block, and `1`.

- [ ] **Step 4: Rebuild only what is needed**

```bash
cd /home/dlandiak/projects/tbmq
mvn install -DskipTests -Ddockerfile.skip=false -pl msa/tbmq 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`. If it fails with a missing `thingsboard-mqtt-broker.deb`, the
`msa/tbmq/target/docker` context was cleaned — rerun the full
`mvn clean install -DskipTests -Ddockerfile.skip=false` instead.

- [ ] **Step 5: Confirm the resolved Dockerfile is valid multi-stage**

```bash
grep -n "^FROM\|^COPY\|^RUN" msa/tbmq/target/docker/Dockerfile
```

Expected: two `FROM` lines with the first ending `AS builder`, and no `${...}` left
unresolved anywhere in the file:

```bash
grep -n '\${' msa/tbmq/target/docker/Dockerfile || echo "all properties resolved"
```

Expected: `all properties resolved`.

- [ ] **Step 6: Record the new size**

```bash
docker images --format '{{.Repository}}:{{.Tag}} {{.Size}}' | grep 'tbmq:2.4.1-SNAPSHOT'
grep ' tbmq:' "$SP/manifests/sizes-baseline.txt"
```

Expected: the new size is ~180MB smaller than the baseline line.

- [ ] **Step 7: Run the verification diff — this is the actual test**

```bash
"$SP/image-manifest.sh" thingsboard/tbmq:2.4.1-SNAPSHOT \
    /usr/share/thingsboard-mqtt-broker /var/log/thingsboard-mqtt-broker \
    > "$SP/manifests/tbmq-after.txt"
diff "$SP/manifests/tbmq-before.txt" "$SP/manifests/tbmq-after.txt" && echo ">>> IDENTICAL <<<"
```

Expected: `>>> IDENTICAL <<<`.

If there is a diff, do not proceed. The likely causes, in order:
- `/usr/share/thingsboard-mqtt-broker` shows `root:root` → the
  `chown ${pkg.user}:${pkg.user} ${pkg.installFolder}` line is missing or misspelled.
- `conf/logback.xml` shows `thingsboard:thingsboard` → a `chown -R` crept into the builder
  stage.
- The `/var/log/...` directory is `ABSENT` → the `mkdir -p ${pkg.unixLogFolder}` is missing.
- A `curl` line still appears under `/usr/bin` → you edited `target/docker/Dockerfile`
  instead of `msa/tbmq/docker/Dockerfile`; the former is regenerated every build.

- [ ] **Step 8: Boot the image and confirm it reaches the same startup point**

With no Postgres running, both old and new must fail identically at the JDBC connection —
which proves the jar, the launcher, the config and the log folder all resolve.

```bash
timeout 75 docker run --rm thingsboard/tbmq:2.4.1-SNAPSHOT 2>&1 | head -20
```

Expected output includes, in order: `Starting TBMQ installation ...`, the
`:: TBMQ Community Edition ::` banner, `The following 1 profile is active: "install"`,
`Found 18 JPA repository interfaces`, then
`Connection to localhost:5432 refused`. Reaching the JDBC failure is success here.

- [ ] **Step 9: Commit**

```bash
git add msa/tbmq/docker/Dockerfile
git commit -m "perf(docker): build tbmq image in two stages

Run dpkg in a builder stage and copy the resulting install tree into the
runtime stage, so the .deb is no longer paid for both as a COPY layer and
again when extracted. Drop curl, which nothing invokes, along with the
apt-get update whose package lists were never cleaned.

Image filesystem is unchanged: paths, modes, owners, sizes, env and
java -version all match the previous build.

thingsboard/tbmq: ~778MB -> ~597MB"
```

---

## Task 3: Convert `msa/mqtt-broker/docker/Dockerfile` to two stages

The biggest win of the three (~−338MB), because this image currently spreads the install
over five `RUN` layers and ends with a `chmod 555` that rewrites the whole 149MB jar into
its own layer.

**Files:**
- Modify: `msa/mqtt-broker/docker/Dockerfile:17-41`

- [ ] **Step 1: Read the current file**

```bash
sed -n '17,41p' msa/mqtt-broker/docker/Dockerfile
```

Expected: `FROM`, a `COPY` of the start script + `.deb`, then **five** separate `RUN`
instructions, then `USER`, `VOLUME`, `CMD`.

- [ ] **Step 2: Replace the body, keeping lines 1-16 untouched**

This image ships **no config overrides** — no `logback.xml`, no `.conf` — so the builder
stage only installs and chmods. It does keep `/data` and `VOLUME`, but it has **no
`ENV DATA_FOLDER`** today, so none is added.

```dockerfile
FROM ${docker.base.image} AS builder

COPY ${pkg.name}.deb /tmp/

RUN dpkg -i /tmp/${pkg.name}.deb \
    && chmod 555 ${pkg.installFolder}/bin/${pkg.name}.jar

FROM ${docker.base.image}

COPY start-tb-mqtt-broker.sh /usr/bin/
COPY --from=builder ${pkg.installFolder} ${pkg.installFolder}

RUN chmod a+x /usr/bin/start-tb-mqtt-broker.sh \
    && chown ${pkg.user}:${pkg.user} ${pkg.installFolder} \
    && mkdir -p /etc/${pkg.name} \
    && ln -s ${pkg.installFolder}/conf /etc/${pkg.name}/conf \
    && mkdir -p /data ${pkg.unixLogFolder} \
    && chown -R ${pkg.user}:${pkg.user} /data ${pkg.unixLogFolder} /tmp

USER ${pkg.user}

VOLUME ["/data"]

CMD ["start-tb-mqtt-broker.sh"]
```

Note: the original used `yes | dpkg -i` here. The `yes |` guards against interactive
config-file prompts on *upgrade* over an existing install; in a fresh image there is
nothing to prompt about, and Task 2 already proved plain `dpkg -i` works. Keeping it would
also be harmless — if you prefer minimal deviation, `yes | dpkg -i ...` is fine.

- [ ] **Step 3: Verify the license header survived**

```bash
head -16 msa/mqtt-broker/docker/Dockerfile
grep -c "Apache License" msa/mqtt-broker/docker/Dockerfile
```

Expected: the full comment block, and `1`.

- [ ] **Step 4: Rebuild**

```bash
cd /home/dlandiak/projects/tbmq
mvn install -DskipTests -Ddockerfile.skip=false -pl msa/mqtt-broker 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Confirm properties resolved and staging is right**

```bash
grep -n "^FROM\|^COPY\|^RUN" msa/mqtt-broker/target/docker/Dockerfile
grep -n '\${' msa/mqtt-broker/target/docker/Dockerfile || echo "all properties resolved"
```

Expected: two `FROM` lines, first ending `AS builder`; `all properties resolved`.

- [ ] **Step 6: Record the new size**

```bash
docker images --format '{{.Repository}}:{{.Tag}} {{.Size}}' | grep 'tbmq-node:2.4.1-SNAPSHOT'
grep 'tbmq-node' "$SP/manifests/sizes-baseline.txt"
```

Expected: ~330MB smaller than baseline.

- [ ] **Step 7: Run the verification diff**

```bash
"$SP/image-manifest.sh" thingsboard/tbmq-node:2.4.1-SNAPSHOT \
    /usr/share/thingsboard-mqtt-broker /var/log/thingsboard-mqtt-broker \
    > "$SP/manifests/tbmq-node-after.txt"
diff "$SP/manifests/tbmq-node-before.txt" "$SP/manifests/tbmq-node-after.txt" && echo ">>> IDENTICAL <<<"
```

Expected: `>>> IDENTICAL <<<`. See Task 2 Step 7 for how to read a diff if there is one.

- [ ] **Step 8: Boot the image**

`tbmq-node` reads its config from `/config` if present, else from the install folder, and
starts the broker directly (no install step unless `INSTALL_TB=true`).

```bash
timeout 75 docker run --rm thingsboard/tbmq-node:2.4.1-SNAPSHOT 2>&1 | head -20
```

Expected: `Starting 'TBMQ Community Edition' ...` (or the equivalent project name), the
banner, then a connection failure against Postgres/Kafka. Compare against the baseline if
unsure:

```bash
timeout 75 docker run --rm baseline/tbmq-node:before 2>&1 | head -20
```

Expected: the same sequence of lines, same failure.

- [ ] **Step 9: Commit**

```bash
git add msa/mqtt-broker/docker/Dockerfile
git commit -m "perf(docker): build tbmq-node image in two stages

Collapse five RUN layers into a builder stage plus a single runtime layer.
The trailing chmod 555 was rewriting the whole 149MB jar into a fresh
layer, so the payload was stored three times: once as the copied .deb,
once extracted, and once again by the chmod. Also drop curl and the
uncleaned apt-get update.

Image filesystem is unchanged: paths, modes, owners, sizes, env and
java -version all match the previous build.

thingsboard/tbmq-node: ~934MB -> ~597MB"
```

---

## Task 4: Convert `msa/integration/executor/docker/Dockerfile` to two stages

Expected: ~−125MB. Differs from the other two in three ways: a different package name and
install folder, **no `/data` and no `VOLUME`**, and no config overrides.

**Files:**
- Modify: `msa/integration/executor/docker/Dockerfile:17-33`

- [ ] **Step 1: Read the current file**

```bash
sed -n '17,33p' msa/integration/executor/docker/Dockerfile
```

Expected: `FROM`, `COPY` of the start script + `.deb`, an `apt-get`/`curl` `RUN`, a second
`RUN` doing the install, then `USER` and `CMD`. Note there is **no `VOLUME`** and no
`ENV DATA_FOLDER` — do not introduce either.

- [ ] **Step 2: Replace the body, keeping lines 1-16 untouched**

```dockerfile
FROM ${docker.base.image} AS builder

COPY ${pkg.name}.deb /tmp/

RUN dpkg -i /tmp/${pkg.name}.deb \
    && chmod 555 ${pkg.installFolder}/bin/${pkg.name}.jar

FROM ${docker.base.image}

COPY start-tbmq-integration-executor.sh /usr/bin/
COPY --from=builder ${pkg.installFolder} ${pkg.installFolder}

RUN chmod a+x /usr/bin/start-tbmq-integration-executor.sh \
    && chown ${pkg.user}:${pkg.user} ${pkg.installFolder} \
    && mkdir -p /etc/${pkg.name} \
    && ln -s ${pkg.installFolder}/conf /etc/${pkg.name}/conf \
    && mkdir -p ${pkg.unixLogFolder} \
    && chown -R ${pkg.user}:${pkg.user} ${pkg.unixLogFolder} /tmp

USER ${pkg.user}

CMD ["start-tbmq-integration-executor.sh"]
```

Note: the original IE Dockerfile never chowned `/tmp`, but `start-tbmq-integration-executor.sh`
does `cd ${pkg.installFolder}/bin` and the JVM writes hsperfdata under `/tmp`. The baseline
image has `/tmp` at mode `drwxrwxrwt` owned by `root:root`; the `chown -R ... /tmp` above
changes that to `thingsboard:thingsboard`. **If Step 7's diff flags `/tmp`, drop `/tmp`
from that `chown` line** — `drwxrwxrwt` is world-writable anyway, so the chown is
cosmetic. Match the baseline, whatever it says.

- [ ] **Step 3: Verify the license header survived**

```bash
head -16 msa/integration/executor/docker/Dockerfile
grep -c "Apache License" msa/integration/executor/docker/Dockerfile
```

Expected: the full comment block, and `1`.

- [ ] **Step 4: Rebuild**

```bash
cd /home/dlandiak/projects/tbmq
mvn install -DskipTests -Ddockerfile.skip=false -pl msa/integration/executor 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Confirm properties resolved**

```bash
grep -n "^FROM\|^COPY\|^RUN" msa/integration/executor/target/docker/Dockerfile
grep -n '\${' msa/integration/executor/target/docker/Dockerfile || echo "all properties resolved"
```

Expected: two `FROM` lines, first ending `AS builder`; `all properties resolved`.

- [ ] **Step 6: Record the new size**

```bash
docker images --format '{{.Repository}}:{{.Tag}} {{.Size}}' | grep 'tbmq-integration-executor:2.4.1-SNAPSHOT'
grep 'integration-executor' "$SP/manifests/sizes-baseline.txt"
```

Expected: ~123MB smaller than baseline.

- [ ] **Step 7: Run the verification diff**

```bash
"$SP/image-manifest.sh" thingsboard/tbmq-integration-executor:2.4.1-SNAPSHOT \
    /usr/share/tbmq-integration-executor /var/log/tbmq-integration-executor \
    > "$SP/manifests/tbmq-ie-after.txt"
diff "$SP/manifests/tbmq-ie-before.txt" "$SP/manifests/tbmq-ie-after.txt" && echo ">>> IDENTICAL <<<"
```

Expected: `>>> IDENTICAL <<<`.

Pay particular attention to two IE-specific lines in the install tree:
- `bin/tbmq-integration-executor.yml -> /usr/share/tbmq-integration-executor/conf/tbmq-integration-executor.yml`
  must still be a symlink with the same target. If it became a regular file, `COPY --from`
  followed the link — that would be a real regression.
- `/tmp` — see the note in Step 2.

- [ ] **Step 8: Boot the image**

```bash
timeout 60 docker run --rm thingsboard/tbmq-integration-executor:2.4.1-SNAPSHOT 2>&1 | head -20
timeout 60 docker run --rm baseline/tbmq-integration-executor:before 2>&1 | head -20
```

Expected: both print `Starting 'TBMQ Integration Executor' ...` (or the equivalent project
name) followed by the same Spring startup and the same failure against the absent Kafka.
The two outputs must agree line-for-line apart from timings and PIDs.

- [ ] **Step 9: Commit**

```bash
git add msa/integration/executor/docker/Dockerfile
git commit -m "perf(docker): build tbmq-integration-executor image in two stages

Run dpkg in a builder stage and copy the install tree into the runtime
stage, removing the duplicated .deb layer. Drop curl and the uncleaned
apt-get update.

Image filesystem is unchanged: paths, modes, owners, sizes, env and
java -version all match the previous build.

thingsboard/tbmq-integration-executor: ~664MB -> ~541MB"
```

---

## Task 5: Remove the dead `tink` dependency

`com.google.crypto.tink:tink` is declared in `application/pom.xml` but has **zero**
references anywhere in the codebase — an inherited leftover from ThingsBoard, which
carries the same dead declaration. 2.30MB.

**Files:**
- Modify: `application/pom.xml:129-132`
- Modify: `pom.xml:74` (version property)
- Modify: `pom.xml:953-957` (managed dependency)

- [ ] **Step 1: Re-confirm it is genuinely unused before deleting anything**

```bash
cd /home/dlandiak/projects/tbmq
grep -rn "com.google.crypto.tink\|crypto\.tink" --include="*.java" . | grep -v target || echo "NO JAVA REFERENCES"
grep -rn "tink" --include="*.yml" --include="*.properties" . | grep -v target | grep -iv thinking || echo "NO CONFIG REFERENCES"
```

Expected: `NO JAVA REFERENCES` and `NO CONFIG REFERENCES`. If either prints a hit, **stop
and skip this task** — the premise is wrong.

- [ ] **Step 2: Remove the dependency declaration**

In `application/pom.xml`, delete these four lines (currently 129-132):

```xml
        <dependency>
            <groupId>com.google.crypto.tink</groupId>
            <artifactId>tink</artifactId>
        </dependency>
```

- [ ] **Step 3: Remove the managed dependency from the root pom**

In `pom.xml`, delete these five lines (currently 953-957):

```xml
            <dependency>
                <groupId>com.google.crypto.tink</groupId>
                <artifactId>tink</artifactId>
                <version>${google-crypto-tink.version}</version>
            </dependency>
```

- [ ] **Step 4: Remove the now-orphaned version property**

In `pom.xml`, delete this line (currently 74):

```xml
        <google-crypto-tink.version>1.11.0</google-crypto-tink.version>
```

- [ ] **Step 5: Confirm nothing else referenced the property**

```bash
grep -rn "google-crypto-tink" --include=pom.xml . | grep -v target || echo "NO REMAINING REFERENCES"
```

Expected: `NO REMAINING REFERENCES`.

- [ ] **Step 6: Rebuild the app jar and prove tink is gone**

```bash
mvn clean install -DskipTests -pl application -am 2>&1 | tail -20
"$SP/jar-inventory.py" application/target/thingsboard-mqtt-broker-*-boot.jar \
    > "$SP/manifests/jars-app-no-tink.txt"
head -1 "$SP/manifests/jars-app-no-tink.txt"
grep -c tink "$SP/manifests/jars-app-no-tink.txt" || echo "TINK GONE"
```

Expected: `BUILD SUCCESS`; `TINK GONE`; the `TOTAL` line ~2.3MB smaller and two fewer
nested jars than `$SP/manifests/jars-app-before.txt`.

- [ ] **Step 7: Confirm protobuf-java survived**

`tink` depends on protobuf, but `dao` and `common/queue` declare it independently, so it
must still be present. If it vanished, something genuinely needs a direct declaration.

```bash
grep protobuf-java "$SP/manifests/jars-app-no-tink.txt"
```

Expected: `protobuf-java-*.jar` still listed at ~1.79MB.

- [ ] **Step 8: Commit**

```bash
git add pom.xml application/pom.xml
git commit -m "chore(deps): drop unused Google Tink dependency

com.google.crypto.tink:tink was declared in the application module but
referenced nowhere in the codebase - a leftover inherited from
ThingsBoard. Removing it takes 2.3MB out of the fat jar, and therefore
out of the tbmq and tbmq-node images.

protobuf-java is unaffected: dao and common/queue declare it directly."
```

---

## Task 6: Stop packaging Lombok into the fat jars

Lombok is correctly scoped `provided` (`pom.xml:790`), but `spring-boot-maven-plugin`
packages `provided`-scope dependencies into the fat jar by default. So a compile-time-only
annotation processor ships to production in **both** the app jar and the IE jar. 1.95MB
each. This is a defect independent of image size.

**Files:**
- Modify: `pom.xml:417-430` (the `spring-boot-maven-plugin` `<configuration>` block inside
  the `activeByDefault` `packaging` profile's `<pluginManagement>`)

- [ ] **Step 1: Confirm Lombok is currently in both jars**

```bash
grep lombok "$SP/manifests/jars-app-before.txt" "$SP/manifests/jars-ie-before.txt"
```

Expected: one `lombok-*.jar` line at ~1.95MB from each file.

- [ ] **Step 2: Add an `<excludes>` block to the plugin configuration**

In `pom.xml`, inside the `spring-boot-maven-plugin` `<configuration>` element (which
currently ends with `</embeddedLaunchScriptProperties>` followed by `</configuration>`),
add an `<excludes>` element immediately before the closing `</configuration>`:

```xml
                                <excludes>
                                    <exclude>
                                        <groupId>org.projectlombok</groupId>
                                        <artifactId>lombok</artifactId>
                                    </exclude>
                                </excludes>
```

The resulting `<configuration>` block reads:

```xml
                            <configuration>
                                <skip>${pkg.disabled}</skip>
                                <mainClass>${pkg.mainClass}</mainClass>
                                <classifier>boot</classifier>
                                <layout>ZIP</layout>
                                <executable>true</executable>
                                <excludeDevtools>true</excludeDevtools>
                                <embeddedLaunchScriptProperties>
                                    <confFolder>${pkg.installFolder}/conf</confFolder>
                                    <logFolder>${pkg.unixLogFolder}</logFolder>
                                    <logFilename>${pkg.name}.out</logFilename>
                                    <initInfoProvides>${pkg.name}</initInfoProvides>
                                </embeddedLaunchScriptProperties>
                                <excludes>
                                    <exclude>
                                        <groupId>org.projectlombok</groupId>
                                        <artifactId>lombok</artifactId>
                                    </exclude>
                                </excludes>
                            </configuration>
```

This is shared `pluginManagement`, so it applies to both modules that build boot jars:
`application` and `integration/executor`.

- [ ] **Step 3: Rebuild both boot jars**

```bash
cd /home/dlandiak/projects/tbmq
mvn clean install -DskipTests 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`. Compilation is unaffected — Lombok is still on the *compile*
classpath; only the packaging step changes.

- [ ] **Step 4: Prove Lombok left both jars**

```bash
"$SP/jar-inventory.py" application/target/thingsboard-mqtt-broker-*-boot.jar \
    > "$SP/manifests/jars-app-no-lombok.txt"
"$SP/jar-inventory.py" integration/executor/target/tbmq-integration-executor-*-boot.jar \
    > "$SP/manifests/jars-ie-no-lombok.txt"
grep -c lombok "$SP/manifests/jars-app-no-lombok.txt" || echo "LOMBOK GONE FROM APP JAR"
grep -c lombok "$SP/manifests/jars-ie-no-lombok.txt"  || echo "LOMBOK GONE FROM IE JAR"
head -1 "$SP/manifests/jars-app-no-lombok.txt" "$SP/manifests/jars-ie-no-lombok.txt"
```

Expected: both `... GONE ...` messages, and both `TOTAL` lines ~1.95MB smaller than their
`-before` counterparts.

- [ ] **Step 5: Prove the app still starts — Lombok's absence at runtime must not matter**

Lombok generates code at compile time, so nothing should reference it at runtime. Prove it
by starting the app, not just by building it.

```bash
mvn install -DskipTests -Ddockerfile.skip=false -pl msa/tbmq 2>&1 | tail -10
timeout 75 docker run --rm thingsboard/tbmq:2.4.1-SNAPSHOT 2>&1 | head -20
```

Expected: the same startup sequence as Task 2 Step 8, ending at
`Connection to localhost:5432 refused`. Any `NoClassDefFoundError` or
`ClassNotFoundException` mentioning `lombok` means something genuinely needs it at
runtime — revert this task if so.

- [ ] **Step 6: Commit**

```bash
git add pom.xml
git commit -m "chore(build): exclude Lombok from the Spring Boot fat jars

Lombok is scoped provided, but spring-boot-maven-plugin packages
provided-scope dependencies into the fat jar by default, so a
compile-time-only annotation processor was shipping to production in both
the application and integration-executor jars.

Excluding it removes 1.95MB from each jar. Compilation is unaffected -
Lombok remains on the compile classpath."
```

---


---

## Task 7: End-to-end verification of all three images together

Tasks 2-6 each verified one artifact in isolation. This task verifies the set, in the two
deployment topologies the repo actually ships, and on both published architectures.

**Files:** none modified — this task only builds and runs.

- [ ] **Step 1: Full clean build of everything**

```bash
cd /home/dlandiak/projects/tbmq
mvn clean install -DskipTests -Ddockerfile.skip=false 2>&1 | tail -30
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Re-run all three manifest diffs against the Task 1 baselines**

The poms changed since Tasks 2-4, so the jar sizes will now differ. Everything *except*
the jar size line must still match.

```bash
"$SP/image-manifest.sh" thingsboard/tbmq:2.4.1-SNAPSHOT \
    /usr/share/thingsboard-mqtt-broker /var/log/thingsboard-mqtt-broker \
    > "$SP/manifests/tbmq-final.txt"
"$SP/image-manifest.sh" thingsboard/tbmq-node:2.4.1-SNAPSHOT \
    /usr/share/thingsboard-mqtt-broker /var/log/thingsboard-mqtt-broker \
    > "$SP/manifests/tbmq-node-final.txt"
"$SP/image-manifest.sh" thingsboard/tbmq-integration-executor:2.4.1-SNAPSHOT \
    /usr/share/tbmq-integration-executor /var/log/tbmq-integration-executor \
    > "$SP/manifests/tbmq-ie-final.txt"

for i in tbmq tbmq-node tbmq-ie; do
  echo "=== $i ==="
  diff "$SP/manifests/$i-before.txt" "$SP/manifests/$i-final.txt" || true
done
```

Expected: for each image, the **only** differing lines are the `.jar` size in the install
tree (smaller now) and the `install tree file count` if it changed. Any difference in a
mode, owner, path, symlink target, env var or `java -version` is a regression — go back to
the task that introduced it.

- [ ] **Step 3: Verify the monolith topology (`thingsboard/tbmq`)**

`msa/tbmq/configs/docker-compose.yml` pins `thingsboard/tbmq:2.4.0`, so tag the new build
as that version for the test, and untag afterwards.

```bash
cd /home/dlandiak/projects/tbmq
docker tag thingsboard/tbmq:2.4.1-SNAPSHOT thingsboard/tbmq:2.4.0
cd msa/tbmq/configs
docker compose up -d
docker compose ps
```

Then watch it come up:

```bash
docker compose logs -f tbmq 2>&1 | head -60
```

Expected: the install step completes against Postgres, then the application starts and
binds MQTT. No stack traces about missing classes, missing files, or permission denied on
`/var/log/thingsboard-mqtt-broker` or `/data`.

- [ ] **Step 4: Confirm the monolith writes its log file**

This is the check that proves `mkdir -p ${pkg.unixLogFolder}` + `chown` replaced the
package postinst correctly. It is the single most likely thing to have been missed.

```bash
docker compose exec -T tbmq ls -la /var/log/thingsboard-mqtt-broker/
```

Expected: `thingsboard-mqtt-broker.log` present and non-empty, owned by `thingsboard`.
An empty directory or a permission error means the log folder ownership is wrong.

- [ ] **Step 5: Tear down the monolith stack**

```bash
docker compose down -v
docker rmi thingsboard/tbmq:2.4.0
cd /home/dlandiak/projects/tbmq
```

- [ ] **Step 6: Verify the cluster topology (`tbmq-node` + `tbmq-integration-executor`)**

`docker/.env` pins `TBMQ_VERSION=2.4.0` with `DOCKER_NAME=tbmq-node`, so tag both images
accordingly.

```bash
docker tag thingsboard/tbmq-node:2.4.1-SNAPSHOT thingsboard/tbmq-node:2.4.0
docker tag thingsboard/tbmq-integration-executor:2.4.1-SNAPSHOT thingsboard/tbmq-integration-executor:2.4.0
cd docker
./scripts/docker-create-volumes.sh
./scripts/docker-install-tbmq.sh
./scripts/docker-start-services.sh
docker compose ps
```

Expected: `postgres`, `kafka`, `tbmq1`, `tbmq2`, both `tbmq-integration-executor*` and the
cache service all running.

- [ ] **Step 7: Confirm both node types are healthy and logging**

```bash
docker compose logs tbmq1 2>&1 | tail -30
docker compose logs tbmq-integration-executor1 2>&1 | tail -30
docker compose exec -T tbmq1 ls -la /var/log/thingsboard-mqtt-broker/
docker compose exec -T tbmq-integration-executor1 ls -la /var/log/tbmq-integration-executor/
```

Expected: both applications started; both log directories contain a non-empty `.log` file
owned by `thingsboard`.

- [ ] **Step 8: Tear down the cluster stack**

```bash
./scripts/docker-stop-services.sh
./scripts/docker-remove-services.sh
./scripts/docker-remove-volumes.sh
docker rmi thingsboard/tbmq-node:2.4.0 thingsboard/tbmq-integration-executor:2.4.0
cd /home/dlandiak/projects/tbmq
```

- [ ] **Step 9: Verify the multi-arch release path**

The release profile builds with `buildx` for `linux/amd64,linux/arm64`
(`msa/pom.xml:74`, `:95`). Multi-stage must work there too, and the arm64 build must
succeed. Build without pushing:

```bash
cd /home/dlandiak/projects/tbmq
docker buildx build --platform=linux/amd64,linux/arm64 \
    -t tbmq-multiarch-check:test msa/tbmq/target/docker 2>&1 | tail -25
```

Expected: the build completes for both platforms. `-o type=registry` is deliberately
omitted so nothing is pushed; without an output the result is discarded, which is fine —
we only care that it builds.

Repeat for the other two contexts:

```bash
docker buildx build --platform=linux/amd64,linux/arm64 \
    -t tbmq-node-multiarch-check:test msa/mqtt-broker/target/docker 2>&1 | tail -15
docker buildx build --platform=linux/amd64,linux/arm64 \
    -t tbmq-ie-multiarch-check:test msa/integration/executor/target/docker 2>&1 | tail -15
```

Expected: both complete.

- [ ] **Step 10: Produce the final size report**

```bash
{
  echo "=== BEFORE ==="
  cat "$SP/manifests/sizes-baseline.txt"
  echo "=== AFTER ==="
  docker images --format '{{.Repository}}:{{.Tag}} {{.Size}}' | grep 2.4.1-SNAPSHOT
} | tee "$SP/manifests/sizes-final.txt"
```

Expected, roughly: `tbmq` 778 → ~597MB, `tbmq-node` 934 → ~597MB,
`tbmq-integration-executor` 664 → ~539MB. Treat a result more than ~20MB off as worth
investigating with `docker history` before declaring done.

- [ ] **Step 11: Confirm the final diff is exactly the intended five files**

```bash
git status --short
git diff --stat main...HEAD
git log --oneline main..HEAD
```

Expected: a clean working tree; `--stat` listing exactly
`application/pom.xml`, `pom.xml`, `msa/tbmq/docker/Dockerfile`,
`msa/mqtt-broker/docker/Dockerfile`, `msa/integration/executor/docker/Dockerfile`; and five
commits (Tasks 2-6).

- [ ] **Step 12: Clean up the baseline images**

```bash
docker rmi baseline/tbmq:before baseline/tbmq-node:before baseline/tbmq-integration-executor:before
docker rmi tbmq-multiarch-check:test tbmq-node-multiarch-check:test tbmq-ie-multiarch-check:test 2>/dev/null || true
docker rmi proto-tbmq-base:latest proto-tbmq-new:latest 2>/dev/null || true
```

Expected: the throwaway images are removed. Keep `$SP/manifests/` — it is the evidence for
the pull request.

---

## Task 8: Open the pull request

**Files:** none modified.

- [ ] **Step 1: Read the repository PR template**

The repo has a template that must be filled rather than replaced with an ad-hoc summary.

```bash
cd /home/dlandiak/projects/tbmq
cat .github/PULL_REQUEST_TEMPLATE.md 2>/dev/null || ls .github/
```

- [ ] **Step 2: Push the branch**

```bash
git push -u origin chore/docker-image-size-optimization
```

- [ ] **Step 3: Create the PR, filling the template's own sections**

Use the template's Description plus its General / Frontend / Backend checklists — do not
substitute a different structure. The Description should carry:

- the before/after size table from `$SP/manifests/sizes-final.txt`;
- the statement that image filesystems are byte-identical apart from mtimes and the
  shrunken jar, with the manifest-diff method described;
- that the JDK and base image are deliberately untouched, and why (`jcmd`/`jstack`/`jmap`
  come from `openjdk-25-jdk-headless`; the JRE-only package has none of them);
- that `curl` was removed to match ThingsBoard, which installs it in none of its 13
  Dockerfiles, and that all k8s probes are `tcpSocket`;

Base the PR on the branch the team is currently merging into — recent history shows both
`main` and `develop/2.4` in use, so confirm which before opening it:

```bash
git log --oneline --merges -6
```

---

## Notes for the reviewer / follow-ups discovered while planning

These are out of scope here but were found during the audit and should not be lost:

1. **`msa/black-box-tests` is dead.** No `src` directory, not listed in any `<modules>`,
   only a stale `target/black-box-tests-2.0.1-SNAPSHOT.jar`. It looks like a test module
   but cannot run anything. Either restore or remove it.
2. **`netty-all` and the arm64 epoll native — dropped from this plan, worth its own.**
   `application/pom.xml:55` declares `netty-all` alongside the three specific modules it
   duplicates (`netty-handler`, `netty-codec-mqtt`, `netty-transport-native-epoll`), and
   main sources import only `bootstrap`, `buffer`, `channel{,.nio,.socket}`,
   `handler.{codec,ipfilter,ssl,timeout}` and `util{,.concurrent}` — all covered by those
   three. Replacing it would drop ~12 unusable modules (riscv64 epoll, osx kqueue, macos
   DNS natives, sctp/udt/rxtx transports, memcache/stomp/smtp/xml/haproxy codecs,
   ssl-ocsp), but only ~0.5MB, so it is not worth carrying here.

   **If anyone does pick it up:** `netty-all` is the *only* source of
   `netty-transport-native-epoll:linux-aarch_64` — `application/pom.xml:47-51` pins
   `linux-x86_64` explicitly. Since images are published for `linux/arm64` and Reactor
   Netty picks epoll up by auto-detection, deleting `netty-all` without adding an explicit
   `linux-aarch_64` classifier would push ARM deployments back to NIO **silently**. Also
   note `common/integration/integration-api/pom.xml:73` declares `netty-all` at `provided`
   scope, so that one is already runtime-irrelevant.

3. **The IE jar has no arm64 epoll native at all** (`linux-x86_64` only), so
   `tbmq-integration-executor` already falls back to NIO on ARM today — a pre-existing gap,
   independent of anything in this plan.
4. **`thingsboard/tbmq-pe`** has the identical Dockerfile shape and the same dead `tink`
   declaration; the whole of this plan ports over.
5. **The legacy Docker builder is deprecated.** Docker 29 warns it "will be removed in a
   future release". This plan does not depend on it — everything works on both builders —
   but the Spotify `dockerfile-maven-plugin` path will eventually need replacing.
6. **The 356MB JDK layer remains the largest single opportunity** (~−265MB with a `jlink`
   runtime). Deliberately declined here. If revisited, the right place is a `-jre` variant
   of the `thingsboard/openjdk25` base image in its own repo, so ThingsBoard benefits and
   no Dockerfile here changes its base. See the spec's *Rejected alternatives* for the
   measurements and the two traps (`java.security` `networkaddress.cache.ttl=60` is
   silently dropped by `jlink`; `cacerts` is correctly resolved).
