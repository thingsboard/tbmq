#!/bin/bash
#
# Copyright © 2016-2020 The Thingsboard Authors
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

source "${CONF_FOLDER}/${configfile}"

echo "Starting '${project.name}' installation ..."

java -cp ${jarfile} $JAVA_OPTS -Dloader.main=org.thingsboard.mqtt.broker.ThingsboardMqttBrokerInstallApplication \
                    -Dspring.jpa.hibernate.ddl-auto=none \
                    -Dinstall.upgrade=false \
                    -Dlogging.config=${logbackfile} \
                    org.springframework.boot.loader.PropertiesLauncher