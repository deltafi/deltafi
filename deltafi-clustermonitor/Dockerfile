# Scab kubectl from this bitnami image...
FROM bitnami/kubectl:1.33.4 AS kubectl

FROM ubuntu:plucky
RUN apt-get -y update && apt-get -y install bash netcat-openbsd && apt-get clean && rm -rf /var/lib/apt/lists/*
COPY --from=kubectl /opt/bitnami/kubectl/bin/kubectl /usr/local/bin

RUN useradd -r -s /bin/false deltafi
USER deltafi

WORKDIR /app
COPY . /app
CMD [ "bash", "-c", "/app/doit.sh" ]
