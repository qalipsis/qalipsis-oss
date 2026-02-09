#!/bin/sh

#
# QALIPSIS
# Copyright (C) 2025 AERIS IT Solutions GmbH
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
#
#

# Add default JVM options here. You can also use JAVA_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS="-Xss${QALIPSIS_THREAD_STACK:-256k} -Xmx${QALIPSIS_MAX_HEAP:-2g} -Xms${QALIPSIS_MIN_HEAP:-512m} -XX:+Use${QALIPSIS_GC_TYPE:-G1}GC -XX:MaxGCPauseMillis=${QALIPSIS_MAX_GC_PAUSE:-200}"
java $DEFAULT_JVM_OPTS $JAVA_OPTS -cp ".:/app/libs/*" io.qalipsis.runtime.Qalipsis $*
