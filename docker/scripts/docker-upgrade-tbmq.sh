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

set -e

source scripts/compose-utils.sh

COMPOSE_VERSION=$(composeVersion) || exit $?

ADDITIONAL_CACHE_ARGS=$(additionalComposeCacheArgs) || exit $?

ADDITIONAL_STARTUP_SERVICES=$(additionalStartupServices) || exit $?

checkVolumes

COMPOSE_ARGS_PULL="\
      --env-file ./.env \
      -f docker-compose.yml ${ADDITIONAL_CACHE_ARGS} \
      pull \
      tbmq1"

COMPOSE_ARGS_UP="\
      --env-file ./.env \
      -f docker-compose.yml ${ADDITIONAL_CACHE_ARGS} \
      up -d ${ADDITIONAL_STARTUP_SERVICES}"

COMPOSE_ARGS_RUN="\
      --env-file ./.env --env-file ./tbmq.env \
      -f docker-compose.yml ${ADDITIONAL_CACHE_ARGS} \
      run --no-deps --rm -e UPGRADE_TB=true \
      tbmq1"

case $COMPOSE_VERSION in
    V2)
        docker compose $COMPOSE_ARGS_PULL
        docker compose $COMPOSE_ARGS_UP
        docker compose $COMPOSE_ARGS_RUN
    ;;
    V1)
        docker-compose $COMPOSE_ARGS_PULL
        docker-compose $COMPOSE_ARGS_UP
        docker-compose $COMPOSE_ARGS_RUN
    ;;
    *)
        # unknown option
    ;;
esac
