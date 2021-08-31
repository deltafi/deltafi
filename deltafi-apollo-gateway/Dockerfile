FROM node:15-alpine
WORKDIR /usr/src/app
COPY package.json package-lock.json /usr/src/app/
RUN npm install
COPY . .
EXPOSE 4000
CMD [ "node", "app.js" ]
