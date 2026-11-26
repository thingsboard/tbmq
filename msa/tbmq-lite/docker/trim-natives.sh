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

# Usage: trim-natives.sh <extracted app dir> <dpkg architecture: amd64|arm64> [native dir]
#
# Leaves only the native code the image runs on:
#  - rocksdbjni keeps its classes; the library of the target architecture moves to the native directory, from which
#    System.loadLibrary finds it through java.library.path (nothing is extracted from the jar at startup);
#  - Netty's kqueue, macOS DNS resolver and epoll libraries are dropped (TBMQ runs on the NIO transport);
#  - JNA keeps the library of the target architecture only.
# Fails if the expected rocksdb or JNA library is missing, or if any other native library is left in a jar.
set -euo pipefail

APP_DIR=${1:?usage: trim-natives.sh <app dir> <amd64|arm64> [native dir]}
ARCH=${2:?usage: trim-natives.sh <app dir> <amd64|arm64> [native dir]}
NATIVE_DIR=${3:-/usr/share/tbmq-lite/native}
LIB_DIR="$APP_DIR/lib"

case "$ARCH" in
  amd64) ROCKSDB_LIB=librocksdbjni-linux64.so; JNA_DIR=linux-x86-64 ;;
  arm64) ROCKSDB_LIB=librocksdbjni-linux-aarch64.so; JNA_DIR=linux-aarch64 ;;
  *) echo "trim-natives: unsupported architecture '$ARCH'" >&2; exit 1 ;;
esac

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

# unpack <jar>: extracts the jar to $WORK/jar and sets MANIFEST to its manifest
unpack() {
  rm -rf "$WORK/jar"
  mkdir "$WORK/jar"
  (cd "$WORK/jar" && jar --extract --file "$1")
  MANIFEST="$WORK/MANIFEST.MF"
  cp "$WORK/jar/META-INF/MANIFEST.MF" "$MANIFEST"
  rm "$WORK/jar/META-INF/MANIFEST.MF"
}

# repack <jar>: rebuilds the jar from $WORK/jar without the directories that became empty
repack() {
  find "$WORK/jar" -mindepth 1 -type d -empty -delete
  rm "$1"
  jar --create --file "$1" --manifest "$MANIFEST" -C "$WORK/jar" .
}

# rocksdbjni
mkdir -p "$NATIVE_DIR"
ROCKSDB_JARS=("$LIB_DIR"/rocksdbjni-*.jar)
[ ${#ROCKSDB_JARS[@]} -eq 1 ] && [ -f "${ROCKSDB_JARS[0]}" ] || { echo "trim-natives: expected exactly one rocksdbjni jar in $LIB_DIR" >&2; exit 1; }
unpack "${ROCKSDB_JARS[0]}"
[ -f "$WORK/jar/$ROCKSDB_LIB" ] || { echo "trim-natives: $ROCKSDB_LIB not found in ${ROCKSDB_JARS[0]}" >&2; exit 1; }
mv "$WORK/jar/$ROCKSDB_LIB" "$NATIVE_DIR/$ROCKSDB_LIB"
find "$WORK/jar" -maxdepth 1 -type f \( -name 'librocksdbjni-*' -o -name '*.so' -o -name '*.dll' -o -name '*.jnilib' -o -name '*.dylib' \) -delete
repack "${ROCKSDB_JARS[0]}"
[ -s "$NATIVE_DIR/$ROCKSDB_LIB" ] || { echo "trim-natives: $NATIVE_DIR/$ROCKSDB_LIB is missing or empty" >&2; exit 1; }

# Netty: the jars that hold only native libraries (Class-Path entries of missing jars are ignored by the JVM)
rm -f "$LIB_DIR"/netty-transport-native-kqueue-*-osx-*.jar \
      "$LIB_DIR"/netty-resolver-dns-native-macos-*-osx-*.jar \
      "$LIB_DIR"/netty-transport-native-epoll-*-linux-*.jar

# JNA
JNA_JARS=("$LIB_DIR"/jna-[0-9]*.jar)
[ ${#JNA_JARS[@]} -eq 1 ] && [ -f "${JNA_JARS[0]}" ] || { echo "trim-natives: expected exactly one jna jar in $LIB_DIR" >&2; exit 1; }
unpack "${JNA_JARS[0]}"
[ -n "$(find "$WORK/jar/com/sun/jna/$JNA_DIR" -name '*jnidispatch*' 2>/dev/null)" ] || { echo "trim-natives: no JNA library for $JNA_DIR in ${JNA_JARS[0]}" >&2; exit 1; }
for dir in "$WORK/jar/com/sun/jna"/*/; do
  name=$(basename "$dir")
  if [ "$name" != "$JNA_DIR" ] && [ -n "$(find "$dir" -maxdepth 1 -name '*jnidispatch*')" ]; then
    rm -r "$dir"
  fi
done
repack "${JNA_JARS[0]}"

# nothing else may carry native code
for jar in $(grep -laE '\.(so|dll|dylib|jnilib)(\.[0-9]+)*|jnidispatch' "$LIB_DIR"/*.jar || true); do
  left=$(jar --list --file "$jar" | grep -E '\.(so|dll|dylib|jnilib)(\.[0-9]+)*$' | grep -v "^com/sun/jna/$JNA_DIR/" || true)
  if [ -n "$left" ]; then
    echo "trim-natives: native code left in $jar: $left" >&2
    exit 1
  fi
done

echo "trim-natives: $ARCH: $ROCKSDB_LIB -> $NATIVE_DIR, JNA $JNA_DIR kept, kqueue/macOS DNS/epoll jars removed"
