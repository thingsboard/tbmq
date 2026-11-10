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

# Upgrades the TBMQ cluster to the TBMQ_VERSION set in .env, and starts it on the new version.
#
# Usage: ./scripts/docker-upgrade-tbmq.sh
#
# The script stops the TBMQ nodes and integration executors, upgrades the database, then starts the cluster.
# Clients are disconnected for the duration of the upgrade and reconnect once the nodes are up again.

TBMQ_NODES="tbmq1 tbmq2"
IE_NODES="tbmq-integration-executor1 tbmq-integration-executor2"

for i in "$@"; do
  echo "Unknown option: $i" >&2
  echo "Usage: $0" >&2
  exit 1
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

# The version the cluster runs now, used to explain how to go back if the database upgrade fails.
previous_image=$(docker inspect -f '{{.Config.Image}}' "$(compose ps -a -q tbmq1)" 2>/dev/null || true)

echo "Upgrading TBMQ to ${TBMQ_VERSION}."

compose pull tbmq1 tbmq-integration-executor1

compose stop $IE_NODES $TBMQ_NODES
compose up -d $ADDITIONAL_STARTUP_SERVICES

db_log=$(mktemp)
set +e
docker compose --env-file ./.env --env-file ./tbmq.env -f docker-compose.yml ${ADDITIONAL_CACHE_ARGS} \
  run -T --no-deps --rm -e UPGRADE_TB=true tbmq1 2>&1 | tee "$db_log"
db_status=${PIPESTATUS[0]}
set -e
already_upgraded=false
if grep -q "database already upgraded to current version" "$db_log"; then already_upgraded=true; fi
rm -f "$db_log"

if [ "$db_status" -ne 0 ]; then
  echo "ERROR: the database upgrade failed. See the output above." >&2
  echo "The TBMQ nodes and integration executors are stopped." >&2
  if $already_upgraded; then
    echo "The database is already on TBMQ ${TBMQ_VERSION}. Start the cluster with: ./scripts/docker-start-services.sh" >&2
  elif [ -n "$previous_image" ] && [ "${previous_image##*:}" != "${TBMQ_VERSION}" ]; then
    echo "To run the previous version again, set TBMQ_VERSION back to ${previous_image##*:} in .env," >&2
    echo "then run: ./scripts/docker-start-services.sh" >&2
  fi
  exit 1
fi

compose up -d

echo "TBMQ is upgraded to ${TBMQ_VERSION}."
