FROM deltafi/deltafi-ruby:3.3.1-alpine3.19-0 AS build
WORKDIR /app
COPY Gemfile* /app/
RUN apk add --no-cache build-base openssl-dev
RUN bundle install

FROM deltafi/deltafi-ruby:3.3.1-alpine3.19-0
WORKDIR /app
COPY --from=build /app /app
COPY . /app/
COPY *probe.sh /
RUN apk add skopeo
EXPOSE 9292
CMD bundle exec rainbows -c rainbows.conf -p 9292
