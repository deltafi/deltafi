FROM deltafi/deltafi-ui-base:2.0.0 as deltafi-ui-build-stage
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY ./ .
RUN npm run build

FROM nginx:1.25.5-alpine3.19 as production-stage
RUN mkdir /app
COPY --from=deltafi-ui-build-stage /app/dist /app
COPY --from=deltafi-ui-build-stage /app/graphiql/build /app/graphiql
COPY nginx.conf /etc/nginx/nginx.conf
COPY probe.sh /
