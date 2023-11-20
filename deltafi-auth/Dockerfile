FROM deltafi/deltafi-ruby:3.2.2-alpine3.18-1
WORKDIR /app
COPY . /app
COPY probe.sh /
RUN apk add --no-cache sqlite-libs && \
    bundle install
EXPOSE 9292
CMD ./run.sh
