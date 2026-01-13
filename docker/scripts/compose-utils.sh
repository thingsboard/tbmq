#!/bin/bash
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

readonly VALKEY_VOLUME="valkey-data"
readonly VALKEY_CLUSTER_VOLUMES=(
  "valkey-cluster-data-0"
  "valkey-cluster-data-1"
  "valkey-cluster-data-2"
  "valkey-cluster-data-3"
  "valkey-cluster-data-4"
  "valkey-cluster-data-5"
)
readonly VALKEY_SENTINEL_VOLUMES=(
  "valkey-sentinel-data-primary"
  "valkey-sentinel-data-slave"
  "valkey-sentinel-data-sentinel"
)
readonly MY_VOLUMES=(
  "tbmq-postgres-data"
  "tbmq-kafka-data"
  "tbmq-kafka-secrets"
  "tbmq-kafka-config"
  "tbmq1-logs"
  "tbmq2-logs"
  "tbmq-config"
  "tbmq-haproxy-config"
  "tbmq-haproxy-letsencrypt"
  "tbmq-haproxy-certs-d"
  "tbmq-ie1-logs"
  "tbmq-ie2-logs"
  "tbmq-ie-config"
)

function additionalComposeCacheArgs() {
  source .env
  CACHE_COMPOSE_ARGS=""
  CACHE="${CACHE:-valkey}"
  case $CACHE in
  valkey)
    CACHE_COMPOSE_ARGS="-f cache/docker-compose.valkey.yml"
    ;;
  valkey-cluster)
    CACHE_COMPOSE_ARGS="-f cache/docker-compose.valkey-cluster.yml"
    ;;
  valkey-sentinel)
    CACHE_COMPOSE_ARGS="-f cache/docker-compose.valkey-sentinel.yml"
    ;;
  *)
    echo "Unknown CACHE value specified in the .env file: '${CACHE}'. Should be either 'valkey' or 'valkey-cluster' or 'valkey-sentinel'." >&2
    exit 1
    ;;
  esac
  echo $CACHE_COMPOSE_ARGS
}

function additionalStartupServices() {
  source .env
  ADDITIONAL_STARTUP_SERVICES="postgres"

  CACHE="${CACHE:-valkey}"
  case $CACHE in
  valkey)
    ADDITIONAL_STARTUP_SERVICES="$ADDITIONAL_STARTUP_SERVICES valkey"
    ;;
  valkey-cluster)
    ADDITIONAL_STARTUP_SERVICES="$ADDITIONAL_STARTUP_SERVICES valkey-node-0 valkey-node-1 valkey-node-2 valkey-node-3 valkey-node-4 valkey-node-5"
    ;;
  valkey-sentinel)
    ADDITIONAL_STARTUP_SERVICES="$ADDITIONAL_STARTUP_SERVICES valkey-primary valkey-slave valkey-sentinel"
    ;;
  *)
    echo "Unknown CACHE value specified in the .env file: '${CACHE}'. Should be either 'valkey' or 'valkey-cluster' or 'valkey-sentinel'." >&2
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
    valkey)
      if ! volume_exists $VALKEY_VOLUME; then
        docker volume create $VALKEY_VOLUME
      fi
      ;;
    valkey-cluster)
      for volume in "${VALKEY_CLUSTER_VOLUMES[@]}"; do
        if ! volume_exists $volume; then
          docker volume create $volume
        fi
      done
      ;;
    valkey-sentinel)
      for volume in "${VALKEY_SENTINEL_VOLUMES[@]}"; do
        if ! volume_exists $volume; then
          docker volume create $volume
        fi
      done
      ;;
    *)
      echo "Unknown CACHE value specified in the .env file: '${CACHE}'. Should be either 'valkey' or 'valkey-cluster' or 'valkey-sentinel'." >&2
      exit 1
      ;;
    esac
  else
    case $CACHE in
    valkey)
      if ! volume_exists $VALKEY_VOLUME; then
        echo "Volume $VALKEY_VOLUME is absent." >&2
        exit 1
      fi
      ;;
    valkey-cluster)
      for volume in "${VALKEY_CLUSTER_VOLUMES[@]}"; do
        if ! volume_exists $volume; then
          echo "Volume $volume is absent." >&2
          exit 1
        fi
      done
      ;;
    valkey-sentinel)
      for volume in "${VALKEY_SENTINEL_VOLUMES[@]}"; do
        if ! volume_exists $volume; then
          echo "Volume $volume is absent." >&2
          exit 1
        fi
      done
      ;;
    *)
      echo "Unknown CACHE value specified in the .env file: '${CACHE}'. Should be either 'valkey' or 'valkey-cluster' or 'valkey-sentinel'." >&2
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
  valkey)
    delete_if_exists "$VALKEY_VOLUME"
    ;;
  valkey-cluster)
    for volume in "${VALKEY_CLUSTER_VOLUMES[@]}"; do
      delete_if_exists "$volume"
    done
    ;;
  valkey-sentinel)
    for volume in "${VALKEY_SENTINEL_VOLUMES[@]}"; do
      delete_if_exists "$volume"
    done
    ;;
  *)
    echo "Unknown CACHE value specified in the .env file: '${CACHE}'. Should be either 'valkey' or 'valkey-cluster' or 'valkey-sentinel'." >&2
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

# Wait until valkey-node-5 replies with PONG (only when CACHE=valkey-cluster)
function waitValkeyNodesAreRunning() {
  [ -f .env ] && source .env

  local cache="${CACHE:-valkey}"
  [ "$cache" = "valkey-cluster" ] || return 0

  [ -f cache-valkey-cluster.env ] && source cache-valkey-cluster.env

  echo "Waiting for valkey-node-5 to respond with PONG"
  for i in $(seq 1 30); do
    if docker exec -e REDISCLI_AUTH="$REDIS_PASSWORD" valkey-node-5 sh -c "valkey-cli -h 127.0.0.1 -p 6379 ping | grep -q '^PONG$'"; then
      echo " ... PONG"
      return 0
    fi
    echo "waiting 1s"
    sleep 1
  done
  echo
  echo "ERROR: valkey-node-5 did not respond with PONG within 30s." >&2
  return 1
}

createValkeyClusterIfNeeded() {
  [ -f .env ] && source .env

  local cache="${CACHE:-valkey}"
  [ "$cache" = "valkey-cluster" ] || return 0

  local nodes="${VALKEY_CLUSTER_NODES:-valkey-node-0:6379 valkey-node-1:6379 valkey-node-2:6379 valkey-node-3:6379 valkey-node-4:6379 valkey-node-5:6379}"
  local replicas="${VALKEY_CLUSTER_REPLICAS:-1}"

  if docker exec -e REDISCLI_AUTH="$REDIS_PASSWORD" valkey-node-5 sh -c "valkey-cli -h 127.0.0.1 -p 6379 cluster info | grep -q 'cluster_state:ok'"; then
    echo "Valkey cluster already formed. Skipping creation."
    return 0
  fi

  echo "Creating Valkey cluster (replicas=${replicas})..."
  if docker exec -e REDISCLI_AUTH="$REDIS_PASSWORD" valkey-node-5 sh -c "valkey-cli --cluster create ${nodes} --cluster-replicas ${replicas} --cluster-yes"; then
    echo "Cluster created."
    return 0
  else
    echo "Cluster creation failed." >&2
    return 1
  fi
}
