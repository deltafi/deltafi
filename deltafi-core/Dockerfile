FROM node:20-alpine3.19 as deltafi-ui-build-stage
WORKDIR /ui
COPY ui .
RUN npm install
RUN npm run build

FROM node:20-alpine3.19 as deltafi-docs-build-stage
WORKDIR /docs
COPY docs .
RUN npm install
RUN npm run build

FROM deltafi/deltafi-java-jre:21.0.4-alpine-0
VOLUME /tmp
COPY deltafi-*.jar deltafi-app.jar
COPY probe.sh .
COPY --from=deltafi-ui-build-stage /ui/dist /deltafi
COPY --from=deltafi-docs-build-stage /docs/dist /deltafi/docs
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/deltafi-app.jar"]
