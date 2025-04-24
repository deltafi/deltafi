FROM alpine:3.21.3

RUN apk -U add \
  bash \
  curl \
  docker-cli \
  bc \
  netcat-openbsd

WORKDIR /app

COPY . /app

RUN adduser -S deltafi
USER deltafi

CMD [ "/bin/bash", "-c", "/app/doit.sh" ]
