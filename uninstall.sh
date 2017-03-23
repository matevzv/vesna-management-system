#!/bin/sh

service vmsd stop
echo 'removing vmsd service'
if [ -f /etc/debian_version ]; then
		update-rc.d -f vmsd remove		
	elif [ -f /etc/redhat-release ]; then
		chkconfig --del vmsd
	else
		echo "unsupported Linux distribution"
		exit 1
	fi
RM_RES=$(rm /etc/init.d/vmsd 2>&1)
if [ -z "$RM_RES" ]; then
	echo 'VESNA Management System daemon successfully uninstalled ...'
else
	echo "$RM_RES"
	echo 'VESNA Management System daemon uninstall failed ...'
fi
