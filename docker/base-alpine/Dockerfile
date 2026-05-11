ARG JAVA_VERSION=21
FROM eclipse-temurin:${JAVA_VERSION}-alpine

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
