FROM node:22-alpine3.19 AS deltafi-ui-build-stage
WORKDIR /ui
# Cache package installation
COPY ui/package.json ui/package-lock.json ./
RUN npm install
COPY ui ./
RUN npm run build

FROM node:20-alpine3.19 AS deltafi-docs-build-stage
WORKDIR /docs
# Cache package installation
COPY docs/package.json ./
RUN npm install
COPY docs ./
RUN npm run build

FROM node:20-alpine3.19 AS deltafi-graphiql-build-stage
WORKDIR /graphiql
# Cache package installation
COPY graphiql/package.json /graphiql/package.json
RUN yarn install
COPY graphiql .
RUN yarn build

FROM deltafi/deltafi-java-jre:21.0.8-alpine-0
VOLUME /tmp
COPY deltafi-*.jar deltafi-app.jar
COPY probe.sh .
COPY --from=deltafi-ui-build-stage /ui/dist /deltafi
COPY --from=deltafi-docs-build-stage /docs/dist /deltafi/docs
COPY --from=deltafi-graphiql-build-stage /graphiql/build /deltafi/graphiql

RUN echo '#!/bin/sh' > /home/deltafi/entrypoint.sh && \
    echo 'if [ -z "$APP_NAME" ]; then export APP_NAME=$(hostname); fi' >> /home/deltafi/entrypoint.sh && \
    echo 'if [ -z "$UNIQUE_ID" ]; then export UNIQUE_ID=$(hostname); fi' >> /home/deltafi/entrypoint.sh && \
    echo 'echo "APP_NAME: $APP_NAME"' >> /home/deltafi/entrypoint.sh && \
    echo 'echo "UNIQUE_ID: $UNIQUE_ID"' >> /home/deltafi/entrypoint.sh && \
    echo 'exec java -Djava.security.egd=file:/dev/./urandom -jar /deltafi-app.jar "$@"' >> /home/deltafi/entrypoint.sh && \
    chmod a+rx /home/deltafi/entrypoint.sh

ENTRYPOINT ["/home/deltafi/entrypoint.sh"]
