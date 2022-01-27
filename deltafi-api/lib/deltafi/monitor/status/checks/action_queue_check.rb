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

module Deltafi
  module Monitor
    module Status
      module Checks
        class ActionQueueCheck < Status::Check
          DEFAULT_SIZE_THRESHOLD = 0
          SIZE_THRESHOLD_PROPERTY = %w[checks actionQueueSizeThreshold]
          DGS_QUEUE_NAME = 'dgs'
          INGORED_QUEUE_NAMES = [
            DF::Common::STATUS_REDIS_KEY,
            DF::Common::HEARTBEAT_REDIS_KEY
          ].freeze

          def initialize
            super('Action Queue Check')
            @queues_over_threshold = {}
            @orphan_queues = {}
          end

          def run
            @redis_client = DF.redis_client
            @threshold = size_threshold
            @queue_names = [ DGS_QUEUE_NAME ] + action_queue_names

            check_queue_sizes
            check_orphan_queues

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

          def check_queue_sizes
            @queue_names.each do |queue_name|
              queue_size = @redis_client.zcount(queue_name, '-inf', '+inf')
              generate_queue_size_metric(queue_name, queue_size)
              @queues_over_threshold[queue_name] = queue_size if queue_size > @threshold
            end
          end

          def check_orphan_queues
            (@redis_client.keys - @queue_names - INGORED_QUEUE_NAMES).each do |queue_name|
              @orphan_queues[queue_name] = @redis_client.zcount(queue_name, '-inf', '+inf')
            end
          end

          def action_queue_names
            response = DF.graphql('query { actionDescriptors { name } }')
            response.parsed_response['data']['actionDescriptors'].map { |a| a['name'] }
          end

          def size_threshold
            DF.system_property(SIZE_THRESHOLD_PROPERTY, DEFAULT_SIZE_THRESHOLD).to_i
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