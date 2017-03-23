#!/bin/sh
#
# chkconfig: 2345 90 20
# description: VESNA Management System and HTTP API daemon
### BEGIN INIT INFO
# Provides:          scriptname
# Required-Start:    $local_fs $remote_fs
# Required-Stop:     $local_fs $remote_fs
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6
# Short-Description: Start vmsd at boot time
# Description:       VESNA Management System and HTTP API daemon

<cd-install-dir>
SERVICE_NAME=vmsd
PATH_TO_JAR=dist/"VesnaManagementSystem.jar -s 10000"
PID=$(ps xaf | grep "$PATH_TO_JAR" | grep -v grep | awk '{print $1}')

start() {
	echo "Starting $SERVICE_NAME ..."
	if [ -z $PID ]; then
		nohup java -jar $PATH_TO_JAR > /dev/null 2>&1 &
		echo "$SERVICE_NAME started ..."
	else
		echo "$SERVICE_NAME is already running ..."
	fi
}

stop() {
	if [ ! -z $PID ]; then
		echo "$SERVICE_NAME stoping ..."
		KILL_RES=$(kill $PID 2>&1)		
		if [ -z "$KILL_RES" ]; then
			unset PID			
			echo "$SERVICE_NAME stopped ..."
		else
			echo $KILL_RES
			echo "$SERVICE_NAME failed to stop ..."
		fi
	else	  
		echo "$SERVICE_NAME is not running ..."
	fi
}

status() {
	if [ ! -z $PID ]; then
		echo "$SERVICE_NAME is running ..."
	else	  
		echo "$SERVICE_NAME is not running ..."
	fi
}

case "$1" in
	start)
		start
		;;
	stop)	
		stop	
		;;
	status)
		status
		;;
	restart|reload)	
		stop
		start
		;;
	*)
		echo $"Usage: $SERVICE_NAME {start|stop|restart|reload|status}"
		exit 1
esac
exit 0

### END INIT INFO
