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
# Usage: ./scripts/docker-upgrade-tbmq.sh [--mode=rolling|stop] [--skip-db-upgrade]
#
#   --mode=rolling     Upgrade the database while the current nodes keep serving clients, then replace the TBMQ nodes
#                      and the integration executors one at a time. HAProxy sends clients to the node that is still up.
#   --mode=stop        Stop the TBMQ nodes and integration executors, upgrade the database, then start the cluster.
#   --skip-db-upgrade  Only replace the nodes. Use it to finish an upgrade whose database step already succeeded,
#                      for example after a node did not become ready.
#
# Without --mode, the script uses rolling when TBMQ_VERSION is listed in ROLLING_UPGRADE_VERSIONS, and stop otherwise.
# It also uses stop when no TBMQ node is running, as there are no clients to keep serving.

# Target versions whose upgrade from the previous release was reviewed and can run node by node, with the old and the
# new version running side by side. Update it in every release bump: add the new version only after confirming that
# its upgrade supports this. Any other TBMQ_VERSION upgrades in stop mode.
ROLLING_UPGRADE_VERSIONS="2.4.0 2.4.1"

# Seconds to wait for a replaced node to accept connections.
NODE_READY_TIMEOUT=${NODE_READY_TIMEOUT:-300}
# Seconds to wait after a TBMQ node is ready before replacing the next one,
# so that HAProxy health checks (every 5s) mark it as up again.
NODE_SETTLE_SECONDS=${NODE_SETTLE_SECONDS:-30}
# A TBMQ node is ready when it accepts connections on any of these ports (MQTT over TCP, MQTT over TLS).
TBMQ_READY_PORTS=${TBMQ_READY_PORTS:-"1883 8883"}

TBMQ_NODES="tbmq1 tbmq2"
IE_NODES="tbmq-integration-executor1 tbmq-integration-executor2"
IE_READY_PORTS=8082

usage="Usage: $0 [--mode=rolling|stop] [--skip-db-upgrade]"
MODE=""
SKIP_DB_UPGRADE=false
for i in "$@"; do
  case $i in
    --mode=rolling | --mode=stop)
      MODE="${i#*=}"
      ;;
    --mode=*)
      echo "Unknown mode: '${i#*=}'. Use --mode=rolling or --mode=stop." >&2
      exit 1
      ;;
    --skip-db-upgrade)
      SKIP_DB_UPGRADE=true
      ;;
    *)
      echo "Unknown option: $i" >&2
      echo "$usage" >&2
      exit 1
      ;;
  esac
done

set -e

source scripts/compose-utils.sh

COMPOSE_VERSION=$(composeVersion) || exit $?

if [ "$COMPOSE_VERSION" != "V2" ]; then
  echo "ERROR: the upgrade needs Docker Compose V2 ('docker compose'). Docker Compose V1 ('docker-compose') is not supported." >&2
  exit 1
fi

ADDITIONAL_CACHE_ARGS=$(additionalComposeCacheArgs) || exit $?

ADDITIONAL_STARTUP_SERVICES=$(additionalStartupServices) || exit $?

checkVolumes

source .env

function compose() {
  docker compose --env-file ./.env -f docker-compose.yml ${ADDITIONAL_CACHE_ARGS} "$@"
}

function serviceRunning() {
  local id
  id=$(compose ps -q "$1" 2>/dev/null)
  [ -n "$id" ] && [ "$(docker inspect -f '{{.State.Running}}' "$id" 2>/dev/null)" = "true" ]
}

function waitReady() {
  local service=$1
  local ports=$2
  local port
  echo "Waiting for $service to accept connections on port ${ports// / or }..."
  for ((waited = 0; waited < NODE_READY_TIMEOUT; waited += 5)); do
    for port in $ports; do
      if compose exec -T "$service" bash -c ": </dev/tcp/127.0.0.1/$port" >/dev/null 2>&1; then
        echo "$service is ready."
        return 0
      fi
    done
    sleep 5
  done
  echo "ERROR: $service did not accept connections on port ${ports// / or } within ${NODE_READY_TIMEOUT}s." >&2
  echo "The database is already upgraded. Nodes the script has not replaced yet still run the previous version." >&2
  echo "Find the cause with: docker compose logs $service" >&2
  echo "If TBMQ listens for MQTT on other ports, list them in TBMQ_READY_PORTS, for example TBMQ_READY_PORTS=1884." >&2
  echo "After fixing it, replace the remaining nodes with: $0 --skip-db-upgrade" >&2
  return 1
}

function serviceReady() {
  local service=$1
  local port
  for port in $TBMQ_READY_PORTS; do
    if compose exec -T "$service" bash -c ": </dev/tcp/127.0.0.1/$port" >/dev/null 2>&1; then return 0; fi
  done
  return 1
}

rolling_supported=false
for version in $ROLLING_UPGRADE_VERSIONS; do
  if [ "$version" = "$TBMQ_VERSION" ]; then rolling_supported=true; fi
done

running_nodes=""
stopped_nodes=""
for node in $TBMQ_NODES; do
  if serviceRunning "$node"; then running_nodes="$running_nodes $node"; else stopped_nodes="$stopped_nodes $node"; fi
done

if [ -z "$MODE" ]; then
  if ! $rolling_supported; then
    echo "TBMQ ${TBMQ_VERSION} is not listed in ROLLING_UPGRADE_VERSIONS (${ROLLING_UPGRADE_VERSIONS}). Using --mode=stop."
    MODE=stop
  elif [ -z "$running_nodes" ]; then
    echo "No TBMQ node is running, so there are no clients to keep serving. Using --mode=stop."
    MODE=stop
  else
    MODE=rolling
  fi
fi

if [ "$MODE" = "rolling" ]; then
  if ! $rolling_supported; then
    echo "ERROR: a rolling upgrade to TBMQ ${TBMQ_VERSION} is not supported." >&2
    echo "ROLLING_UPGRADE_VERSIONS lists: ${ROLLING_UPGRADE_VERSIONS}. Run with --mode=stop." >&2
    exit 1
  fi
  if [ -z "$running_nodes" ]; then
    echo "ERROR: no TBMQ node is running, so a rolling upgrade has no clients to keep serving. Run with --mode=stop." >&2
    exit 1
  fi
  if [ -n "$stopped_nodes" ] && ! $SKIP_DB_UPGRADE; then
    echo "ERROR: a rolling upgrade needs every TBMQ node running, but these are not:${stopped_nodes}." >&2
    echo "Replacing the running nodes one at a time would leave no node serving clients." >&2
    echo "Start the stopped nodes on the current version first, or run with --mode=stop." >&2
    exit 1
  fi
fi

echo "Upgrading TBMQ to ${TBMQ_VERSION} (mode: $MODE)."

compose pull tbmq1 tbmq-integration-executor1

previous_image=""
if [ -n "$running_nodes" ]; then
  first_running=$(echo $running_nodes | cut -d' ' -f1)
  previous_image=$(docker inspect -f '{{.Config.Image}}' "$(compose ps -q "$first_running")" 2>/dev/null || true)
fi

if [ "$MODE" = "stop" ]; then
  compose stop $IE_NODES $TBMQ_NODES
  compose up -d $ADDITIONAL_STARTUP_SERVICES
else
  # Start the database and cache if they are down, but never restart them while the cluster is serving clients.
  compose up -d --no-recreate $ADDITIONAL_STARTUP_SERVICES
fi

if $SKIP_DB_UPGRADE; then
  echo "Skipping the database upgrade."
elif ! docker compose --env-file ./.env --env-file ./tbmq.env -f docker-compose.yml ${ADDITIONAL_CACHE_ARGS} \
    run --no-deps --rm -e UPGRADE_TB=true tbmq1; then
  echo "ERROR: the database upgrade failed. See the output above." >&2
  if [ "$MODE" = "stop" ]; then
    echo "The TBMQ nodes and integration executors are stopped." >&2
    if [ -n "$previous_image" ]; then
      echo "To run the previous version again, set TBMQ_VERSION back to ${previous_image##*:} in .env, then run ./scripts/docker-start-services.sh" >&2
    fi
  else
    echo "No TBMQ node was replaced; the cluster still runs the previous version." >&2
  fi
  exit 1
fi

if [ "$MODE" = "stop" ]; then
  compose up -d
else
  # Replace nodes that are not ready first (after a failed run), so a node that serves clients is replaced only
  # while another node is ready to take them over.
  order=""
  for node in $TBMQ_NODES; do
    if ! serviceReady "$node"; then order="$order $node"; fi
  done
  for node in $TBMQ_NODES; do
    case " $order " in *" $node "*) ;; *) order="$order $node" ;; esac
  done
  last_node=${order##* }
  for node in $order; do
    echo "Replacing $node..."
    compose up -d --no-deps "$node"
    waitReady "$node" "$TBMQ_READY_PORTS"
    if [ "$node" != "$last_node" ]; then
      echo "Waiting ${NODE_SETTLE_SECONDS}s for HAProxy to route clients to $node again..."
      sleep "$NODE_SETTLE_SECONDS"
    fi
  done
  for node in $IE_NODES; do
    echo "Replacing $node..."
    compose up -d --no-deps "$node"
    waitReady "$node" "$IE_READY_PORTS"
  done
fi

echo "TBMQ is upgraded to ${TBMQ_VERSION}."
