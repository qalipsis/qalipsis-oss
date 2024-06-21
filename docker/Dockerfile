FROM --platform=$TARGETPLATFORM nginx:alpine
LABEL maintainer="QALIPSIS Team <dev@qalipsis.io>"

RUN apk add --no-cache tini envsubst curl

ADD nginx/* /etc/nginx/
ADD nginx/conf.d/http-log.conf /etc/nginx/conf.d/http-log.conf
ADD nginx/sites-enabled/http-site.conf.template /etc/nginx/conf.d/http-site.conf.template

ADD public /usr/share/nginx/html/

RUN mkdir -p /var/log/nginx /var/cache/nginx /var/lib/nginx /var/tmp/nginx && \
    chown -R nginx:root /etc/nginx /var/log/nginx /var/cache/nginx /var/lib/nginx /var/tmp/nginx && \
    chmod -R 770 /etc/nginx /var/log/nginx /var/cache/nginx /var/lib/nginx /var/tmp/nginx && \
    chmod 777 /var/run

ADD entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

HEALTHCHECK CMD ["nc", "-z", "localhost", "80"]
ENTRYPOINT ["/sbin/tini", "--", "/usr/local/bin/entrypoint.sh"]

