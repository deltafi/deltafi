FROM alpine:3.17.3
RUN apk -U add \
  bash \
  curl \
  netcat-openbsd
WORKDIR /app
COPY . /app
CMD [ "/bin/bash", "-c", "/app/doit.sh" ]
