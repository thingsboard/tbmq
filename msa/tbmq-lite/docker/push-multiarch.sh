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

# Builds the image from the current directory for linux/amd64 and linux/arm64 and pushes it to the registry as one
# multi-arch manifest, tagged with the version only (never latest).
# DRY_RUN=1 prints the command instead of running it, so that nothing is built or pushed. Unset or empty DRY_RUN pushes
# for real; any other value is refused, so that a mistyped DRY_RUN=true cannot turn into a push.
usage() {
  echo "Usage: [DRY_RUN=1] $0 <repository/name> <version>" >&2
  echo "  e.g. $0 thingsboard/tbmq-lite 2.5.0-beta" >&2
  exit 1
}

[ "$#" -eq 2 ] && [ -n "$1" ] && [ -n "$2" ] || usage
case "$1$2" in
  *[[:space:]]*) usage ;;
esac

case "${DRY_RUN:-}" in
  "" | 1) ;;
  *)
    echo "Error: DRY_RUN must be unset, empty or 1, got '$DRY_RUN'; nothing was run" >&2
    exit 1
    ;;
esac

image=$1
version=$2

args=(buildx build --platform=linux/amd64,linux/arm64 --push -t "$image:$version" .)

if [ "${DRY_RUN:-}" = "1" ]; then
  echo "docker ${args[*]}"
  exit 0
fi

exec docker "${args[@]}"
