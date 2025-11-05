ARG JAVA_VERSION=21
FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine

WORKDIR /app

RUN apk add --no-cache tini && \
    mkdir -p /app/libs /app/logs /app/config && \
    chmod -R 777 /app

COPY entrypoint.sh /usr/local/bin
RUN chmod +x /usr/local/bin/entrypoint.sh

EXPOSE 8400

ENV LOG_PATH /app/logs
ENV MIN_FLAT_FILE_LOG_LEVEL ALL
ENTRYPOINT ["/sbin/tini", "--", "/usr/local/bin/entrypoint.sh"]
