FROM armhf/debian:jessie

# File Author / Maintainer
MAINTAINER Matevz Vucnik

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update --fix-missing
RUN apt-get upgrade -y
RUN apt-get install -y apt-utils
RUN apt-get install -y git
RUN apt-get install -y default-jdk
RUN apt-get install -y ant
RUN apt-get install -y librxtx-java

ADD . /root/vms
WORKDIR /root/vms
RUN ant build
RUN sed -i 's|10000|/dev/ttyS1|g' webapp/vms/vms.html

# Run app
CMD java -jar build/VesnaManagementSystem.jar -l false -p /dev/ttyS1
