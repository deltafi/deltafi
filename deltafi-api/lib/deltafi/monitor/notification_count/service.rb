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

$LOAD_PATH.unshift File.expand_path(File.join(File.dirname(__FILE__), '../../../../models'))

require 'deltafi/common'
require 'event'

module Deltafi
  module Monitor
    module NotificationCount
      class Service
        SSE_REDIS_CHANNEL = [DF::Common::SSE_REDIS_CHANNEL_PREFIX, 'notificationCount'].compact.join('.')
        INTERVAL = 5

        def initialize
          @redis = DF.redis_client
        end

        def run
          query = {
            timestamp: (7.days.ago..Time.now),
            notification: true,
            acknowledged: false
          }
          count = Event.where(query).count
          @redis.publish(SSE_REDIS_CHANNEL, count)
        end
      end
    end
  end
end
