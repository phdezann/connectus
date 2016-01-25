#!/bin/bash
set -e
docker build -t connectus-build -f Dockerfile.build .
docker build -t connectus-build-cache -f Dockerfile.build.cache .
docker create --name connectus-build-cache connectus-build-cache /bin/bash || echo "Data container for build caches already exists, keep it for faster build executions."
docker run -it --rm --volumes-from connectus-build-cache -v $(pwd):/workspace -w /workspace/backend connectus-build sbt stage
docker run -it --rm --volumes-from connectus-build-cache -v $(pwd):/workspace -w /workspace/android-app connectus-build ./gradlew assembleDebug
