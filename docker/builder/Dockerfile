FROM openjdk:11-jdk-slim

LABEL maintainer="Eric Jessé <me@ericjesse.com>"

USER root
# https://stackoverflow.com/questions/15505775/debian-apt-packages-hash-sum-mismatch
RUN  > /etc/apt/apt.conf.d/99fixbadproxy
RUN echo "Acquire::http::Pipeline-Depth 0;" >> /etc/apt/apt.conf.d/99fixbadproxy
RUN echo "Acquire::http::No-Cache true;" >> /etc/apt/apt.conf.d/99fixbadproxy
RUN echo "Acquire::BrokenProxy    true;" >> /etc/apt/apt.conf.d/99fixbadproxy
RUN apt-get clean && rm -rf /var/lib/apt/lists/* && apt-get clean && apt-get update && apt-get upgrade -y

RUN apt-get install -y --no-install-recommends \
     curl \
     bash \
     apt-transport-https \
     ca-certificates \
     gnupg2 \
     gcc \
     g++ \
     make \
     software-properties-common

RUN curl -sS https://dl.yarnpkg.com/debian/pubkey.gpg | apt-key add -
RUN echo "deb https://dl.yarnpkg.com/debian/ stable main" | tee /etc/apt/sources.list.d/yarn.list

RUN curl -sL https://deb.nodesource.com/setup_12.x | bash -

RUN apt-get update
RUN apt-get install -y --no-install-recommends \
     wget \
     netcat \
     graphviz \
     nodejs \
     yarn \
     git \
     gettext

RUN curl -fsSL https://download.docker.com/linux/debian/gpg | apt-key add -
RUN add-apt-repository \
       "deb [arch=amd64] https://download.docker.com/linux/debian \
       $(lsb_release -cs) \
       stable"

RUN apt-get update
RUN apt-get install -y docker-ce docker-ce-cli containerd.io

RUN cp /usr/bin/envsubst /usr/local/bin/envsubst && \
    rm -rf /var/lib/apt/lists/*

ENV PATH="$(yarn global bin):$PATH"
RUN yarn global add vega vega-cli

RUN mkdir "$HOME/.gradle"
COPY gradle.properties "$HOME/.gradle"