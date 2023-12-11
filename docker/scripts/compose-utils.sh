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

readonly REDIS_VOLUME="redis-data"
readonly REDIS_CLUSTER_VOLUMES=(
  "redis-cluster-data-0"
  "redis-cluster-data-1"
  "redis-cluster-data-2"
  "redis-cluster-data-3"
  "redis-cluster-data-4"
  "redis-cluster-data-5"
)
readonly REDIS_SENTINEL_VOLUMES=(
  "redis-sentinel-data-master"
  "redis-sentinel-data-slave"
  "redis-sentinel-data-sentinel"
)
readonly MY_VOLUMES=(
  "tbmq-postgres-data"
  "tbmq-kafka-data"
  "tbmq1-logs"
  "tbmq2-logs"
  "tbmq-config"
  "tbmq-haproxy-config"
  "tbmq-haproxy-letsencrypt"
  "tbmq-haproxy-certs-d"
)

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

function volume_exists() {
  docker volume inspect $1 &>/dev/null
}

function delete_volume() {
  docker volume rm $1
}

function checkVolumes() {
  # Source environment variables
  source .env

  if [[ "$1" == "--create" ]]; then
    case $CACHE in
    redis)
      if ! volume_exists $REDIS_VOLUME; then
        docker volume create $REDIS_VOLUME
      fi
      ;;
    redis-cluster)
      for volume in "${REDIS_CLUSTER_VOLUMES[@]}"; do
        if ! volume_exists $volume; then
          docker volume create $volume
        fi
      done
      ;;
    redis-sentinel)
      for volume in "${REDIS_SENTINEL_VOLUMES[@]}"; do
        if ! volume_exists $volume; then
          docker volume create $volume
        fi
      done
      ;;
    *)
      echo "Unknown CACHE value specified in the .env file: '${CACHE}'. Should be either 'redis' or 'redis-cluster' or 'redis-sentinel'." >&2
      exit 1
      ;;
    esac
  else
    case $CACHE in
    redis)
      if ! volume_exists $REDIS_VOLUME; then
        echo "Volume $REDIS_VOLUME is absent." >&2
        exit 1
      fi
      ;;
    redis-cluster)
      for volume in "${REDIS_CLUSTER_VOLUMES[@]}"; do
        if ! volume_exists $volume; then
          echo "Volume $volume is absent." >&2
          exit 1
        fi
      done
      ;;
    redis-sentinel)
      for volume in "${REDIS_SENTINEL_VOLUMES[@]}"; do
        if ! volume_exists $volume; then
          echo "Volume $volume is absent." >&2
          exit 1
        fi
      done
      ;;
    *)
      echo "Unknown CACHE value specified in the .env file: '${CACHE}'. Should be either 'redis' or 'redis-cluster' or 'redis-sentinel'." >&2
      exit 1
      ;;
    esac
  fi

  # Create additional volumes if needed
  for volume in "${MY_VOLUMES[@]}"; do
    if [[ "$1" == "--create" ]]; then
      if ! volume_exists $volume; then
        docker volume create $volume
      fi
    else
      if ! volume_exists $volume; then
        echo "Volume $volume is absent." >&2
        exit 1
      fi
    fi
  done

  # Display a message if volumes are present and no --create flag provided
  if [[ "$1" != "--create" ]]; then
    echo "All volumes are present."
  fi
}

function deleteVolumes() {
  source .env

  function delete_if_exists() {
    local volume_name=$1
    if volume_exists "$volume_name"; then
      if ! delete_volume "$volume_name"; then
        echo "Failed to delete volume: $volume_name" >&2
      fi
    fi
  }

  case $CACHE in
  redis)
    delete_if_exists "$REDIS_VOLUME"
    ;;
  redis-cluster)
    for volume in "${REDIS_CLUSTER_VOLUMES[@]}"; do
      delete_if_exists "$volume"
    done
    ;;
  redis-sentinel)
    for volume in "${REDIS_SENTINEL_VOLUMES[@]}"; do
      delete_if_exists "$volume"
    done
    ;;
  *)
    echo "Unknown CACHE value specified in the .env file: '${CACHE}'. Should be either 'redis' or 'redis-cluster' or 'redis-sentinel'." >&2
    exit 1
    ;;
  esac

  # Delete other volumes
  for volume in "${MY_VOLUMES[@]}"; do
    delete_if_exists "$volume"
  done
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
