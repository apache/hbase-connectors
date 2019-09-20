#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

personality_plugins "all"

# customizing yetus build to run scaladoc plugin only on hbase-spark project
function personality_modules
{
  local testtype="$2"

  clear_personality_queue

  if [[ ${testtype} == mvninstall ]]; then
    # shellcheck disable=SC2086
    personality_enqueue_module .
    return
  fi

  for m in "${CHANGED_MODULES[@]}"; do
    if [[ "$testtype" != "scaladoc" ]]; then
      personality_enqueue_module "${m}"
    else
      if [[ "$m" == "spark/hbase-spark" ]]; then
        personality_enqueue_module spark/hbase-spark
      fi
    fi
  done
}
