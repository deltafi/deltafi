FROM deltafi/deltafi-ruby:3.2.2-alpine3.18-0
WORKDIR /app
COPY ./Gemfile* /app/
COPY *probe.sh /
RUN bundle install && apk add skopeo
COPY . /app
EXPOSE 9292
CMD bundle exec rainbows -c rainbows.conf -p 9292
