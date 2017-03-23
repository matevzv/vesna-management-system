#!/bin/sh

SCRIPT=$(readlink -f "$0")
SCRIPTPATH=$(dirname "$SCRIPT")

echo 'Setting up installation directory ...'
sed -i "/<cd-install-dir>/c\cd $SCRIPTPATH" vms.sh
echo 'Setting up service daemon ...'
CP_RES=$(cp vms.sh /etc/init.d/vmsd 2>&1)
if [ -z "$CP_RES" ]; then
	echo 'Updating runlevel information ...'
	if [ -f /etc/debian_version ]; then	
		update-rc.d vmsd defaults		
	elif [ -f /etc/redhat-release ]; then
		chkconfig --add vmsd
	else
		echo "unsupported Linux distribution"
		exit 1
	fi	
	service vmsd start
	echo 'VESNA Management System daemon successfully installed ...'
else
	echo "$CP_RES"
	echo 'VESNA Management System daemon install failed ...'
fi
sed -i "/cd $(echo $SCRIPTPATH | sed -e 's/\\/\\\\/g' -e 's/\//\\\//g' -e 's/&/\\\&/g')/c\<cd-install-dir>" vms.sh
exit 0
