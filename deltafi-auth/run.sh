#!/bin/sh
bundle exec rake db:migrate
bundle exec rake db:seed
bundle exec rainbows -c rainbows.conf -p 9292