FROM deltafi/deltafi-ruby:3.3.0-alpine3.19-1
WORKDIR /app
RUN apk add --no-cache sqlite-libs
COPY . /app
COPY probe.sh /
RUN bundle install
EXPOSE 9292
CMD ./run.sh
