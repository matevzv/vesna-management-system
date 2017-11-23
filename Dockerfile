FROM sensorlab6/vesna-tools

# File Author / Maintainer
MAINTAINER Matevz Vucnik

# Set debian frontend
ENV DEBIAN_FRONTEND=noninteractive

# Compile firmware
WORKDIR /root
COPY id_rsa_github .ssh/id_rsa
RUN chown root:root .ssh/id_rsa && \
	chmod 600 .ssh/id_rsa && \
	/bin/echo -e 'Host github.com\nHostname ssh.github.com\nPort 443' > .ssh/config && \
	ssh-keyscan -p 443 -t rsa ssh.github.com > .ssh/known_hosts
RUN git clone -b logatec-3 --depth 1 git@github.com:sensorlab/vesna-drivers.git

WORKDIR /root/vesna-drivers/Applications/Logatec/NodeSpectrumSensorLocal
RUN cp ../Clusters/local_usart_networkconf.h ../networkconf.h && \
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
