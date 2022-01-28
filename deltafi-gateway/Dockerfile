FROM node:15-alpine
WORKDIR /usr/src/app
COPY package.json package-lock.json /usr/src/app/
RUN apk add --no-cache curl && \
    npm install
COPY . .
COPY probe.sh /
EXPOSE 4000
CMD [ "node", "app.js" ]
