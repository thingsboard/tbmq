#
# Copyright © 2016-2024 The Thingsboard Authors
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

FROM thingsboard/openjdk17:bullseye-slim

ENV DATA_FOLDER=/data

COPY logback.xml ${pkg.name}.conf start-tbmq.sh install-tbmq.sh upgrade-tbmq.sh ${pkg.name}.deb /tmp/

RUN apt-get update \
    && chmod a+x /tmp/*.sh \
    && mv /tmp/start-tbmq.sh /usr/bin \
    && mv /tmp/install-tbmq.sh /usr/bin \
    && mv /tmp/upgrade-tbmq.sh /usr/bin \
    && dpkg -i /tmp/${pkg.name}.deb \
    && rm /tmp/${pkg.name}.deb \
    && (systemctl --no-reload disable --now ${pkg.name}.service > /dev/null 2>&1 || :) \
    && mv /tmp/logback.xml ${pkg.installFolder}/conf \
    && mv /tmp/${pkg.name}.conf ${pkg.installFolder}/conf \
    && mkdir -p $DATA_FOLDER \
    && chown -R ${pkg.user}:${pkg.user} $DATA_FOLDER \
    && chown -R ${pkg.user}:${pkg.user} /var/log/${pkg.name} \
    && chmod 555 ${pkg.installFolder}/bin/${pkg.name}.jar

USER ${pkg.user}

VOLUME ["/data"]

CMD ["start-tbmq.sh"]
