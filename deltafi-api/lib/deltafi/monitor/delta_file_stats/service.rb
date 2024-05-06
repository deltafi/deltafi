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
require 'deltafi/monitor/service'

module Deltafi
  module Monitor
    module DeltaFileStats
      class Service < Deltafi::Monitor::Service
        SSE_VALKEY_CHANNEL = [DF::Common::SSE_VALKEY_CHANNEL_PREFIX, 'deltaFileStats'].compact.join('.')
        INTERVAL = 5

        def query
          query = 'query { deltaFileStats { totalCount inFlightCount inFlightBytes } }'
          response = DF.graphql(query)
          parsed_response = JSON.parse(response.body, symbolize_names: true)
          raise StandardError, parsed_response[:errors]&.first&.dig(:message) if parsed_response.key?(:errors)

          parsed_response.dig(:data, :deltaFileStats)
        end

        def run
          periodic_timer(INTERVAL) do
            result = query

            DF.valkey.set(SSE_VALKEY_CHANNEL, result.to_json)

            DF::Metrics.record_metrics(
              result.map do |k, v|
                {
                  name: k,
                  value: v.to_i
                }
              end, prefix: 'gauge.deltafile', gauge: true
            )
          end
        end
      end
    end
  end
end
