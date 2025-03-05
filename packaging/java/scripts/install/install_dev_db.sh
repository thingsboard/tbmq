#!/bin/bash
#
# Copyright © 2016-2025 The Thingsboard Authors
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

BASE=${project.basedir}/target
CONF_FOLDER=${BASE}/conf
jarfile="${BASE}/thingsboard-mqtt-broker-${project.version}-boot.jar"
installDir=${BASE}/data

export JAVA_OPTS="$JAVA_OPTS -Dplatform=@pkg.platform@"
export LOADER_PATH=${BASE}/conf
export SQL_DATA_FOLDER=${SQL_DATA_FOLDER:-/tmp}

run_user="$USER"

sudo -u "$run_user" -s /bin/sh -c "java -cp ${jarfile} $JAVA_OPTS -Dloader.main=org.thingsboard.mqtt.broker.ThingsboardMqttBrokerInstallApplication \
                    -Dinstall.data_dir=${installDir} \
                    -Dspring.jpa.hibernate.ddl-auto=none \
                    -Dinstall.upgrade=false \
                    org.springframework.boot.loader.launch.PropertiesLauncher"

if [ $? -ne 0 ]; then
    echo "TBMQ DB installation failed!"
else
    echo "TBMQ DB installed successfully!"
fi

exit $?
