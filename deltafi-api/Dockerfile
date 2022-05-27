# Pinning base image to 2.7-alpine3.13 as 2.7-alpine3.16 had issues in gitlab-ci.
FROM ruby:2.7-alpine3.13 AS build
WORKDIR /app
COPY Gemfile Gemfile.lock /app/
RUN apk add --no-cache make yajl-dev gcc g++ musl-dev && \
    bundle config set path 'vendor/bundle' && \
    bundle install

FROM ruby:2.7-alpine3.13
WORKDIR /app
COPY --from=build /app /app
COPY . /app
COPY *probe.sh /
RUN apk add --no-cache curl && \
    bundle config set path 'vendor/bundle'
EXPOSE 9292
CMD bundle exec rainbows -c rainbows.conf -p 9292
