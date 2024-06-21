#!/bin/sh

envsubst '$API_URL' < /etc/nginx/conf.d/http-site.conf.template > /etc/nginx/conf.d/http-site.conf
nginx -g 'daemon off;'