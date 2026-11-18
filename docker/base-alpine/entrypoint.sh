#!/bin/sh

#
# QALIPSIS
# Copyright (C) 2025 AERIS IT Solutions GmbH
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
#

# Add default JVM options here. You can also use JAVA_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS="-XX:+MaxFDLimit -Xss${QALIPSIS_THREAD_STACK:-256k} -Xmx${QALIPSIS_MAX_HEAP:-2g} -Xms${QALIPSIS_MIN_HEAP:-512m} -XX:+Use${QALIPSIS_GC_TYPE:-Z}GC"
java $DEFAULT_JVM_OPTS $JAVA_OPTS -cp ".:/app/libs/*" io.qalipsis.runtime.Qalipsis $*
