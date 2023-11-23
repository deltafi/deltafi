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
require 'deltafi/monitor/status/check'
require 'time'

module Deltafi
  module Monitor
    module Status
      module Checks
        class LongRunningActionsCheck < Status::Check
          include Deltafi::Logger
          def initialize
            super('Long Running Actions Check')
          end

          def run
            actions = long_running_actions.sort_by { |action, _| action }.to_h
            self.description = "Long Running Actions Check#{" (#{actions.size})" unless actions.empty?}"
            unless actions.empty?
              message_lines << '##### Actions with long running tasks:'
              actions.each do |action, values|
                message_lines << "- #{action}"
                values.sort_by { |value| value[:did] }.each do |value|
                  short_did = value[:did][0..7]
                  did_link = "[#{short_did}](/deltafile/viewer/#{value[:did]})"
                  message_lines << "    - #{did_link} - Running >#{value[:time]} seconds"
                end
              end
            end

            self
          end

          private

          def long_running_actions
            tasks = DF.redis.hgetall(DF::Common::LONG_RUNNING_TASKS_REDIS_KEY)

            tasks.each_with_object(Hash.new { |h, k| h[k] = [] }) do |(k, v), result|
              times_array = JSON.parse(v)
              start_time = Time.parse(times_array[0])
              heartbeat_time = Time.parse(times_array[1])

              next unless Time.now - heartbeat_time < 30

              key_parts = k.split(':')
              action = key_parts[1]
              did = key_parts[2]
              result[action] << { did: did, time: (heartbeat_time - start_time).to_i }
            end
          end
        end
      end
    end
  end
end
