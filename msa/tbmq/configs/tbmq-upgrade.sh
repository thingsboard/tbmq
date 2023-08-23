#!/bin/bash
#
# Copyright © 2016-2023 The Thingsboard Authors
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

function composeVersion() {
  #Checking whether "set -e" shell option should be restored after Compose version check
  FLAG_SET=false
  if [[ $SHELLOPTS =~ errexit ]]; then
    set +e
    FLAG_SET=true
  fi

  #Checking Compose V1 availability
  docker-compose version >/dev/null 2>&1
  if [ $? -eq 0 ]; then status_v1=true; else status_v1=false; fi

  #Checking Compose V2 availability
  docker compose version >/dev/null 2>&1
  if [ $? -eq 0 ]; then status_v2=true; else status_v2=false; fi

  COMPOSE_VERSION=""

  if $status_v2; then
    COMPOSE_VERSION="V2"
  elif $status_v1; then
    COMPOSE_VERSION="V1"
  else
    echo "Docker Compose plugin is not detected. Please check your environment." >&2
    exit 1
  fi

  echo $COMPOSE_VERSION

  if $FLAG_SET; then set -e; fi
}

set -u

# Define TBMQ versions
old_version="1.0.1"
new_version="1.1.0-SNAPSHOT"

# Define TBMQ images
old_image="image: \"thingsboard/tbmq:$old_version\""
new_image="image: \"thingsboard/tbmq:$new_version\""

# Define DB variables
db_url="jdbc:postgresql://postgres:5432/thingsboard_mqtt_broker"
db_username="postgres"
db_password="postgres"

# Define data directory
data_dir="$HOME/.tb-mqtt-broker-data/data"

COMPOSE_VERSION=$(composeVersion) || exit $?
echo 'Docker Compose version is: '$COMPOSE_VERSION

docker pull thingsboard/tbmq:$new_version

# Backup the original Docker Compose file
cp docker-compose.yml docker-compose.yml.bak
echo "Docker Compose file backup created: docker-compose.yml.bak"

# Replace the TBMQ image version using sed
echo "Trying to replace the TBMQ image version from [$old_image] to [$new_image]..."
sed -i "s#$old_image#$new_image#g" docker-compose.yml

if grep -q "$new_image" docker-compose.yml; then
    echo "TBMQ image line updated in docker-compose.yml with the new version"
else
    echo "Failed to replace the image version. Please, update the version manually and re-run the script"
    exit 1
fi

case $COMPOSE_VERSION in
V2)
  docker compose stop tbmq

  postgresContainerName=$(docker compose ps | grep "postgres" | awk '{ print $1 }')

  composeNetworkId=$(docker inspect -f '{{ range .NetworkSettings.Networks }}{{ .NetworkID }}{{ end }}' $postgresContainerName)

  docker run -it --network=$composeNetworkId \
    -e SPRING_DATASOURCE_URL=$db_url \
    -e SPRING_DATASOURCE_USERNAME=$db_username \
    -e SPRING_DATASOURCE_PASSWORD=$db_password \
    -v $data_dir:/data \
    --rm \
    thingsboard/tbmq:$new_version upgrade-tbmq.sh

  docker compose rm tbmq

  docker compose up -d tbmq --no-deps
  ;;
V1)
  docker-compose stop tbmq

  postgresContainerName=$(docker-compose ps | grep "postgres" | awk '{ print $1 }')

  composeNetworkId=$(docker inspect -f '{{ range .NetworkSettings.Networks }}{{ .NetworkID }}{{ end }}' $postgresContainerName)

  docker run -it --network=$composeNetworkId \
    -e SPRING_DATASOURCE_URL=$db_url \
    -e SPRING_DATASOURCE_USERNAME=$db_username \
    -e SPRING_DATASOURCE_PASSWORD=$db_password \
    -v $data_dir:/data \
    --rm \
    thingsboard/tbmq:$new_version upgrade-tbmq.sh

  docker-compose rm tbmq

  docker-compose up -d tbmq --no-deps
  ;;
*)
  # unknown option
  ;;
esac

