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

function additionalComposeCacheArgs() {
  source .env
  CACHE_COMPOSE_ARGS=""
  CACHE="${CACHE:-redis}"
  case $CACHE in
  redis)
    CACHE_COMPOSE_ARGS="-f cache/docker-compose.redis.yml"
    ;;
  redis-cluster)
    CACHE_COMPOSE_ARGS="-f cache/docker-compose.redis-cluster.yml"
    ;;
  redis-sentinel)
    CACHE_COMPOSE_ARGS="-f cache/docker-compose.redis-sentinel.yml"
    ;;
  *)
    echo "Unknown CACHE value specified in the .env file: '${CACHE}'. Should be either 'redis' or 'redis-cluster' or 'redis-sentinel'." >&2
    exit 1
    ;;
  esac
  echo $CACHE_COMPOSE_ARGS
}

function additionalStartupServices() {
  source .env
  ADDITIONAL_STARTUP_SERVICES="postgres"

  CACHE="${CACHE:-redis}"
  case $CACHE in
  redis)
    ADDITIONAL_STARTUP_SERVICES="$ADDITIONAL_STARTUP_SERVICES redis"
    ;;
  redis-cluster)
    ADDITIONAL_STARTUP_SERVICES="$ADDITIONAL_STARTUP_SERVICES redis-node-0 redis-node-1 redis-node-2 redis-node-3 redis-node-4 redis-node-5"
    ;;
  redis-sentinel)
    ADDITIONAL_STARTUP_SERVICES="$ADDITIONAL_STARTUP_SERVICES redis-master redis-slave redis-sentinel"
    ;;
  *)
    echo "Unknown CACHE value specified in the .env file: '${CACHE}'. Should be either 'redis' or 'redis-cluster' or 'redis-sentinel'." >&2
    exit 1
    ;;
  esac

  echo $ADDITIONAL_STARTUP_SERVICES
}

function permissionList() {
  PERMISSION_LIST="
      799  799  tb-mqtt-broker/log
      999  999  tb-mqtt-broker/postgres
      1001  1001  tb-mqtt-broker/kafka
      "

  source .env

  CACHE="${CACHE:-redis}"
  case $CACHE in
  redis)
    PERMISSION_LIST="$PERMISSION_LIST
          1001 1001 tb-mqtt-broker/redis-data
          "
    ;;
  redis-cluster)
    PERMISSION_LIST="$PERMISSION_LIST
          1001 1001 tb-mqtt-broker/redis-cluster-data-0
          1001 1001 tb-mqtt-broker/redis-cluster-data-1
          1001 1001 tb-mqtt-broker/redis-cluster-data-2
          1001 1001 tb-mqtt-broker/redis-cluster-data-3
          1001 1001 tb-mqtt-broker/redis-cluster-data-4
          1001 1001 tb-mqtt-broker/redis-cluster-data-5
          "
    ;;
  redis-sentinel)
    PERMISSION_LIST="$PERMISSION_LIST
          1001 1001 tb-mqtt-broker/redis-sentinel-data-master
          1001 1001 tb-mqtt-broker/redis-sentinel-data-slave
          1001 1001 tb-mqtt-broker/redis-sentinel-data-sentinel
          "
    ;;
  *)
    echo "Unknown CACHE value specified in the .env file: '${CACHE}'. Should be either 'redis' or 'redis-cluster' or 'redis-sentinel'." >&2
    exit 1
    ;;
  esac

  echo "$PERMISSION_LIST"
}

function checkFolders() {
  EXIT_CODE=0
  PERMISSION_LIST=$(permissionList) || exit $?
  set -e
  while read -r USR GRP DIR; do
    if [ -z "$DIR" ]; then # skip empty lines
      continue
    fi
    MESSAGE="Checking user ${USR} group ${GRP} dir ${DIR}"
    if [[ -d "$DIR" ]] &&
      [[ $(ls -ldn "$DIR" | awk '{print $3}') -eq "$USR" ]] &&
      [[ $(ls -ldn "$DIR" | awk '{print $4}') -eq "$GRP" ]]; then
      MESSAGE="$MESSAGE OK"
    else
      if [ "$1" = "--create" ]; then
        echo "Create and chown: user ${USR} group ${GRP} dir ${DIR}"
        mkdir -p "$DIR" && sudo chown -R "$USR":"$GRP" "$DIR"
      else
        echo "$MESSAGE FAILED"
        EXIT_CODE=1
      fi
    fi
  done < <(echo "$PERMISSION_LIST")
  return $EXIT_CODE
}

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
