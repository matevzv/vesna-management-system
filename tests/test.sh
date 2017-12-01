#!/usr/bin/env bash

cd /root/vesna-drivers/Applications/Logatec/NodeSpectrumSensorLocal
make node.load

cd /root/tests
python test.py
