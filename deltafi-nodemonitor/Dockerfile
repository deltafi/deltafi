FROM alpine:3.22

RUN apk -U add \
  bash \
  curl \
  docker-cli \
  bc \
  netcat-openbsd \
  postgresql-client

WORKDIR /app

COPY . /app

RUN adduser -S deltafi
USER deltafi

CMD [ "/bin/bash", "-c", "/app/doit.sh" ]
