# nats-ingress

DeltaFi NATS Ingress Bridge

## Description

Provides a container which will connect to a NATS server and subscribe to a subject. When a message is received on the
subject, the message content will be sent to DeltaFi to ingress on the configured REST data source.

## Set up listen on a subject

To set up a subject, edit your `site/compose.yaml` and add to the services section a configuration like the following:

```
services:
  nats-ingress:
    image: ${DELTAFI_NATS_INGRESS}
    container_name: deltafi-nats-ingress
    depends_on:
      core-scheduler:
        condition: service_healthy
    environment:
      NATS_SERVER_URL: nats://nats:4222
      NATS_SERVER_USER: admin
      NATS_SERVER_PASSWORD: admin123
      NATS_MESSAGE_SUBJECT: passthrough-nats-subject
      NATS_REST_DATA_SOURCE: passthrough-rest-data-source
      NATS_MEDIA_TYPE: application/json
    env_file:
      - ${CONFIG_DIR}/common.env
    labels:
      deltafi-group: deltafi-core
    networks:
      deltafi:
        aliases:
          - "deltafi-nats-ingress-service"
          - "nats-ingress"
    restart: unless-stopped
    volumes:
      - ${DATA_DIR}/certs/${DELTAFI_SECRET_PLUGINS_SSL:-ssl-secret}:/certs
    user: "${USER_ID}:${GROUP_ID}"
```

## Set up listen on a second subject

Edit `site/compose.yaml`, and add another service with a different name, container name, subject, and data source:

```
services:
  nats-ingress:
    image: ${DELTAFI_NATS_INGRESS}
    container_name: deltafi-nats-ingress2
    depends_on:
      core-scheduler:
        condition: service_healthy
    environment:
      NATS_SERVER_URL: nats://nats:4222
      NATS_SERVER_USER: admin
      NATS_SERVER_PASSWORD: admin123
      NATS_MESSAGE_SUBJECT: smoke-nats-subject
      NATS_REST_DATA_SOURCE: smoke-rest-data-source
      NATS_MEDIA_TYPE: application/json
    env_file:
      - ${CONFIG_DIR}/common.env
    labels:
      deltafi-group: deltafi-core
    networks:
      deltafi:
        aliases:
          - "deltafi-nats-ingress-service2"
          - "nats-ingress"
    restart: unless-stopped
    volumes:
      - ${DATA_DIR}/certs/${DELTAFI_SECRET_PLUGINS_SSL:-ssl-secret}:/certs
    user: "${USER_ID}:${GROUP_ID}"
```

## Testing

To start a NATS server for testing, add the following to the services section of `site/compose.yaml`:

```
services:
  nats:
    image: nats:2.12.6-alpine
    ports:
      - "4222:4222"
    command: ["--user", "admin", "--pass", "admin123"]
    networks:
      - deltafi

```

1. deltafi up
2. Make sure the passthrough and smoke data sources are running.
3. Install the NATS CLI:  go install github.com/nats-io/natscli/nats@latest
4. `nats pub --user=admin --password=admin123 <subject> <message> -H "K1:V1" -H "K2:V2"` (subject should be
passthrough-nats-subject or smoke-nats-subject)
