FROM deltafi/deltafi-ruby-base:1.0.0
WORKDIR /app
COPY . /app
COPY probe.sh /
RUN mkdir -p /data/deltafi/out && \
    adduser -u 1000 -D egressinator && \
    chown -R 1000 /app
USER 1000
RUN bundle config set path 'vendor/bundle' && \
    bundle install
EXPOSE 9292
CMD bundle exec rainbows -c rainbows.conf -p 9292
