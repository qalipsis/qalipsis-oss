#!/bin/sh

cd ..
./gradlew displayVersion clean :runtime:classes :runtime:runQalipsisHead --rerun-tasks --no-build-cache $*