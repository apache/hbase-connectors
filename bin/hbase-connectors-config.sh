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
# *
# * Unless required by applicable law or agreed to in writing, software
# * distributed under the License is distributed on an "AS IS" BASIS,
# * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# * See the License for the specific language governing permissions and
# * limitations under the License.
# */

# included in all the hbase connector scripts with source command
# should not be executable directly
# also should not be passed any arguments, since we need original $*
# Modelled after $HADOOP_HOME/bin/hadoop-env.sh.

# resolve links - "${BASH_SOURCE-$0}" may be a softlink

this="${BASH_SOURCE-$0}"
while [ -h "$this" ]; do
  ls=`ls -ld "$this"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '.*/.*' > /dev/null; then
    this="$link"
  else
    this=`dirname "$this"`/"$link"
  fi
done

# convert relative path to absolute path
bin=`dirname "$this"`
script=`basename "$this"`
bin=`cd "$bin">/dev/null; pwd`
this="$bin/$script"

# the root of the hbase connector installation
if [ -z "$HBASE_CONNECTOR_HOME" ]; then
  export HBASE_CONNECTOR_HOME=`dirname "$this"`/..
fi

#check to see if the conf dir or hbase home are given as an optional arguments
while [ $# -gt 1 ]
do
  if [ "--config" = "$1" ]
  then
    shift
    confdir=$1
    shift
    HBASE_CONF_DIR=$confdir
  elif [ "--autostart-window-size" = "$1" ]
  then
    shift
    AUTOSTART_WINDOW_SIZE=$(( $1 + 0 ))
    if [ $AUTOSTART_WINDOW_SIZE -lt 0 ]; then
      echo "Invalid value for --autostart-window-size, should be a positive integer"
      exit 1
    fi
    shift
  elif [ "--autostart-window-retry-limit" = "$1" ]
  then
    shift
    AUTOSTART_WINDOW_RETRY_LIMIT=$(( $1 + 0 ))
    if [ $AUTOSTART_WINDOW_RETRY_LIMIT -lt 0 ]; then
      echo "Invalid value for --autostart-window-retry-limit, should be a positive integer"
      exit 1
    fi
    shift
  elif [ "--internal-classpath" = "$1" ]
  then
    shift
    # shellcheck disable=SC2034
    INTERNAL_CLASSPATH="true"
  elif [ "--debug" = "$1" ]
  then
    shift
    # shellcheck disable=SC2034
    DEBUG="true"
  else
    # Presume we are at end of options and break
    break
  fi
done



# Allow alternate hbase connector conf dir location.
HBASE_CONNECTOR_CONF_DIR="${HBASE_CONNECTOR_CONF_DIR:-$HBASE_CONNECTOR_HOME/conf}"


if [ -n "$HBASE_CONNECTOR_JMX_BASE" ] && [ -z "$HBASE_CONNECTOR_JMX_OPTS" ]; then
  HBASE_CONNECTOR_JMX_OPTS="$HBASE_CONNECTOR_JMX_BASE"
fi


# Source the hbase-connector-env.sh only if it has not already been done. HBASE_CONNECTOR_ENV_INIT keeps track of it.
if [ -z "$HBASE_CONNECTOR_ENV_INIT" ] && [ -f "${HBASE_CONNECTOR_CONF_DIR}/hbase-connector-env.sh" ]; then
  . "${HBASE_CONNECTOR_CONF_DIR}/hbase-connector-env.sh"
  export HBASE_CONNECTOR_ENV_INIT="true"
fi

# Newer versions of glibc use an arena memory allocator that causes virtual
# memory usage to explode. Tune the variable down to prevent vmem explosion.
export MALLOC_ARENA_MAX=${MALLOC_ARENA_MAX:-4}


# Now having JAVA_HOME defined is required
if [ -z "$JAVA_HOME" ]; then
    cat 1>&2 <<EOF
+======================================================================+
|                    Error: JAVA_HOME is not set                       |
+----------------------------------------------------------------------+
| Please download the latest Sun JDK from the Sun Java web site        |
|     > http://www.oracle.com/technetwork/java/javase/downloads        |
|                                                                      |
| HBase Connectors requires Java 1.8 or later.                                    |
+======================================================================+
EOF
    exit 1
fi
