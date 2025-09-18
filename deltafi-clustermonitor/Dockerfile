# Scab kubectl from this bitnami image...
FROM rancher/kubectl:v1.33.5 AS kubectl

FROM ubuntu:questing
RUN apt-get -y update && apt-get -y install bash netcat-openbsd && apt-get clean && rm -rf /var/lib/apt/lists/*
COPY --from=kubectl /bin/kubectl /usr/local/bin

RUN useradd -r -s /bin/false deltafi
USER deltafi

WORKDIR /app
COPY . /app
CMD [ "bash", "-c", "/app/doit.sh" ]
