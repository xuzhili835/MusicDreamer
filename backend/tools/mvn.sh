#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")" && pwd)"
export JAVA_HOME="$HOME/.jdks/openjdk-22.0.2"
exec "$DIR/apache-maven-3.9.16/bin/mvn" -s "$DIR/settings.xml" "$@"
