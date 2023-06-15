FROM node:14-alpine as deltafi-docs-build-stage
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY ./ .
ENV BASE_PATH=/docs/
RUN npm run build

FROM nginx:1.25.1-alpine as production-stage
COPY --from=deltafi-docs-build-stage /app/dist /usr/share/nginx/html/docs
