# frozen_string_literal: true

require 'deltafi/api/status/check'

module Deltafi
  module API
    module Status
      module Checks
        class ActionQueueCheck < Status::Check
          DEFAULT_SIZE_THRESHOLD = 0
          SIZE_THRESHOLD_PROPERTY = 'deltafi.checks.actionQueue.sizeThreshold'
          DGS_QUEUE_NAME = 'dgs'

          def initialize
            super('Action Queue Check')
            @redis_client = Deltafi::API.redis_client
            @threshold = size_threshold
            @queue_names = [ DGS_QUEUE_NAME ] + action_queue_names
            @queues_over_threshold = {}
            @orphan_queues = {}
          end

          def run
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
            (@redis_client.keys - @queue_names).each do |queue_name|
              @orphan_queues[queue_name] = @redis_client.zcount(queue_name, '-inf', '+inf')
            end
          end

          def action_queue_names
            response = Deltafi::API.graphql('query { actionSchemas { id } }')
            response.parsed_response['data']['actionSchemas'].map { |a| a['id'] }
          end

          def size_threshold
            (Deltafi::API.system_properties[SIZE_THRESHOLD_PROPERTY] || DEFAULT_SIZE_THRESHOLD).to_i
          end

          def generate_queue_size_metric(queue_name, queue_size)
            generate_metric(
              source: 'action_queue_check',
              type: 'GAUGE',
              name: 'queue_size',
              value: queue_size,
              tags: { queue_name: queue_name }
            )
          end
        end
      end
    end
  end
end
