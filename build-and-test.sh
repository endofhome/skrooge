#!/bin/bash

set -e
set -o errexit
set -o pipefail
set -o nounset

./build-and-test-frontend.sh
./gradlew clean test
