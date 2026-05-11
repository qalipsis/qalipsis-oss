ARG JAVA_VERSION=21
FROM eclipse-temurin:${JAVA_VERSION}

WORKDIR /app

RUN apt-get clean &&  \
    apt-get update &&  \
    apt-get upgrade -y &&  \
    apt-get install -y --no-install-recommends \
         apt-transport-https \
         ca-certificates \
         curl \
         tini \
         wget \
         netcat-traditional && \
    rm -rf /var/lib/apt/lists/* && \
    mkdir -p /app/libs /app/logs /app/config && \
    chmod -R 777 /app

COPY entrypoint.sh /usr/local/bin
RUN chmod +x /usr/local/bin/entrypoint.sh

EXPOSE 8400

ENTRYPOINT ["/bin/tini", "--", "/usr/local/bin/entrypoint.sh"]
