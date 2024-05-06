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
        class ActionQueueCheck < Status::Check
          DEFAULT_SIZE_THRESHOLD = 0
          SIZE_THRESHOLD_PROPERTY = %w[checks actionQueueSizeThreshold].freeze
          IGNORED_QUEUE_NAMES = [
            DF::Common::STATUS_VALKEY_KEY,
            DF::Common::ACTION_HEARTBEAT_VALKEY_KEY,
            DF::Common::MONITOR_HEARTBEAT_VALKEY_KEY,
            DF::Common::LONG_RUNNING_TASKS_VALKEY_KEY,
            DF::Common::ACTION_QUEUE_SIZES_VALKEY_KEY
          ].freeze

          def initialize
            super('Action Queue Check')
          end

          def run
            @threshold = size_threshold
            recent_queue_names = recent_queues.keys
            @queues_over_threshold = {}
            @orphan_queues = {}

            check_queue_sizes(recent_queue_names)
            check_orphan_queues(recent_queue_names)

            unless @queues_over_threshold.empty?
              self.code = 1
              message_lines << "##### Action queues with size over the configured threshold (#{@threshold}):\n"
              message_lines.concat(@queues_over_threshold.map { |q, s| "- #{q}: __#{s}__" })
              message_lines << "\n_Threshold property: #{SIZE_THRESHOLD_PROPERTY}_"
            end

            unless @orphan_queues.empty?
              self.code = 1
              message_lines << "##### Orphan Queues\n"
              message_lines.concat(@orphan_queues.map { |q, s| "- #{q}: __#{s}__" })
            end

            self
          end

          private

          def recent_queues
            queues = DF.valkey.hgetall(DF::Common::ACTION_HEARTBEAT_VALKEY_KEY)
            queues.select { |_, v| Time.now - Time.parse(v) < DF::Common::ACTION_HEARTBEAT_THRESHOLD }
          end

          def check_queue_sizes(queue_names)
            queue_sizes = {}
            queue_names.each do |queue_name|
              queue_size = DF.valkey.zcount(queue_name, '-inf', '+inf')
              queue_sizes[queue_name] = queue_size
              generate_queue_size_metric(queue_name, queue_size)
              @queues_over_threshold[queue_name] = queue_size if queue_size > @threshold
            end
            DF.valkey.set(DF::Common::ACTION_QUEUE_SIZES_VALKEY_KEY, { time: Time.now, queues: queue_sizes }.to_json)
          end

          def check_orphan_queues(queue_names)
            (DF.valkey.keys - queue_names - IGNORED_QUEUE_NAMES).each do |queue_name|
              next if queue_name.start_with?(DF::Common::SSE_VALKEY_CHANNEL_PREFIX) || queue_name.start_with?('gauge.node')

              @orphan_queues[queue_name] = begin
                                             DF.valkey.zcount(queue_name, '-inf', '+inf')
                                           rescue StandardError
                                             0
                                           end
            end
          end

          def size_threshold
            DF::SystemProperties.dig(SIZE_THRESHOLD_PROPERTY, DEFAULT_SIZE_THRESHOLD).to_i
          end

          def generate_queue_size_metric(queue_name, queue_size)
            DF::Metrics.record_metric(
              prefix: 'gauge.action_queue',
              name: 'queue_size',
              value: queue_size,
              tags: { queue_name: queue_name },
              gauge: true
            )
          end
        end
      end
    end
  end
end
