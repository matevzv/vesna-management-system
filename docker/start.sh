#!/bin/bash

# Compile vesna drivers
if [ "$VESNA" = "true" ]; then
  cd /root/vesna-drivers/Applications/Logatec/NodeSpectrumSensorLocal
  make node.loadbone
else
  echo "Not compiling VESNA drivers!"
fi

# Set serial device
if [ -z "$SERIAL" ]; then
  echo "Serial device missing!"
else
  cd /root/vesna-management-system
  sed -i "s|10000|"$SERIAL"|g" webapp/vms/vms.html
  java -jar build/VesnaManagementSystem.jar -l false -p "$SERIAL"
fi
