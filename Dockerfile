FROM sensorlab6/vesna-tools

# File Author / Maintainer
MAINTAINER Matevz Vucnik

# Set debian frontend
ENV DEBIAN_FRONTEND=noninteractive

# Clone vesna-drivers source
# Note: replace <token> here with a GitHub personal access token
# https://github.com/settings/tokens
WORKDIR /root
RUN git clone -b logatec-3 https://<token>@github.com/avian2/vesna-drivers.git
WORKDIR /root/vesna-drivers/Applications/Logatec/NodeSpectrumSensorLocal
RUN	cp ../Clusters/local_usart_networkconf.h ../networkconf.h && \
	make node.out

# Clone vesna management system source
WORKDIR /root
RUN git clone https://github.com/matevzv/vesna-management-system

# Compile vesna management system
WORKDIR /root/vesna-management-system
RUN ant build

# Prepare to launch
COPY docker/start.sh /root/start.sh
RUN chmod 755 /root/start.sh

ENTRYPOINT ["/root/start.sh"]
