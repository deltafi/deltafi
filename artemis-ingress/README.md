# artemis-ingress

DeltaFi Artemis-ActiveMQ Ingress Bridge

## Description

Provides a container which will connect to an Artemis broker and subscribe to a JMS queue. When a message is received
on the queue, the message content will be sent to DeltaFi to ingress on the configured REST data source.

## Set up listen on a queue

To set up a queue, edit your `site/compose.yaml` and add to the services section a configuration like the following:

```
services:
  artemis-ingress:
    image: ${DELTAFI_ARTEMIS_INGRESS}
    container_name: deltafi-artemis-ingress
    depends_on:
      core-scheduler:
        condition: service_healthy
    environment:
      JMS_BROKER_URL: tcp://artemis:61616
      JMS_BROKER_USER: admin
      JMS_BROKER_PASSWORD: admin123
      LISTEN_DEST_NAME: passthrough-queue
      JMS_REST_DATA_SOURCE: passthrough-rest-data-source
      JMS_MEDIA_TYPE: application/octet-stream
    env_file:
      - ${CONFIG_DIR}/common.env
    labels:
      deltafi-group: deltafi-core
    networks:
      deltafi:
        aliases:
          - "deltafi-artemis-ingress-service"
          - "artemis-ingress"
    restart: unless-stopped
    volumes:
      - ${DATA_DIR}/certs/${DELTAFI_SECRET_PLUGINS_SSL:-ssl-secret}:/certs
    user: "${USER_ID}:${GROUP_ID}"
```

## Set up listen on a second queue

Edit `site/compose.yaml`, and add another service with a different name, container name, queue, and data source:

```
services:
  artemis-ingress2:
    image: ${DELTAFI_ARTEMIS_INGRESS}
    container_name: deltafi-artemis-ingress2
    depends_on:
      core-scheduler:
        condition: service_healthy
    environment:
      JMS_BROKER_URL: tcp://artemis:61616
      JMS_BROKER_USER: admin
      JMS_BROKER_PASSWORD: admin123
      LISTEN_DEST_NAME: smoke-queue
      JMS_REST_DATA_SOURCE: smoke-rest-data-source
      JMS_MEDIA_TYPE: application/octet-stream
    env_file:
      - ${CONFIG_DIR}/common.env
    labels:
      deltafi-group: deltafi-core
    networks:
      deltafi:
        aliases:
          - "deltafi-artemis-ingress-service2"
          - "artemis-ingress"
    restart: unless-stopped
    volumes:
      - ${DATA_DIR}/certs/${DELTAFI_SECRET_PLUGINS_SSL:-ssl-secret}:/certs
    user: "${USER_ID}:${GROUP_ID}"
```

## Testing

To start an Artemis-ActiveMQ broker for testing, add the following to the services section of `site/compose.yaml`:

```
services:
  artemis:
    image: apache/activemq-artemis:latest
    ports:
      - "61616:61616"
      - "8161:8161"
    environment:
      - ARTEMIS_USER=admin
      - ARTEMIS_PASSWORD=admin123
    networks:
      - deltafi

```

1. deltafi up
2. Make sure the passthrough and smoke data sources are running.
3. Navigate to `http://deltafi.localhost:8161/console/artemis` in a browser.
4. Login with admin/admin123.
5. Select the Queues tab.
6. Use the Send Message option from the right-most column menu to send a test message to a queue.
