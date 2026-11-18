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

ARG JAVA_VERSION=21
FROM eclipse-temurin:${JAVA_VERSION}-alpine

# The license applies to the QALIPSIS files of this image; the JRE and the operating system packages
# keep their own licenses. Images adding the QALIPSIS runtime are dual-licensed AGPL-3.0 or commercial.
LABEL org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.source="https://github.com/qalipsis/qalipsis-oss"

WORKDIR /app

RUN apk update --no-cache && \
    apk upgrade --no-cache && \
    apk add --no-cache \
         ca-certificates \
         curl \
         tini \
         wget \
         netcat-openbsd && \
    mkdir -p /app/libs /app/logs /app/config && \
    chmod -R 777 /app

COPY entrypoint.sh /usr/local/bin
RUN chmod +x /usr/local/bin/entrypoint.sh

EXPOSE 8400

ENTRYPOINT ["/sbin/tini", "--", "/usr/local/bin/entrypoint.sh"]
