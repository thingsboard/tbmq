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

# Starts TBMQ Lite with JVM flags chosen from the container's limits, read the way the JVM reads them: in the process's
# own cgroup (from /proc/self/cgroup, or the mount root when that path is not visible under the mount) and every parent
# up to the mount root, taking the smallest limit. Memory from memory.max (v1 memory.limit_in_bytes), falling back to
# MemTotal when unlimited or when the limit is not below MemTotal (the JVM ignores such a limit too); CPUs as the smaller
# of the cpuset size and ceil(cpu quota / period), falling back to nproc.
# A collector in JAVA_OPTS, JDK_JAVA_OPTIONS, JAVA_TOOL_OPTIONS or _JAVA_OPTIONS suppresses the script's choice of
# collector.
#
#   start-tbmq-lite.sh                 logs one line with what it read and chose, then execs java
#   start-tbmq-lite.sh --print-flags   prints that line, the flags (one per line), LD_PRELOAD=<value> and
#                                      MALLOC_ARENA_MAX=<value>, and exits
#
# Overrides, for tests: TBMQ_CGROUP_ROOT, TBMQ_PROC_SELF_CGROUP, TBMQ_MEMINFO, TBMQ_JEMALLOC, TBMQ_CONFIG_DIR.
set -eu

CGROUP_ROOT=${TBMQ_CGROUP_ROOT:-/sys/fs/cgroup}
CGROUP_ROOT=${CGROUP_ROOT%/}
PROC_SELF_CGROUP=${TBMQ_PROC_SELF_CGROUP:-/proc/self/cgroup}
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

# the process's cgroup path from /proc/self/cgroup: the 0:: line for v2 (no argument), the line listing the
# controller for v1; empty when not found
own_cgroup_path() {
  local want=${1:-} id controllers path
  [ -r "$PROC_SELF_CGROUP" ] || return 0
  while IFS=: read -r id controllers path; do
    if [ -z "$want" ]; then
      if [ "$id" = 0 ] && [ -z "$controllers" ]; then
        printf '%s' "$path"
        return 0
      fi
    else
      case ",$controllers," in
        *",$want,"*)
          printf '%s' "$path"
          return 0
          ;;
      esac
    fi
  done < "$PROC_SELF_CGROUP"
}

# the directory of a cgroup path under a mount, or the mount itself when the path is not visible there (a private
# cgroup namespace shows /, Docker on cgroup v1 mounts the container's own cgroup as the root)
cgroup_dir() {
  local mount=$1 path=$2
  if [ -n "$path" ] && [ "$path" != / ] && [[ $path != *..* ]] && [ -d "$mount$path" ]; then
    printf '%s' "$mount${path%/}"
  else
    printf '%s' "$mount"
  fi
}

# a memory limit in bytes, or empty: "max", a missing file and v1's near-2^63 "no limit" value (19 digits or more)
# all mean unlimited
memory_limit_in() {
  local value
  value=$(first_line "$1")
  if is_number "$value" && [ ${#value} -lt 19 ] && [ "$value" -gt 0 ]; then
    printf '%s' "$value"
  fi
}

# ceil(quota / period), or empty when there is no quota
quota_cpus_of() {
  local quota=$1 period=$2
  if is_number "$quota" && is_number "$period" && [ "$quota" -gt 0 ] && [ "$period" -gt 0 ]; then
    printf '%s' $(((quota + period - 1) / period))
  fi
}

# the smaller of two numbers, either of which may be empty
min_of() {
  if [ -z "$1" ] || { [ -n "$2" ] && [ "$2" -lt "$1" ]; }; then
    printf '%s' "$2"
  else
    printf '%s' "$1"
  fi
}

# parent directory, stopping at the mount
parent_dir() {
  local mount=$1 dir=$2
  if [ ${#dir} -le ${#mount} ]; then
    return 1
  fi
  dir=${dir%/*}
  if [ ${#dir} -lt ${#mount} ]; then
    dir=$mount
  fi
  printf '%s' "$dir"
}

mem_limit="" quota_cpus=""
if [ -e "$CGROUP_ROOT/cgroup.controllers" ] || [ -e "$CGROUP_ROOT/memory.max" ] || [ -e "$CGROUP_ROOT/cpu.max" ]; then
  CGROUP_VERSION=2
  dir=$(cgroup_dir "$CGROUP_ROOT" "$(own_cgroup_path)")
  cpuset=$(first_line "$dir/cpuset.cpus.effective")
  while :; do
    mem_limit=$(min_of "$mem_limit" "$(memory_limit_in "$dir/memory.max")")
    cpu_quota="" cpu_period=""
    read -r cpu_quota cpu_period <<< "$(first_line "$dir/cpu.max")" || true
    quota_cpus=$(min_of "$quota_cpus" "$(quota_cpus_of "$cpu_quota" "$cpu_period")")
    dir=$(parent_dir "$CGROUP_ROOT" "$dir") || break
  done
else
  CGROUP_VERSION=1
  dir=$(cgroup_dir "$CGROUP_ROOT/memory" "$(own_cgroup_path memory)")
  while :; do
    mem_limit=$(min_of "$mem_limit" "$(memory_limit_in "$dir/memory.limit_in_bytes")")
    dir=$(parent_dir "$CGROUP_ROOT/memory" "$dir") || break
  done
  dir=$(cgroup_dir "$CGROUP_ROOT/cpu" "$(own_cgroup_path cpu)")
  while :; do
    quota_cpus=$(min_of "$quota_cpus" \
      "$(quota_cpus_of "$(first_line "$dir/cpu.cfs_quota_us")" "$(first_line "$dir/cpu.cfs_period_us")")")
    dir=$(parent_dir "$CGROUP_ROOT/cpu" "$dir") || break
  done
  cpuset=$(first_line "$(cgroup_dir "$CGROUP_ROOT/cpuset" "$(own_cgroup_path cpuset)")/cpuset.effective_cpus")
fi

# memory: the limit, unless there is none or it is not below the host's memory
host_mib=$(memtotal_mib)
memory_mib=$host_mib
if [ -n "$mem_limit" ] && { [ "$host_mib" -le 0 ] || [ $((mem_limit / MIB)) -lt "$host_mib" ]; }; then
  memory_mib=$((mem_limit / MIB))
fi

# CPUs: the cpuset (nproc when unknown), capped by the quota when there is one
cpus=0
if [ -n "$cpuset" ]; then
  cpus=$(cpuset_size "$cpuset")
fi
if [ "$cpus" -lt 1 ]; then
  cpus=$(nproc 2>/dev/null || echo 1)
fi
if [ -n "$quota_cpus" ] && [ "$quota_cpus" -lt "$cpus" ]; then
  cpus=$quota_cpus
fi

direct_mib=$((memory_mib / 10))
if [ "$direct_mib" -lt "$MIN_DIRECT_MIB" ]; then
  direct_mib=$MIN_DIRECT_MIB
fi

# user flags, split into words on any whitespace including newlines (no globbing). read -d '' returns non-zero at the
# end of input, which is expected here.
user_opts=()
if [ -n "${JAVA_OPTS:-}" ]; then
  read -r -d '' -a user_opts <<< "$JAVA_OPTS" || true
fi
# a collector named in any variable the JVM reads options from suppresses the script's choice: with two collectors
# the JVM refuses to start (JAVA_OPTS is passed by this script, JDK_JAVA_OPTIONS, JAVA_TOOL_OPTIONS and _JAVA_OPTIONS
# are read by the JVM)
collector_source=""
for var in JAVA_OPTS JDK_JAVA_OPTIONS JAVA_TOOL_OPTIONS _JAVA_OPTIONS; do
  words=()
  if [ -n "${!var:-}" ]; then
    read -r -d '' -a words <<< "${!var}" || true
  fi
  for word in ${words[@]+"${words[@]}"}; do
    if [ -z "$collector_source" ] && [[ $word == -XX:+Use*GC ]]; then
      collector_source=$var
    fi
  done
done
collector_flag=""
collector_name="from $collector_source"
if [ -z "$collector_source" ]; then
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
