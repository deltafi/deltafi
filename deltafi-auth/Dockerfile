FROM deltafi/deltafi-ruby-base:0.102.0
WORKDIR /app
COPY . /app
COPY probe.sh /
RUN apk add --no-cache sqlite-libs && \
    bundle install
EXPOSE 9292
CMD bundle exec rainbows -c rainbows.conf -p 9292
