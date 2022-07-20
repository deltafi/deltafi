# FROM ruby:2.7-alpine3.13
FROM alpine:3.16.0
RUN apk -U add \
  bash \
  curl \
  netcat-openbsd
WORKDIR /app
COPY . /app
CMD [ "/bin/bash", "-c", "/app/doit.sh" ]
