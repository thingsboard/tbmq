#!/bin/bash
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

function compose_version() {
  #Checking whether "set -e" shell option should be restored after Compose version check
  flag_set=false
  if [[ $SHELLOPTS =~ errexit ]]; then
    set +e
    flag_set=true
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

  if $flag_set; then set -e; fi
}

function create_volume_if_not_exists() {
  local volume_name=$1
  if docker volume inspect "$volume_name" >/dev/null 2>&1; then
    echo "Volume '$volume_name' already exists."
  else
    docker volume create "$volume_name"
    echo "Volume '$volume_name' created."
  fi
}

set -u

# Check if docker-compose.yml is present
if [ -f "docker-compose.yml" ]; then
  echo "docker-compose.yml is already present in the current directory. Skipping download."
else
  echo "docker-compose.yml is absent in the current directory. Downloading the file..."
  wget https://raw.githubusercontent.com/thingsboard/tbmq/release-1.2.1/msa/tbmq/configs/docker-compose.yml
fi

COMPOSE_VERSION=$(compose_version) || exit $?
echo "Docker Compose version is: $COMPOSE_VERSION"

# Define the string to search for
search_string="thingsboard/tbmq"
# Check if the Docker Compose file contains the search_string
if grep -q "$search_string" docker-compose.yml; then
  echo "The Docker Compose file is ok, checking volumes..."
else
  echo "The Docker Compose file missing tbmq. Seems the file is invalid for tbmq configuration."
  exit 1
fi

create_volume_if_not_exists tbmq-postgres-data
create_volume_if_not_exists tbmq-kafka-data
create_volume_if_not_exists tbmq-logs
create_volume_if_not_exists tbmq-data

echo "Starting TBMQ!"
case $COMPOSE_VERSION in
V2)
  docker compose up -d
  docker compose logs -f
  ;;
V1)
  docker-compose up -d
  docker-compose logs -f
  ;;
*)
  # unknown option
  ;;
esac
