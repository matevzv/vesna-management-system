# Vesna Management System

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

Note: building the container requires cloning of the private `vesna-drivers`
GitHub repository. Because of this, you need to pass a GitHub access token to
the build process (the `ghtoken=` parameter below). You can generate a token at
your [GitHub settings page](https://github.com/settings/tokens). Select the
*Full control of private repositories* permission.

**Do not push the resulting container to Docker Hub or any other public
repository. It's best to revoke the access token immediately after building the
container.**

    $ docker build -t vms --build-arg=ghtoken=... .

Run in Docker container. Env VESNA=true will compile and flash VESNA firmware.

    $ docker run -it -p 9000:9000 -v /sys/class/gpio:/sys/class/gpio \
      --device=/dev/ttyS1 --privileged \
      -e VESNA=true \
      -e SERIAL=/dev/ttyS1 \
      vms
