#!/usr/bin/env bash
#
#/**
# * Licensed to the Apache Software Foundation (ASF) under one
# * or more contributor license agreements.  See the NOTICE file
# * distributed with this work for additional information
# * regarding copyright ownership.  The ASF licenses this file
# * to you under the Apache License, Version 2.0 (the
# * "License"); you may not use this file except in compliance
# * with the License.  You may obtain a copy of the License at
# *
# *     http://www.apache.org/licenses/LICENSE-2.0
# * Unless required by applicable law or agreed to in writing, software
# * distributed under the License is distributed on an "AS IS" BASIS,
# * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# * See the License for the specific language governing permissions and
# * limitations under the License.
# */
#
# Runs a Hadoop hbase connector command as a daemon.
#
# Environment Variables
#
#   HBASE_CONNECTOR_CONF_DIR   Alternate hbase conf dir. Default is ${HBASE_HOME}/conf.
#   HBASE_CONNECTOR_LOG_DIR    Where log files are stored.  PWD by default.
#   HBASE_CONNECTOR_PID_DIR    The pid files are stored. /tmp by default.
#   HBASE_CONNECTOR_IDENT_STRING   A string representing this instance of hadoop. $USER by default
#   HBASE_CONNECTOR_NICENESS The scheduling priority for daemons. Defaults to 0.
#   HBASE_CONNECTOR_STOP_TIMEOUT  Time, in seconds, after which we kill -9 the server if it has not stopped.
#                        Default 1200 seconds.
#
# Modelled after $HBASE_HOME/bin/hbase-daemon.sh

usage="Usage: hbase-connectors-daemon.sh [--config <conf-dir>]\
 [--autostart-window-size <window size in hours>]\
 [--autostart-window-retry-limit <retry count limit for autostart>]\
 (start|stop|restart|autostart|autorestart|foreground_start) <hbase-connector-commmand> \
 <args...>"

# if no args specified, show usage
if [ $# -le 1 ]; then
  echo $usage
  exit 1
fi

# default autostart args value indicating infinite window size and no retry limit
AUTOSTART_WINDOW_SIZE=0
AUTOSTART_WINDOW_RETRY_LIMIT=0

bin=`dirname "${BASH_SOURCE-$0}"`
bin=`cd "$bin">/dev/null; pwd`

. "$bin"/hbase-connectors-config.sh



# get arguments
startStop=$1
shift

command=$1
shift

rotate_log ()
{
    log=$1;
    num=5;
    if [ -n "$2" ]; then
    num=$2
    fi
    if [ -f "$log" ]; then # rotate logs
    while [ $num -gt 1 ]; do
        prev=`expr $num - 1`
        [ -f "$log.$prev" ] && mv -f "$log.$prev" "$log.$num"
        num=$prev
    done
    mv -f "$log" "$log.$num";
    fi
}

cleanAfterRun() {
  if [ -f ${HBASE_CONNECTOR_PID} ]; then
    # If the process is still running time to tear it down.
    kill -9 `cat ${HBASE_CONNECTOR_PID}` > /dev/null 2>&1
    rm -f ${HBASE_CONNECTOR_PID} > /dev/null 2>&1
  fi
}

check_before_start(){
    #ckeck if the process is not running
    mkdir -p "$HBASE_CONNECTOR_PID_DIR"
    if [ -f "$HBASE_CONNECTOR_PID" ]; then
      if kill -0 `cat $HBASE_CONNECTOR_PID` > /dev/null 2>&1; then
        echo $command running as process `cat $HBASE_CONNECTOR_PID`.  Stop it first.
        exit 1
      fi
    fi
}

wait_until_done ()
{
    p=$1
    cnt=${HBASE_CONNECTOR_SLAVE_TIMEOUT:-300}
    origcnt=$cnt
    while kill -0 $p > /dev/null 2>&1; do
      if [ $cnt -gt 1 ]; then
        cnt=`expr $cnt - 1`
        sleep 1
      else
        echo "Process did not complete after $origcnt seconds, killing."
        kill -9 $p
        exit 1
      fi
    done
    return 0
}

waitForProcessEnd() {
  pidKilled=$1
  commandName=$2
  processedAt=`date +%s`
  while kill -0 $pidKilled > /dev/null 2>&1;
   do
     echo -n "."
     sleep 1;
     # if process persists more than $HBASE_STOP_TIMEOUT (default 1200 sec) no mercy
     if [ $(( `date +%s` - $processedAt )) -gt ${HBASE_STOP_TIMEOUT:-1200} ]; then
       break;
     fi
   done
  # process still there : kill -9
  if kill -0 $pidKilled > /dev/null 2>&1; then
    echo -n force stopping $commandName with kill -9 $pidKilled
    $JAVA_HOME/bin/jstack -l $pidKilled > "$logout" 2>&1
    kill -9 $pidKilled > /dev/null 2>&1
  fi
  # Add a CR after we're done w/ dots.
  echo
}


# get log directory
if [ "$HBASE_CONNECTOR_LOG_DIR" = "" ]; then
  export HBASE_CONNECTOR_LOG_DIR="$HBASE_CONNECTOR_HOME/logs"
fi
mkdir -p "$HBASE_CONNECTOR_LOG_DIR"

if [ "$HBASE_CONNECTOR_PID_DIR" = "" ]; then
  HBASE_CONNECTOR_PID_DIR=/tmp
fi

if [ "$HBASE_CONNECTOR_IDENT_STRING" = "" ]; then
  export HBASE_CONNECTOR_IDENT_STRING="$USER"
fi

# Some variables
# Work out java location so can print version into log.
if [ "$JAVA_HOME" != "" ]; then
  #echo "run java in $JAVA_HOME"
  JAVA_HOME=$JAVA_HOME
fi
if [ "$JAVA_HOME" = "" ]; then
  echo "Error: JAVA_HOME is not set."
  exit 1
fi

JAVA=$JAVA_HOME/bin/java
export HBASE_CONNECTOR_LOG_PREFIX=hbase-connector-$HBASE_CONNECTOR_IDENT_STRING-$command-$HOSTNAME
export HBASE_CONNECTOR_LOGFILE=$HBASE_CONNECTOR_LOG_PREFIX.log

if [ -z "${HBASE_CONNECTOR_ROOT_LOGGER}" ]; then
export HBASE_CONNECTOR_ROOT_LOGGER=${HBASE_CONNECTOR_ROOT_LOGGER:-"INFO,RFA"}
fi

if [ -z "${HBASE_CONNECTOR_SECURITY_LOGGER}" ]; then
export HBASE_CONNECTOR_SECURITY_LOGGER=${HBASE_CONNECTOR_SECURITY_LOGGER:-"INFO,RFAS"}
fi

HBASE_CONNECTOR_LOGOUT=${HBASE_CONNECTOR_LOGOUT:-"$HBASE_CONNECTOR_LOG_DIR/$HBASE_CONNECTOR_LOG_PREFIX.out"}
HBASE_CONNECTOR_LOGGC=${HBASE_CONNECTOR_LOGGC:-"$HBASE_CONNECTOR_LOG_DIR/$HBASE_CONNECTOR_LOG_PREFIX.gc"}
HBASE_CONNECTOR_LOGLOG=${HBASE_CONNECTOR_LOGLOG:-"${HBASE_CONNECTOR_LOG_DIR}/${HBASE_CONNECTOR_LOGFILE}"}
HBASE_CONNECTOR_CONNECTOR_PID=$HBASE_CONNECTOR_CONNECTOR_PID_DIR/hbase-connector-$HBASE_CONNECTOR_IDENT_STRING-$command.pid

export HBASE_CONNECTOR_AUTOSTART_FILE=$HBASE_CONNECTOR_CONNECTOR_PID_DIR/hbase-connector-$HBASE_CONNECTOR_IDENT_STRING-$command.autostart

if [ -n "$SERVER_GC_OPTS" ]; then
  export SERVER_GC_OPTS=${SERVER_GC_OPTS/"-Xloggc:<FILE-PATH>"/"-Xloggc:${HBASE_CONNECTOR_LOGGC}"}
fi
if [ -n "$CLIENT_GC_OPTS" ]; then
  export CLIENT_GC_OPTS=${CLIENT_GC_OPTS/"-Xloggc:<FILE-PATH>"/"-Xloggc:${HBASE_CONNECTOR_LOGGC}"}
fi

# Set default scheduling priority
if [ "$HBASE_CONNECTOR_NICENESS" = "" ]; then
    export HBASE_CONNECTOR_NICENESS=0
fi

#thiscmd="$bin/${BASH_SOURCE-$0}"
thiscmd="$bin/hbase-connectors-daemon.sh"
args=$@


if [ -f $HBASE_CONNECTOR_PID_DIR/"hbase-connectors-"$command".pid" ];
then
    HBASE_CONNECTOR_PID=$HBASE_CONNECTOR_PID_DIR/"hbase-connectors-"$command".pid"
else
    HBASE_CONNECTOR_PID=""
fi

case $startStop in

(start)
    check_before_start
    rotate_log $HBASE_CONNECTOR_LOGOUT
    rotate_log $HBASE_CONNECTOR_LOGGC
    echo running $command, logging to $HBASE_CONNECTOR_LOGOUT
    $thiscmd --config "${HBASE_CONNECTOR_CONF_DIR}" \
        foreground_start $command $args < /dev/null > ${HBASE_CONNECTOR_LOGOUT} 2>&1  &
    disown -h -r
    sleep 1; head "${HBASE_CONNECTOR_LOGOUT}"
  ;;

(autostart)
    check_before_start
    rotate_log $HBASE_CONNECTOR_LOGOUT
    rotate_log $HBASE_CONNECTOR_LOGGC
    echo running $command, logging to $HBASE_CONNECTOR_LOGOUT
    nohup $thiscmd --config "${HBASE_CONNECTOR_CONF_DIR}" --autostart-window-size ${AUTOSTART_WINDOW_SIZE} --autostart-window-retry-limit ${AUTOSTART_WINDOW_RETRY_LIMIT} \
        internal_autostart $command $args < /dev/null > ${HBASE_CONNECTOR_LOGOUT} 2>&1  &
  ;;

(autorestart)
    echo running $command, logging to $HBASE_CONNECTOR_LOGOUT
    # stop the command
    $thiscmd --config "${HBASE_CONNECTOR_CONF_DIR}" stop $command $args &
    wait_until_done $!
    # wait a user-specified sleep period
    sp=${HBASE_CONNECTOR_RESTART_SLEEP:-3}
    if [ $sp -gt 0 ]; then
      sleep $sp
    fi

    check_before_start
    rotate_log $HBASE_CONNECTOR_LOGOUT
    nohup $thiscmd --config "${HBASE_CONNECTOR_CONF_DIR}" --autostart-window-size ${AUTOSTART_WINDOW_SIZE} --autostart-window-retry-limit ${AUTOSTART_WINDOW_RETRY_LIMIT} \
        internal_autostart $command $args < /dev/null > ${HBASE_CONNECTOR_LOGOUT} 2>&1  &
  ;;

(foreground_start)
    trap cleanAfterRun SIGHUP SIGINT SIGTERM EXIT
    if [ "$HBASE_CONNECTOR_NO_REDIRECT_LOG" != "" ]; then
        # NO REDIRECT
        echo "`date` Starting $command on `hostname`"
        echo "`ulimit -a`"
        # in case the parent shell gets the kill make sure to trap signals.
        # Only one will get called. Either the trap or the flow will go through.
        nice -n $HBASE_CONNECTOR_NICENESS "$HBASE_CONNECTOR_HOME"/bin/hbase-connectors \
            --config "${HBASE_CONNECTOR_CONF_DIR}" \
            $command "$@" start &
    else
        echo "`date` Starting $command on `hostname`" >> ${HBASE_CONNECTOR_LOGLOG}
        echo "`ulimit -a`" >> "$HBASE_CONNECTOR_LOGLOG" 2>&1
        # in case the parent shell gets the kill make sure/ to trap signals.
        # Only one will get called. Either the trap or the flow will go through.
        nice -n $HBASE_CONNECTOR_NICENESS "$HBASE_CONNECTOR_HOME"/bin/hbase-connectors \
            --config "${HBASE_CONNECTOR_CONF_DIR}" \
            $command "$@" start >> ${HBASE_CONNECTOR_LOGOUT} 2>&1 &
    fi
    # Add to the command log file vital stats on our environment.
    hbase_connector_pid=$!
    HBASE_CONNECTOR_PID=$HBASE_CONNECTOR_PID_DIR/"hbase-connectors-"$command".pid"
    echo $hbase_connector_pid > ${HBASE_CONNECTOR_PID}
    wait $hbase_connector_pid
  ;;

(internal_autostart)
    ONE_HOUR_IN_SECS=3600
    autostartWindowStartDate=`date +%s`
    autostartCount=0
    touch "$HBASE_CONNECTOR_AUTOSTART_FILE"

    # keep starting the command until asked to stop. Reloop on software crash
    while true
    do
      rotate_log $HBASE_CONNECTOR_LOGGC
      if [ -f $HBASE_CONNECTOR_PID ] &&  kill -0 "$(cat "$HBASE_CONNECTOR_PID")" > /dev/null 2>&1 ; then
        wait "$(cat "$HBASE_CONNECTOR_PID")"
      else
        #if the file does not exist it means that it was not stopped properly by the stop command
        if [ ! -f "$HBASE_CONNECTOR_AUTOSTART_FILE" ]; then
          echo "`date` HBase might be stopped removing the autostart file. Exiting Autostart process" >> ${HBASE_CONNECTOR_LOGOUT}
          exit 1
        fi

        echo "`date` Autostarting hbase $command service. Attempt no: $(( $autostartCount + 1))" >> ${HBASE_CONNECTOR_LOGLOG}
        touch "$HBASE_CONNECTOR_AUTOSTART_FILE"
        $thiscmd --config "${HBASE_CONNECTOR_CONF_DIR}" foreground_start $command $args
        autostartCount=$(( $autostartCount + 1 ))
      fi

      curDate=`date +%s`
      autostartWindowReset=false

      # reset the auto start window size if it exceeds
      if [ $AUTOSTART_WINDOW_SIZE -gt 0 ] && [ $(( $curDate - $autostartWindowStartDate )) -gt $(( $AUTOSTART_WINDOW_SIZE * $ONE_HOUR_IN_SECS )) ]; then
        echo "Resetting Autorestart window size: $autostartWindowStartDate" >> ${HBASE_CONNECTOR_LOGOUT}
        autostartWindowStartDate=$curDate
        autostartWindowReset=true
        autostartCount=0
      fi

      # kill autostart if the retry limit is exceeded within the given window size (window size other then 0)
      if ! $autostartWindowReset && [ $AUTOSTART_WINDOW_RETRY_LIMIT -gt 0 ] && [ $autostartCount -gt $AUTOSTART_WINDOW_RETRY_LIMIT ]; then
        echo "`date` Autostart window retry limit: $AUTOSTART_WINDOW_RETRY_LIMIT exceeded for given window size: $AUTOSTART_WINDOW_SIZE hours.. Exiting..." >> ${HBASE_CONNECTOR_LOGLOG}
        rm -f "$HBASE_CONNECTOR_AUTOSTART_FILE"
        exit 1
      fi

      # wait for shutdown hook to complete
      sleep 20
    done
  ;;

(stop)
    echo running $command, logging to $HBASE_CONNECTOR_LOGOUT
    rm -f "$HBASE_CONNECTOR_AUTOSTART_FILE"
    if [ "$HBASE_CONNECTOR_PID" != "" ]; then
	if [ -f $HBASE_CONNECTOR_PID ]; then
	    pidToKill=`cat $HBASE_CONNECTOR_PID`
	    # kill -0 == see if the PID exists
	    if kill -0 $pidToKill > /dev/null 2>&1; then
		echo -n stopping $command
		echo "`date` Terminating $command" >> $HBASE_CONNECTOR_LOGLOG
		kill $pidToKill > /dev/null 2>&1
		waitForProcessEnd $pidToKill $command
	    else
		retval=$?
		echo no $command to stop because kill -0 of pid $pidToKill failed with status $retval
	    fi
	else
	    echo no $command to stop because no pid file $HBASE_CONNECTOR_PID
	fi
    else
	echo no $command to stop because no pid file $HBASE_CONNECTOR_PID
    fi

    rm -f $HBASE_CONNECTOR_PID
  ;;

(restart)
    echo running $command, logging to $HBASE_CONNECTOR_LOGOUT
    # stop the command
    $thiscmd --config "${HBASE_CONNECTOR_CONF_DIR}" stop $command $args &
    wait_until_done $!
    # wait a user-specified sleep period
    sp=${HBASE_CONNECTOR_RESTART_SLEEP:-3}
    if [ $sp -gt 0 ]; then
      sleep $sp
    fi
    # start the command
    $thiscmd --config "${HBASE_CONNECTOR_CONF_DIR}" start $command $args &
    wait_until_done $!
  ;;

(*)
  echo $usage
  echo "ze parms " $0 $1 $2 $3 $4
  exit 1
  ;;
esac
