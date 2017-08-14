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

Run in docker container:

  `docker build -t vms .`
  `docker run -p 9000:9000 -it --device=/dev/ttyS1 vms`
