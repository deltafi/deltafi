FROM --platform=$BUILDPLATFORM node:22-alpine3.19 AS deltafi-ui-build-stage
WORKDIR /ui
# Cache package installation
RUN npm config set fetch-timeout 600000 && npm config set fetch-retry-mintimeout 20000 && npm config set fetch-retry-maxtimeout 120000 && npm config set fetch-retries 5
COPY ui/package.json ui/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm install
COPY ui ./
RUN npm run build

FROM --platform=$BUILDPLATFORM node:22-alpine3.19 AS deltafi-docs-build-stage
WORKDIR /docs
# Cache package installation
RUN npm config set fetch-timeout 600000 && npm config set fetch-retry-mintimeout 20000 && npm config set fetch-retry-maxtimeout 120000 && npm config set fetch-retries 5
COPY docs/package.json ./
RUN --mount=type=cache,target=/root/.npm npm install
COPY docs ./
ENV VUE_APP_EMBEDDED=true
RUN npm run build

FROM --platform=$BUILDPLATFORM node:20-alpine3.19 AS deltafi-graphiql-build-stage
WORKDIR /graphiql
# Cache package installation
RUN yarn config set network-timeout 60000
COPY graphiql/package.json /graphiql/package.json
RUN --mount=type=cache,target=/root/.cache/yarn yarn install --network-timeout 60000
COPY graphiql .
RUN yarn build

FROM --platform=$BUILDPLATFORM alpine:3.22 AS aggregator
COPY --from=deltafi-ui-build-stage /ui/dist /deltafi
COPY --from=deltafi-docs-build-stage /docs/.vitepress/dist /deltafi/docs
COPY --from=deltafi-graphiql-build-stage /graphiql/build /deltafi/graphiql

FROM deltafi/deltafi-java-jre:21.0.9-alpine-3 AS final
VOLUME /tmp
COPY probe.sh .
COPY --from=aggregator /deltafi /deltafi

RUN echo '#!/bin/sh' > /home/deltafi/entrypoint.sh && \
  echo 'if [ -z "$APP_NAME" ]; then export APP_NAME=$(hostname); fi' >> /home/deltafi/entrypoint.sh && \
  echo 'if [ -z "$UNIQUE_ID" ]; then export UNIQUE_ID=$(hostname); fi' >> /home/deltafi/entrypoint.sh && \
  echo 'echo "APP_NAME: $APP_NAME"' >> /home/deltafi/entrypoint.sh && \
  echo 'echo "UNIQUE_ID: $UNIQUE_ID"' >> /home/deltafi/entrypoint.sh && \
  echo 'exec java -Djava.security.egd=file:/dev/./urandom -jar /deltafi-app.jar "$@"' >> /home/deltafi/entrypoint.sh && \
  chmod a+rx /home/deltafi/entrypoint.sh

COPY deltafi-*.jar /deltafi-app.jar

ENTRYPOINT ["/home/deltafi/entrypoint.sh"]
