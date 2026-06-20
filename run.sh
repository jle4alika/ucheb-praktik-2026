#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

if [[ ! -f lib/sqlite-jdbc.jar ]]; then
  mkdir -p lib
  curl -sL -o lib/sqlite-jdbc.jar \
    "https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.46.1.3/sqlite-jdbc-3.46.1.3.jar"
fi

mkdir -p out
find src/main/java -name "*.java" > sources.txt
javac -encoding UTF-8 -d out -cp lib/sqlite-jdbc.jar @sources.txt
rm -f sources.txt

exec java --enable-native-access=ALL-UNNAMED -cp "out:lib/sqlite-jdbc.jar" folderindexer.Main "$@"
