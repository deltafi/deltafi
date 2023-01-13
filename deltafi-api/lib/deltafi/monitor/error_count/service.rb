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

require 'deltafi/common'

module Deltafi
  module Monitor
    module ErrorCount
      class Service
        SSE_REDIS_CHANNEL = [DF::Common::SSE_REDIS_CHANNEL_PREFIX, 'errorCount'].compact.join('.')
        QUERY = 'query { deltaFiles (filter: {stage: ERROR, errorAcknowledged: false}) { totalCount } }'
        INTERVAL = 5

        def initialize
          @redis = DF.redis_client
        end

        def run
          response = DF.graphql(QUERY)
          parsed_response = JSON.parse(response.body, symbolize_names: true)
          raise StandardError, parsed_response[:errors].first[:message] if parsed_response.key?(:errors)

          count = parsed_response.dig(:data, :deltaFiles, :totalCount)
          @redis.publish(SSE_REDIS_CHANNEL, count) unless count.nil?
        end
      end
    end
  end
end
