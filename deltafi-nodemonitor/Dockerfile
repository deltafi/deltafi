FROM alpine:3.20.2
RUN apk -U add \
  bash \
  curl \
  docker-cli \
  bc \
  netcat-openbsd
WORKDIR /app
COPY . /app
CMD [ "/bin/bash", "-c", "/app/doit.sh" ]
