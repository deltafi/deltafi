# Scab kubectl from this bitnami image...
FROM bitnami/kubectl:1.32.2 AS kubectl

FROM ubuntu:noble
RUN apt-get -y update && apt-get -y install bash netcat-openbsd && apt-get clean && rm -rf /var/lib/apt/lists/*
COPY --from=kubectl /opt/bitnami/kubectl/bin/kubectl /usr/local/bin
WORKDIR /app
COPY . /app
CMD [ "bash", "-c", "/app/doit.sh" ]
