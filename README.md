# Vesna Management System

test

Run install.sh/uninstall.sh script as root

Requires Java

Supports debian and redhat based sistems

When installed the VESNA management platform will be accessible
through you browser on: http://localhost:9000

The VESNA gateway or emulator (vesna-emulator folder)
will connect to port 10000 located on localhost
over secure SSL connection

The VESNA management system daemon name is vmsd. You can
use standard service commands to interact with the daemon i.e.:

service vmsd start|stop|restart|reload|status

## Docker container

To build the container, use the following command.

    $ docker build -t vms .

Run in Docker container. Env VESNA=true will compile and flash VESNA firmware.

    $ docker run -it -p 9000:9000 --privileged \
      -e VESNA=true \
      -e SERIAL=/dev/ttyS1 \
      vms
