#!/bin/sh

cd ${1}
echo "[INFO] Deploying workflow: ${2}"
echo "[INFO] Deploying environment: ${3}"
./controller --workflow ${2} --environment ${3}
