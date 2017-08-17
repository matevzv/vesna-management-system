FROM arm32v7/debian:jessie

# File Author / Maintainer
MAINTAINER Matevz Vucnik

# Set debian frontend
ENV DEBIAN_FRONTEND=noninteractive

# Install common dependences
RUN apt-get update --fix-missing
RUN apt-get upgrade -y
RUN apt-get install -y apt-utils
RUN apt-get install -y git

# Install dependences for vena drivers
RUN apt-get install -y gcc-arm-none-eabi
RUN apt-get install -y gdb-arm-none-eabi
RUN apt-get install -y libnewlib-arm-none-eabi

# Install dependences for vesna management system
RUN apt-get install -y default-jdk
RUN apt-get install -y ant
RUN apt-get install -y librxtx-java
RUN apt-get clean

# Compile and install OpenOCD
RUN cd /root &&\
git clone -b bbblack https://github.com/avian2/openocd.git
WORKDIR /root/openocd
RUN dpkg-buildpackage -uc -b
RUN dpkg -i ../openocd_0.8.0-4tomaz-bbblack-1_armhf.deb

# Compile and install vesna-drivers
RUN cd /root &&\
git clone -b bbblack https://github.com/avian2/vesna-drivers.git
WORKDIR /root/vesna-drivers/VESNADriversDemo
RUN make drivers_demo.loadbone

# Add vesna management system source
ADD . /root/vms
WORKDIR /root/vms

# Compile vesna management system
RUN ant build
RUN sed -i 's|10000|/dev/ttyS1|g' webapp/vms/vms.html

# Run vesna management system
CMD java -jar build/VesnaManagementSystem.jar -l false -p /dev/ttyS1
