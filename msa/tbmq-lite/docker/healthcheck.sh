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

# Passes only when the broker answers /actuator/health on ${HTTP_BIND_PORT:-8083} with a body that starts with
# {"status":"UP". It connects to HTTP_BIND_ADDRESS when that names one address, else to 127.0.0.1, and needs the default
# actuator base path. With SSL_ENABLED=true (TLS on the HTTP port) it can only check that the port accepts a connection.
# Uses bash's /dev/tcp (the image has no curl), HTTP/1.0 so that the server closes the connection, and a time limit on
# the whole exchange (connect included) so that a hung broker fails the check instead of hanging it.
if [ -z "${TBMQ_HEALTHCHECK_BOUNDED:-}" ]; then
  TBMQ_HEALTHCHECK_BOUNDED=1 exec timeout 8 bash "$0"
fi
address=${HTTP_BIND_ADDRESS:-}
case "$address" in
  "" | 0.0.0.0 | :: | "[::]") address=127.0.0.1 ;;
esac
address=${address#[}
address=${address%]}
exec 3<>"/dev/tcp/$address/${HTTP_BIND_PORT:-8083}" || exit 1
ssl_enabled=${SSL_ENABLED:-false}
if [ "${ssl_enabled,,}" = true ]; then
  exit 0
fi
printf 'GET /actuator/health HTTP/1.0\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3 || exit 1
IFS= read -r -t 5 -d '' response <&3
body=${response#*$'\r\n\r\n'}
[[ $response == HTTP/1.?\ 200* && $body == '{"status":"UP"'* ]]
