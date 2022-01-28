# Pinning base image to 2.7-alpine3.13 as 2.7-alpine3.14 had issues in gitlab-ci.
FROM ruby:2.7-alpine3.13 AS build
WORKDIR /app
COPY Gemfile Gemfile.lock /app/
RUN apk add --no-cache make yajl-dev gcc musl-dev && \
    bundle config set path 'vendor/bundle' && \
    bundle install

FROM ruby:2.7-alpine3.13
COPY probe.sh /
RUN apk add --no-cache curl && \
    mkdir -p /data/deltafi/out && \
    adduser -u 1000 -D egressinator
USER 1000
WORKDIR /app
COPY --from=build /app /app
COPY . /app
RUN bundle config set path 'vendor/bundle'
EXPOSE 9292
CMD bundle exec rackup --host 0.0.0.0
