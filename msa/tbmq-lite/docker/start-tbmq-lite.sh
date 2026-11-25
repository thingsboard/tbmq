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

# Starts TBMQ Lite with JVM flags chosen from the container's limits, read the way the JVM reads them:
# memory from cgroup memory.max (v1 memory.limit_in_bytes), falling back to MemTotal when unlimited; CPUs as the
# smaller of the cpuset size and ceil(cpu quota / period), falling back to nproc.
#
#   start-tbmq-lite.sh                 logs one line with what it read and chose, then execs java
#   start-tbmq-lite.sh --print-flags   prints that line, the flags (one per line), LD_PRELOAD=<value> and
#                                      MALLOC_ARENA_MAX=<value>, and exits
#
# Overrides, for tests: TBMQ_CGROUP_ROOT, TBMQ_MEMINFO, TBMQ_JEMALLOC, TBMQ_CONFIG_DIR.
set -eu

CGROUP_ROOT=${TBMQ_CGROUP_ROOT:-/sys/fs/cgroup}
MEMINFO=${TBMQ_MEMINFO:-/proc/meminfo}
CONFIG_DIR=${TBMQ_CONFIG_DIR:-/config}
BUNDLED_LOGBACK=/usr/share/tbmq-lite/conf/logback.xml
NATIVE_DIR=/usr/share/tbmq-lite/native
APP_JAR=/usr/share/tbmq-lite/app/tbmq-lite.jar

MIB=1048576
MIN_DIRECT_MIB=64
SERIAL_BELOW_MIB=2048

# first line of a file, or empty when it cannot be read
first_line() {
  local line=""
  if [ -r "$1" ]; then
    IFS= read -r line < "$1" || true
  fi
  printf '%s' "$line"
}

is_number() {
  [[ $1 =~ ^[0-9]+$ ]]
}

# number of CPUs in a cpuset list such as 0-1,4
cpuset_size() {
  local list=$1 count=0 part
  local IFS=,
  for part in $list; do
    if [[ $part =~ ^([0-9]+)-([0-9]+)$ ]]; then
      count=$((count + BASH_REMATCH[2] - BASH_REMATCH[1] + 1))
    elif [[ $part =~ ^[0-9]+$ ]]; then
      count=$((count + 1))
    fi
  done
  echo "$count"
}

memtotal_mib() {
  local key value unit
  while read -r key value unit; do
    if [ "$key" = "MemTotal:" ] && is_number "$value"; then
      echo $((value / 1024))
      return
    fi
  done < "$MEMINFO"
  echo 0
}

# cgroup version: 2 when the unified hierarchy is mounted, else 1
if [ -e "$CGROUP_ROOT/cgroup.controllers" ] || [ -e "$CGROUP_ROOT/memory.max" ] || [ -e "$CGROUP_ROOT/cpu.max" ]; then
  CGROUP_VERSION=2
  mem_limit=$(first_line "$CGROUP_ROOT/memory.max")
  cpu_quota="" cpu_period=""
  read -r cpu_quota cpu_period <<< "$(first_line "$CGROUP_ROOT/cpu.max")" || true
  cpuset=$(first_line "$CGROUP_ROOT/cpuset.cpus.effective")
else
  CGROUP_VERSION=1
  mem_limit=$(first_line "$CGROUP_ROOT/memory/memory.limit_in_bytes")
  cpu_quota=$(first_line "$CGROUP_ROOT/cpu/cpu.cfs_quota_us")
  cpu_period=$(first_line "$CGROUP_ROOT/cpu/cpu.cfs_period_us")
  cpuset=$(first_line "$CGROUP_ROOT/cpuset/cpuset.effective_cpus")
fi

# memory: "max", a missing file and v1's near-2^63 "no limit" value (19 digits or more) all mean unlimited
if is_number "$mem_limit" && [ ${#mem_limit} -lt 19 ] && [ "$mem_limit" -gt 0 ]; then
  memory_mib=$((mem_limit / MIB))
else
  memory_mib=$(memtotal_mib)
fi

# CPUs: the cpuset (nproc when unknown), capped by the quota when there is one
cpus=0
if [ -n "$cpuset" ]; then
  cpus=$(cpuset_size "$cpuset")
fi
if [ "$cpus" -lt 1 ]; then
  cpus=$(nproc 2>/dev/null || echo 1)
fi
if is_number "$cpu_quota" && is_number "$cpu_period" && [ "$cpu_quota" -gt 0 ] && [ "$cpu_period" -gt 0 ]; then
  quota_cpus=$(((cpu_quota + cpu_period - 1) / cpu_period))
  if [ "$quota_cpus" -lt "$cpus" ]; then
    cpus=$quota_cpus
  fi
fi

direct_mib=$((memory_mib / 10))
if [ "$direct_mib" -lt "$MIN_DIRECT_MIB" ]; then
  direct_mib=$MIN_DIRECT_MIB
fi

# user flags, split into words on any whitespace including newlines (no globbing); a collector named in them
# suppresses the script's choice. read -d '' returns non-zero at the end of input, which is expected here.
user_opts=()
if [ -n "${JAVA_OPTS:-}" ]; then
  read -r -d '' -a user_opts <<< "$JAVA_OPTS" || true
fi
user_collector=false
for word in ${user_opts[@]+"${user_opts[@]}"}; do
  if [[ $word == -XX:+Use*GC ]]; then
    user_collector=true
  fi
done
collector_flag=""
collector_name="from JAVA_OPTS"
if [ "$user_collector" = false ]; then
  if [ "$cpus" -lt 2 ] || [ "$memory_mib" -lt "$SERIAL_BELOW_MIB" ]; then
    collector_flag=-XX:+UseSerialGC
    collector_name=Serial
  else
    collector_flag=-XX:+UseG1GC
    collector_name=G1
  fi
fi

logging_config=$BUNDLED_LOGBACK
if [ -f "$CONFIG_DIR/logback.xml" ]; then
  logging_config=$CONFIG_DIR/logback.xml
fi

# jemalloc, only if it loads: ld.so ignores a preload it cannot load, with exit status 0 and a message on stderr
case "$(uname -m)" in
  x86_64 | amd64) triplet=x86_64-linux-gnu ;;
  aarch64 | arm64) triplet=aarch64-linux-gnu ;;
  *) triplet=$(uname -m)-linux-gnu ;;
esac
jemalloc=${TBMQ_JEMALLOC:-/usr/lib/$triplet/libjemalloc.so.2}
ld_preload=""
malloc_arena_max=""
warning=""
if [ -r "$jemalloc" ] && preload_stderr=$(LD_PRELOAD="$jemalloc" /bin/true 2>&1) && [ -z "$preload_stderr" ]; then
  ld_preload=$jemalloc
  jemalloc_state=on
else
  malloc_arena_max=2
  jemalloc_state=off
  warning="WARN TBMQ Lite: jemalloc ($jemalloc) is missing or does not load on this system, running with the default allocator and MALLOC_ARENA_MAX=2"
fi

flags=()
if [ -n "$collector_flag" ]; then
  flags+=("$collector_flag")
fi
flags+=(
  -XX:MaxRAMPercentage=45
  "-XX:MaxDirectMemorySize=${direct_mib}m"
  -XX:+ExitOnOutOfMemoryError
  "-Djava.library.path=$NATIVE_DIR"
  "-Dlogging.config=$logging_config"
  --enable-native-access=ALL-UNNAMED
)
if [ ${#user_opts[@]} -gt 0 ]; then
  flags+=("${user_opts[@]}")
fi

echo "TBMQ Lite: memory $memory_mib MiB, CPUs $cpus (cgroup v$CGROUP_VERSION), collector $collector_name, max direct memory $direct_mib MiB, jemalloc $jemalloc_state"
if [ -n "$warning" ]; then
  echo "$warning" >&2
fi

if [ "${1:-}" = "--print-flags" ]; then
  printf '%s\n' "${flags[@]}"
  echo "LD_PRELOAD=$ld_preload"
  echo "MALLOC_ARENA_MAX=$malloc_arena_max"
  exit 0
fi

if [ -n "$ld_preload" ]; then
  export LD_PRELOAD=$ld_preload
fi
if [ -n "$malloc_arena_max" ]; then
  export MALLOC_ARENA_MAX=$malloc_arena_max
fi
exec java "${flags[@]}" -jar "$APP_JAR"
