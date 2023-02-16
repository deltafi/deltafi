FROM alpine:3.17.2
RUN apk -U add \
  bash \
  curl \
  netcat-openbsd
WORKDIR /app
COPY . /app
CMD [ "/bin/bash", "-c", "/app/doit.sh" ]
