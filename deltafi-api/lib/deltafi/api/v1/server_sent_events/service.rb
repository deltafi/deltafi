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

require 'deltafi/logger'
require 'eventmachine'

module Deltafi
  module API
    module V1
      module ServerSentEvents
        class Service
          include Deltafi::Logger

          attr_accessor :subscribers

          HEARTBEAT_INTERVAL = 15

          def initialize
            @subscribers = []
            @channels = {}
            @timers_intiialized = false
          end

          def initialize_timers
            return if @timers_intiialized

            schedule_sse_task
            schedule_heartbeat_task
            @timers_intiialized = true
          end

          def schedule_sse_task
            @sse_task_running = false
            EM.add_periodic_timer(1) do
              if @sse_task_running
                warn 'Skipping sse task, last execution still running'
                return
              end

              @sse_task_running = true
              begin
                sse_keys = DF.valkey.keys.select { |key| key.start_with?(DF::Common::SSE_VALKEY_CHANNEL_PREFIX) }
                sse_keys.each do |sse_key|
                  sse_value = DF.valkey.get(sse_key)
                  channel = sse_key[(DF::Common::SSE_VALKEY_CHANNEL_PREFIX.size + 1)..-1]
                  if sse_value != @channels[channel]
                    @channels[channel] = sse_value
                    debug "Sending on channel '#{channel}' to #{subscribers.size} subscriber(s)" unless subscribers.empty?
                    subscribers.each do |conn|
                      send(conn, channel, sse_value)
                    end
                  end
                ensure
                  @sse_task_running = false
                end
              end
            end
          end

          def schedule_heartbeat_task
            @heartbeat_task_running = false

            EM.add_periodic_timer(HEARTBEAT_INTERVAL) do
              if @heartbeat_task_running
                warn 'Skipping heartbeat task, last execution still running'
                return
              end

              @heartbeat_task_running
              begin
                debug "Sending heartbeat to #{subscribers.size} subscriber(s)" unless subscribers.empty?
                subscribers.each(&:send_heartbeat)
              ensure
                @heartbeat_task_running = false
              end
            end
          end

          def send_all(conn)
            @channels.each do |channel, value|
              send(conn, channel, value)
            end
          end

          def send(conn, channel, sse_value)
            conn << "event: #{channel}\n"
            conn << "data: #{sse_value}\n\n"
          end
        end
      end
    end
  end
end
