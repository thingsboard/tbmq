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

# Upgrades the TBMQ cluster to the TBMQ_VERSION set in .env.
#
# Usage: ./scripts/docker-upgrade-tbmq.sh [--mode=rolling|stop]
#
#   rolling  Upgrade the database while the current nodes keep serving clients, then replace the TBMQ nodes and the
#            integration executors one at a time. HAProxy sends clients to the node that is still up.
#   stop     Stop the TBMQ nodes and integration executors, upgrade the database, then start the cluster.
#
# Without --mode, the script uses rolling when ROLLING_UPGRADE_SUPPORTED is true, and stop otherwise.

# Review on every release: true when the upgrade from the previous release can run node by node, with the old and the
# new version running side by side; false when every TBMQ node must be stopped before the database upgrade.
ROLLING_UPGRADE_SUPPORTED=true

# Seconds to wait for a replaced node to accept connections.
NODE_READY_TIMEOUT=${NODE_READY_TIMEOUT:-300}
# Seconds to wait after a TBMQ node is ready before replacing the next one,
# so that HAProxy health checks (every 5s) mark it as up again.
NODE_SETTLE_SECONDS=${NODE_SETTLE_SECONDS:-30}

TBMQ_NODES="tbmq1 tbmq2"
TBMQ_MQTT_PORT=1883
IE_NODES="tbmq-integration-executor1 tbmq-integration-executor2"
IE_HTTP_PORT=8082

MODE=""
for i in "$@"; do
  case $i in
    --mode=*)
      MODE="${i#*=}"
      ;;
    *)
      echo "Unknown option: $i" >&2
      echo "Usage: $0 [--mode=rolling|stop]" >&2
      exit 1
      ;;
  esac
done

set -e

source scripts/compose-utils.sh

COMPOSE_VERSION=$(composeVersion) || exit $?

ADDITIONAL_CACHE_ARGS=$(additionalComposeCacheArgs) || exit $?

ADDITIONAL_STARTUP_SERVICES=$(additionalStartupServices) || exit $?

checkVolumes

source .env

function compose() {
  local args="--env-file ./.env -f docker-compose.yml ${ADDITIONAL_CACHE_ARGS}"
  case $COMPOSE_VERSION in
    V2) docker compose $args "$@" ;;
    V1) docker-compose $args "$@" ;;
  esac
}

function serviceRunning() {
  local id
  id=$(compose ps -q "$1" 2>/dev/null)
  [ -n "$id" ] && [ "$(docker inspect -f '{{.State.Running}}' "$id" 2>/dev/null)" = "true" ]
}

function waitServicePort() {
  local service=$1
  local port=$2
  echo "Waiting for $service to accept connections on port $port..."
  for ((waited = 0; waited < NODE_READY_TIMEOUT; waited += 5)); do
    if compose exec -T "$service" bash -c ": </dev/tcp/127.0.0.1/$port" >/dev/null 2>&1; then
      echo "$service is ready."
      return 0
    fi
    sleep 5
  done
  echo "ERROR: $service did not accept connections on port $port within ${NODE_READY_TIMEOUT}s." >&2
  echo "Check its logs with: docker compose logs $service" >&2
  return 1
}

if [ -z "$MODE" ]; then
  if $ROLLING_UPGRADE_SUPPORTED; then MODE=rolling; else MODE=stop; fi
fi

case $MODE in
  rolling)
    if ! $ROLLING_UPGRADE_SUPPORTED; then
      echo "ERROR: upgrading to TBMQ ${TBMQ_VERSION} needs every TBMQ node stopped. Run with --mode=stop." >&2
      exit 1
    fi
    for node in $TBMQ_NODES; do
      if ! serviceRunning "$node"; then
        echo "$node is not running, so there is no running cluster to keep serving clients. Switching to --mode=stop."
        MODE=stop
        break
      fi
    done
    ;;
  stop) ;;
  *)
    echo "Unknown mode: '$MODE'. Use --mode=rolling or --mode=stop." >&2
    exit 1
    ;;
esac

echo "Upgrading TBMQ to ${TBMQ_VERSION} (mode: $MODE)."

compose pull tbmq1 tbmq-integration-executor1

if [ "$MODE" = "stop" ]; then
  compose stop $IE_NODES $TBMQ_NODES
  compose up -d $ADDITIONAL_STARTUP_SERVICES
else
  # Start the database and cache if they are down, but never restart them while the cluster is serving clients.
  compose up -d --no-recreate $ADDITIONAL_STARTUP_SERVICES
fi

# Upgrade the database.
case $COMPOSE_VERSION in
  V2) docker compose --env-file ./.env --env-file ./tbmq.env -f docker-compose.yml ${ADDITIONAL_CACHE_ARGS} \
        run --no-deps --rm -e UPGRADE_TB=true tbmq1 ;;
  V1) docker-compose --env-file ./.env --env-file ./tbmq.env -f docker-compose.yml ${ADDITIONAL_CACHE_ARGS} \
        run --no-deps --rm -e UPGRADE_TB=true tbmq1 ;;
esac

if [ "$MODE" = "stop" ]; then
  compose up -d
else
  last_node=${TBMQ_NODES##* }
  for node in $TBMQ_NODES; do
    echo "Replacing $node..."
    compose up -d --no-deps "$node"
    waitServicePort "$node" "$TBMQ_MQTT_PORT"
    if [ "$node" != "$last_node" ]; then
      echo "Waiting ${NODE_SETTLE_SECONDS}s for HAProxy to route clients to $node again..."
      sleep "$NODE_SETTLE_SECONDS"
    fi
  done
  for node in $IE_NODES; do
    echo "Replacing $node..."
    compose up -d --no-deps "$node"
    waitServicePort "$node" "$IE_HTTP_PORT"
  done
fi

echo "TBMQ is upgraded to ${TBMQ_VERSION}."
