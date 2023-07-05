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
        SSE_REDIS_CHANNEL = [DF::Common::SSE_REDIS_CHANNEL_PREFIX, 'deltaFileStats'].compact.join('.')
        INTERVAL = 5

        def query(in_flight_only:)
          query = "query { deltaFileStats(inFlightOnly: #{in_flight_only}) { count referencedBytes totalBytes } }"
          response = DF.graphql(query)
          parsed_response = JSON.parse(response.body, symbolize_names: true)
          raise StandardError, parsed_response[:errors]&.first&.dig(:message) if parsed_response.key?(:errors)

          parsed_response.dig(:data, :deltaFileStats)
        end

        def run
          periodic_timer(INTERVAL) do
            result = {
              all: query(in_flight_only: false),
              inFlight: query(in_flight_only: true)
            }

            @redis.publish(SSE_REDIS_CHANNEL, result.to_json)

            result.each do |mode, metrics|
              metrics.each do |k, v|
                DF::Metrics.record_metric(
                  prefix: "gauge.deltafile.#{mode}",
                  name: k,
                  value: v.to_i,
                  gauge: true
                )
              end
            end
          end
        end
      end
    end
  end
end
