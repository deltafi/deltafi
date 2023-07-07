FROM deltafi/deltafi-ruby-base:1.0.0
WORKDIR /app
COPY . /app
COPY *probe.sh /
RUN bundle install && apk add skopeo
EXPOSE 9292
CMD bundle exec rainbows -c rainbows.conf -p 9292
