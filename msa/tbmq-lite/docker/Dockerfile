#
# Copyright © 2016-2026 The Thingsboard Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Builder: a JDK 25 with jlink. The architecture is read inside the stage (dpkg), not from TARGETARCH, because the
# builder of the Maven Docker plugin does not set it. This file is filtered by Maven (only the project version is
# substituted), so the shell uses $VAR and never the brace form.
FROM eclipse-temurin:25-jdk AS builder

COPY tbmq-lite-${project.version}-boot.jar /build/tbmq-lite.jar
COPY trim-natives.sh /build/trim-natives.sh

# the boot jar becomes a thin jar with its libraries in lib/ (a plain class path)
RUN set -eux; \
    java -Djarmode=tools -jar /build/tbmq-lite.jar extract --destination /usr/share/tbmq-lite/app; \
    bash /build/trim-natives.sh /usr/share/tbmq-lite/app "$(dpkg --print-architecture)" /usr/share/tbmq-lite/native

# the modules the application needs, plus the ones that are found only by reflection or service loading
# (jdk.crypto.ec only if this JDK still has it) and the diagnostic tools (jcmd, attach, the remote JMX agent)
RUN set -eux; \
    cd /usr/share/tbmq-lite/app; \
    jdeps --ignore-missing-deps --multi-release 25 --print-module-deps tbmq-lite.jar lib/*.jar > /tmp/jdeps.out; \
    modules="$(tail -n 1 /tmp/jdeps.out)"; \
    modules="$modules,jdk.unsupported,jdk.management,jdk.management.agent,jdk.jcmd,jdk.attach"; \
    if java --list-modules | grep -q '^jdk.crypto.ec[@]'; then modules="$modules,jdk.crypto.ec"; fi; \
    echo "jlink modules: $modules"; \
    jlink --strip-debug --no-header-files --no-man-pages --compress=zip-6 \
      --add-modules "$modules" --output /usr/share/tbmq-lite/jre

FROM debian:trixie-slim

# Java's TLS uses the runtime's own cacerts, so jemalloc is the only package
RUN apt-get update \
    && apt-get install -y --no-install-recommends libjemalloc2 \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 799 tbmq \
    && useradd --uid 799 --gid 799 --no-create-home --shell /usr/sbin/nologin tbmq \
    && mkdir /data \
    && chown 799:799 /data

COPY --from=builder /usr/share/tbmq-lite /usr/share/tbmq-lite
COPY logback.xml /usr/share/tbmq-lite/conf/logback.xml
COPY healthcheck.sh /usr/share/tbmq-lite/healthcheck.sh
COPY start-tbmq-lite.sh /usr/bin/start-tbmq-lite.sh
RUN chmod 755 /usr/share/tbmq-lite/healthcheck.sh /usr/bin/start-tbmq-lite.sh

ENV DATA_FOLDER=/data
# jcmd and the other tools of the bundled runtime
ENV PATH=/usr/share/tbmq-lite/jre/bin:$PATH

# numeric, so that Kubernetes runAsNonRoot can verify it (the tbmq entries stay for id and jcmd)
USER 799:799
WORKDIR /data
VOLUME ["/data"]
EXPOSE 1883 8083 8084

HEALTHCHECK --interval=10s --timeout=10s --start-period=60s CMD ["/usr/share/tbmq-lite/healthcheck.sh"]

CMD ["start-tbmq-lite.sh"]
