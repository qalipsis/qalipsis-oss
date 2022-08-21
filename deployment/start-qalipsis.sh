#!/bin/sh

cd ..
./gradlew displayVersion clean :runtime:classes :runtime:runQalipsis --rerun-tasks --no-build-cache $*