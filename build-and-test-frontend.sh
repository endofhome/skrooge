#!/bin/bash

set -e
set -o errexit
set -o pipefail
set -o nounset

command -v npm >/dev/null 2>&1 || { echo >&2 "NPM is required - please install it and try again."; exit 1; }
npm i
npm test
./node_modules/.bin/webpack
