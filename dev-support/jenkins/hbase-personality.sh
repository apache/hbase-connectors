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

function personality_globals
{
  # See notes down by the definition
  BUILDTOOL=hb_maven
  PROC_LIMIT=12500
}

# customizing yetus build to run scaladoc plugin only on hbase-spark project
function personality_modules
{
  local testtype="$2"
  local extra=""
  local MODULES=("${CHANGED_MODULES[@]}")

  clear_personality_queue

  # Always to install at root.
  if [[ ${testtype} == mvninstall ]]; then
    # shellcheck disable=SC2086
    personality_enqueue_module . "${extra}"
    return
  fi

  # If root is in the list of changes, just do whatever test at root
  if [[ "${MODULES[*]}" =~ \. ]]; then
    MODULES=(.)
  fi

  # If we'll end up needing a plugin to run on the spark
  # modules, then we need to ensure a 'package' phase runs.
  if [[ "${MODULES[*]}" =~ \. ]] || \
     [[ "${MODULES[*]}" =~ "spark" ]]; then
    extra="${extra} package"
  fi

  for m in "${MODULES[@]}"; do
    if [[ "$testtype" != "scaladoc" ]]; then
      personality_enqueue_module "${m}" "${extra}"
    else
      if [[ "$m" == "spark/hbase-spark" ]]; then
        personality_enqueue_module spark/hbase-spark "${extra}"
      fi
    fi
  done
}

# { Start workaround stuff caused by our need for a package phase to run
# on hbase-spark-protocol and hbase-spark-protocol-shaded
#
# By default in Yetus any extra parameters given by a personality
# go at the start of the build tool's execution. Unfortunately,
# the order of maven phases matters for how maven chooses to run
# those phases. For the specific plugins we have (i.e. the shade plugin)
# we need the package phase to be ordered after any clean phase requested
# several of the invocation called by Yetus's maven plugin include a clean.
#
# To work around this, we define a new build tool 'hb_maven' that
# mostly wraps the built in maven plugin. It differs when calling the
# module worker function for specific tests. Instead of calling the
# built in one from Yetus (that isn't replaceable) we instead call one of
# our own making. This new module workers function puts additional
# parameters from personalities after the normal executor args.
add_build_tool hb_maven


# we redefine the modules_workers method here as well as the maven build
# tool's call so that we can make sure any "clean" phases happen
# before phases added as extra params
# copied from Apache Yetus 0.11.0 release test-patch.sh
function hb_modules_workers {
  declare repostatus=$1
  declare testtype=$2
  shift 2
  declare modindex=0
  declare fn
  declare savestart=${TIMER}
  declare savestop
  declare repo
  declare modulesuffix
  declare jdk=""
  declare jdkindex=0
  declare statusjdk
  declare result=0
  declare argv
  declare execvalue

  if [[ "${BUILDMODE}" = full ]]; then
    repo="the source"
  elif [[ ${repostatus} == branch ]]; then
    repo=${PATCH_BRANCH}
  else
    repo="the patch"
  fi

  modules_reset

  if verify_multijdk_test "${testtype}"; then
    jdk=$(report_jvm_version "${JAVA_HOME}")
    statusjdk=" with JDK v${jdk}"
    jdk="-jdk${jdk}"
    jdk=${jdk// /}
    yetus_debug "Starting MultiJDK mode${statusjdk} on ${testtype}"
  fi

  until [[ ${modindex} -eq ${#MODULE[@]} ]]; do
    start_clock

    fn=$(module_file_fragment "${MODULE[${modindex}]}")
    fn="${fn}${jdk}"
    modulesuffix=$(basename "${MODULE[${modindex}]}")
    if [[ ${modulesuffix} = \. ]]; then
      modulesuffix="root"
    fi

    if ! buildtool_cwd "${modindex}"; then
      echo "${BASEDIR}/${MODULE[${modindex}]} no longer exists. Skipping."
      ((modindex=modindex+1))
      savestop=$(stop_clock)
      MODULE_STATUS_TIMER[${modindex}]=${savestop}
      continue
    fi

    argv=("${@//@@@MODULEFN@@@/${fn}}")
    argv=("${argv[@]//@@@MODULEDIR@@@/${BASEDIR}/${MODULE[${modindex}]}}")

    # XX this bit below is what's different from yetus
    # the order of executor args betwen built in an extra from personality
    # are swapped
    # shellcheck disable=2086,2046
    echo_and_redirect "${PATCH_DIR}/${repostatus}-${testtype}-${fn}.txt" \
      $("${BUILDTOOL}_executor" "${testtype}") \
      "${argv[@]}" \
      ${MODULEEXTRAPARAM[${modindex}]//@@@MODULEFN@@@/${fn}}
    execvalue=$?
    # XX end different bit

    reaper_post_exec "${modulesuffix}" "${repostatus}-${testtype}-${fn}"
    ((execvalue = execvalue + $? ))

    if [[ ${execvalue} == 0 ]] ; then
      module_status \
        ${modindex} \
        +1 \
        "${repostatus}-${testtype}-${fn}.txt" \
        "${modulesuffix} in ${repo} passed${statusjdk}."
    else
      module_status \
        ${modindex} \
        -1 \
        "${repostatus}-${testtype}-${fn}.txt" \
        "${modulesuffix} in ${repo} failed${statusjdk}."
      ((result = result + 1))
    fi

    # compile is special
    if [[ ${testtype} = compile ]]; then
      MODULE_COMPILE_LOG[${modindex}]="${PATCH_DIR}/${repostatus}-${testtype}-${fn}.txt"
      yetus_debug "Compile log set to ${MODULE_COMPILE_LOG[${modindex}]}"
    fi

    savestop=$(stop_clock)
    MODULE_STATUS_TIMER[${modindex}]=${savestop}
    # shellcheck disable=SC2086
    echo "Elapsed: $(clock_display ${savestop})"
    popd >/dev/null || return 1
    ((modindex=modindex+1))
  done

  TIMER=${savestart}

  if [[ ${result} -gt 0 ]]; then
    return 1
  fi
  return 0
}


function hb_maven_add_install {
  maven_add_install "$@"
}

function hb_maven_delete_install {
  maven_delete_install "$@"
}

function hb_maven_ws_replace {
  maven_ws_replace "$@"
}

function hb_maven_usage {
  maven_usage "$@"
}

function hb_maven_parse_args {
  maven_parse_args "$@"
}

function hb_maven_initialize {
  maven_initialize "$@"
}

function hb_maven_precheck {
  maven_precheck "$@"
}

function hb_maven_filefilter {
  maven_filefilter "$@"
}

function hb_maven_buildfile {
  maven_buildfile "$@"
}

function hb_maven_executor {
  maven_executor "$@"
}

function hb_maven_javac_logfilter {
  maven_javac_logfilter "$@"
}

function hb_maven_javadoc_logfilter {
  maven_javadoc_logfilter "$@"
}

function hb_maven_javac_calcdiffs {
  maven_javac_calcdiffs "$@"
}

function hb_maven_javadoc_calcdiffs {
  maven_javadoc_calcdiffs "$@"
}

function hb_maven_builtin_personality_modules {
  maven_builtin_personality_modules "$@"
}

function hb_maven_builtin_personality_file_tests {
  maven_builtin_personality_file_tests "$@"
}

function hb_maven_reorder_modules {
  maven_reorder_modules "$@"
}

function hb_maven_docker_support {
  maven_docker_support "$@"
}

# Copied from Yetus 0.11.1 maven.sh
function hb_maven_precompile {
  declare repostatus=$1
  declare result=0
  declare need=${2:-false}

  # Only run for hb_maven
  if [[ ${BUILDTOOL} != hb_maven ]]; then
    return 0
  fi

  # not everything needs a maven install
  # but quite a few do ...
  # shellcheck disable=SC2086
  for index in "${MAVEN_NEED_INSTALL[@]}"; do
    if verify_needed_test "${index}"; then
      need=true
    fi
  done

  if [[ "${need}" == false ]]; then
    return 0
  fi

  if [[ "${repostatus}" == branch ]]; then
    big_console_header "maven install: ${PATCH_BRANCH}"
  else
    big_console_header "maven install: ${BUILDMODE}"
  fi

  personality_modules "${repostatus}" mvninstall
  modules_workers "${repostatus}" mvninstall -fae \
    clean install \
    -DskipTests=true -Dmaven.javadoc.skip=true \
    -Dcheckstyle.skip=true -Dfindbugs.skip=true \
    -Dspotbugs.skip=true
  result=$?
  modules_messages "${repostatus}" mvninstall true
  if [[ ${result} != 0 ]]; then
    return 1
  fi
  return 0
}

function hb_maven_reorder_module_process {
  maven_reorder_module_process "$@"
}

# copied from Apache Yetus 0.11.0 maven.sh
function hb_maven_modules_worker {
  declare repostatus=$1
  declare tst=$2
  declare maven_unit_test_filter

  maven_unit_test_filter="$(maven_unit_test_filter)"
  # shellcheck disable=SC2034
  UNSUPPORTED_TEST=false

  case ${tst} in
    findbugs)
      hb_modules_workers "${repostatus}" findbugs test-compile findbugs:findbugs -DskipTests=true
    ;;
    compile)
      hb_modules_workers "${repostatus}" compile clean test-compile -DskipTests=true
    ;;
    distclean)
      hb_modules_workers "${repostatus}" distclean clean -DskipTests=true
    ;;
    javadoc)
      hb_modules_workers "${repostatus}" javadoc clean javadoc:javadoc -DskipTests=true
    ;;
    scaladoc)
      hb_modules_workers "${repostatus}" scaladoc clean scala:doc -DskipTests=true
    ;;
    spotbugs)
      hb_modules_workers "${repostatus}" spotbugs test-compile spotbugs:spotbugs -DskipTests=true
    ;;
    unit)
      if [[ -n "${maven_unit_test_filter}" ]]; then
        hb_modules_workers "${repostatus}" unit clean test -fae "${maven_unit_test_filter}"
      else
        hb_modules_workers "${repostatus}" unit clean test -fae
      fi
    ;;
    *)
      # shellcheck disable=SC2034
      UNSUPPORTED_TEST=true
      if [[ ${repostatus} = patch ]]; then
        add_footer_table "${tst}" "not supported by the ${BUILDTOOL} plugin"
      fi
      yetus_error "WARNING: ${tst} is unsupported by ${BUILDTOOL}"
      return 1
    ;;
  esac
}

# } End workaround stuff caused by our need for a package phase to run
# on hbase-spark-protocol and hbase-spark-protocol-shaded
