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

require 'deltafi/monitor/service'
require 'deltafi/monitor/status/check'
Dir[File.join(File.dirname(__FILE__), 'checks', '*.rb')].each do |f|
  require "deltafi/monitor/status/checks/#{File.basename(f).split('.')[0]}"
end

module Deltafi
  module Monitor
    module Status
      class Service < Deltafi::Monitor::Service
        include Deltafi::Logger

        PUBLISH_INTERVAL = 5
        COLORS = %w[green yellow red].freeze
        STATES = %w[Healthy Degraded Unhealthy].freeze
        SSE_REDIS_CHANNEL = [DF::Common::SSE_REDIS_CHANNEL_PREFIX, 'status'].compact.join('.')

        def initialize
          super
          @statuses = {}
          @checks = Status::Checks.constants.map { |c| Status::Checks.const_get(c) }
        end

        def run
          spawn_check_threads

          periodic_timer(PUBLISH_INTERVAL) do
            publish_status unless @statuses.keys.empty?
          end
        end

        private

        def spawn_check_threads
          @checks.each do |check|
            Thread.new do
              checker = check.new
              periodic_timer(check::INTERVAL) do
                @statuses[check.name] = checker.run_check
              end
            end
          end
        end

        def publish_status
          debug 'Publishing status'
          overall_code = @statuses.values.map { |s| s[:code] }.max || -1
          status = {
            code: overall_code,
            color: COLORS[overall_code] || 'Unknown',
            state: STATES[overall_code] || 'Unknown',
            checks: @statuses.values.sort_by { |s| [-s[:code], s[:description]] },
            timestamp: Time.now
          }
          DF.redis.set(SSE_REDIS_CHANNEL, status.to_json)
          DF.redis.set(DF::Common::STATUS_REDIS_KEY, status.to_json)
        end
      end
    end
  end
end
