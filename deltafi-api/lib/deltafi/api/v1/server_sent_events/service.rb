#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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

module Deltafi
  module API
    module V1
      module ServerSentEvents
        class Service
          attr_accessor :subscribers

          def initialize
            self.subscribers = []
            @redis = DF.redis_client

            Thread.new do
              channel_prefix = DF::Common::SSE_REDIS_CHANNEL_PREFIX
              @redis.psubscribe("#{channel_prefix}.*") do |on|
                on.pmessage do |_match, channel, message|
                  channel = channel.sub("#{channel_prefix}.", '')

                  subscribers.each do |conn|
                    conn << "event: #{channel}\n"
                    conn << "data: #{message}\n\n"
                  end
                end
              end
            end
          end
        end
      end
    end
  end
end
