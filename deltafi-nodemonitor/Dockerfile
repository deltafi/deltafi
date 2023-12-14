FROM alpine:3.19.0
RUN apk -U add \
  bash \
  curl \
  netcat-openbsd
WORKDIR /app
COPY . /app
CMD [ "/bin/bash", "-c", "/app/doit.sh" ]
