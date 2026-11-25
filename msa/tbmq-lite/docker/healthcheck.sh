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

# Passes only when the broker answers /actuator/health on 127.0.0.1:8083 with a body that starts with {"status":"UP".
# Uses bash's /dev/tcp (the image has no curl), HTTP/1.0 so that the server closes the connection, and a read timeout
# so that a hung broker fails the check instead of hanging it.
exec 3<>/dev/tcp/127.0.0.1/8083 || exit 1
printf 'GET /actuator/health HTTP/1.0\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n' >&3 || exit 1
IFS= read -r -t 5 -d '' response <&3
body=${response#*$'\r\n\r\n'}
[[ $response == HTTP/1.?\ 200* && $body == '{"status":"UP"'* ]]
