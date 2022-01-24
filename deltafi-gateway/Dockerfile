FROM node:15-alpine
RUN apk add --no-cache curl
WORKDIR /usr/src/app
COPY package.json package-lock.json /usr/src/app/
RUN npm install
COPY . .
COPY probe.sh /
EXPOSE 4000
CMD [ "node", "app.js" ]
