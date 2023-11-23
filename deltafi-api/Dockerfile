FROM deltafi/deltafi-ruby:3.2.2-alpine3.18-1 AS build
WORKDIR /app
COPY . /app/
RUN apk add --no-cache build-base openssl-dev && \
    bundle install

FROM deltafi/deltafi-ruby:3.2.2-alpine3.18-1
WORKDIR /app
COPY --from=build /app /app
COPY *probe.sh /
RUN apk add skopeo
EXPOSE 9292
CMD bundle exec rainbows -c rainbows.conf -p 9292
