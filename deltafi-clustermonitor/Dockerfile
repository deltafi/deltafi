# Scab kubectl from this bitnami image...
FROM bitnami/kubectl:1.30.3 as kubectl

FROM ubuntu:noble
RUN apt-get -y update && apt-get -y install bash netcat-openbsd && apt-get clean && rm -rf /var/lib/apt/lists/*
COPY --from=kubectl /opt/bitnami/kubectl/bin/kubectl /usr/local/bin
# USER 1001
WORKDIR /app
COPY . /app
CMD [ "bash", "-c", "/app/doit.sh" ]
