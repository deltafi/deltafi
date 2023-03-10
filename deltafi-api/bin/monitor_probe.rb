#!/usr/bin/env ruby
#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
#

# frozen_string_literal: true

$LOAD_PATH.unshift File.expand_path(File.join(File.dirname(__FILE__), '../lib'))

require 'redis'
require 'deltafi/common'

REDIS_RETRY_COUNT = 30
THRESHOLD = 30 # seconds

def redis_client
  url = ENV['DELTAFI_REDIS_MASTER_PORT'].gsub('tcp://', 'redis://')
  password = ENV.fetch('REDIS_PASSWORD', nil)
  retries = 0
  begin
    Redis.new(
      url: url,
      password: password,
      reconnect_attempts: REDIS_RETRY_COUNT,
      reconnect_delay: 1,
      reconnect_delay_max: 5
    )
  rescue Errno::EALREADY => e
    raise e if retries >= REDIS_RETRY_COUNT

    sleep 1
    retries += 1
    retry
  end
end

exit 1 if redis_client.get(Deltafi::Common::HEARTBEAT_REDIS_KEY).to_i < (Time.now.to_i - THRESHOLD)
