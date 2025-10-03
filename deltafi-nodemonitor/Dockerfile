FROM alpine:3.22

RUN apk -U --no-cache add \
  bash \
  curl \
  docker-cli \
  bc \
  netcat-openbsd \
  postgresql-client \
  && apk upgrade -U --no-cache libexpat libcrypto3 libssl3 busybox coreutils

WORKDIR /app

COPY . /app

RUN adduser -S deltafi
USER deltafi

CMD [ "/bin/bash", "-c", "/app/doit.sh" ]
