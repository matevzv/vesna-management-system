FROM armv7/armhf-ubuntu:xenial

# File Author / Maintainer
MAINTAINER Matevz Vucnik

RUN apt-get update
RUN apt-get install -y default-jre
RUN apt-get install -y librxtx-java

ADD . /root/vms

WORKDIR /root/vms

# Run app
CMD java -jar build/VesnaManagementSystem.jar -l false -p /dev/ttyS1