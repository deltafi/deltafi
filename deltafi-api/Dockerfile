FROM deltafi/deltafi-ruby-base:0.99.6-2
WORKDIR /app
COPY . /app
COPY *probe.sh /
RUN bundle install
EXPOSE 9292
CMD bundle exec rainbows -c rainbows.conf -p 9292
