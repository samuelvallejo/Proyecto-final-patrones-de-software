#!/usr/bin/env sh
set -eu
TASK_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TASK_MAVEN="$TASK_ROOT/.tools/apache-maven-3.9.9"
if [ ! -x "$TASK_MAVEN/bin/mvn" ]; then
 mkdir -p "$TASK_ROOT/.tools"
 TASK_ARCHIVE="$TASK_ROOT/.tools/maven.tar.gz"
 TASK_URL='https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.tar.gz'
 curl -fsSL "$TASK_URL" -o "$TASK_ARCHIVE"
 TASK_HASH=$(curl -fsSL "$TASK_URL.sha512")
 printf '%s  %s\n' "$TASK_HASH" "$TASK_ARCHIVE" | sha512sum -c -
 tar -xzf "$TASK_ARCHIVE" -C "$TASK_ROOT/.tools"
fi
exec "$TASK_MAVEN/bin/mvn" "$@"
