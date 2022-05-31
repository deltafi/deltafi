FROM node:14-alpine as deltafi-ui-build-stage
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY ./ .
RUN npm run build

FROM node:14-alpine as graphiql-build-stage
WORKDIR /app
COPY graphiql .
RUN yarn install && yarn build

FROM nginx:1.21-alpine as production-stage
RUN mkdir /app
COPY --from=deltafi-ui-build-stage /app/dist /app
COPY --from=graphiql-build-stage /app/build /app/graphiql
COPY nginx.conf /etc/nginx/nginx.conf
COPY probe.sh /
