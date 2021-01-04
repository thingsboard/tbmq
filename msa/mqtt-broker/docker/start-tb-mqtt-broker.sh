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

export jarfile=${pkg.installFolder}/bin/${pkg.name}.jar

export configfile="/config/${pkg.name}.conf"
if [ ! -f ${configfile} ]; then
  export configfile=${pkg.installFolder}/conf/${pkg.name}.conf
fi
export LOADER_PATH=/config,${LOADER_PATH}

export logbackfile="/config/logback.xml"
if [ ! -f ${logbackfile} ]; then
  export logbackfile=${pkg.installFolder}/conf/logback.xml
fi

source "${configfile}"

firstlaunch=${DATA_FOLDER}/.firstlaunch
if [ ! -f ${firstlaunch} ]; then
    install-tb-mqtt-broker.sh --loadDemo
    touch ${firstlaunch}
fi

echo "Starting '${project.name}' ..."

exec java -cp ${jarfile} $JAVA_OPTS -Dloader.main=org.thingsboard.mqtt.broker.ThingsboardMqttBrokerApplication \
                    -Dlogging.config=${logbackfile} \
                    org.springframework.boot.loader.PropertiesLauncher
