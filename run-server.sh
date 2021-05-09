#!/bin/sh
set -e
if ! test -f target/nioserver-*-jar-with-dependencies.jar; then
    echo Building..
    mvn -q -DskipTests package
fi
exec java -jar target/nioserver-*-jar-with-dependencies.jar "$@"
