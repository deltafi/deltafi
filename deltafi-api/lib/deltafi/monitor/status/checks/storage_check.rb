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

require 'deltafi/monitor/status/check'

module Deltafi
  module Monitor
    module Status
      module Checks
        class StorageCheck < Status::Check
          DEFAULT_THRESHOLD = 90
          THRESHOLD_PROPERTY = %w[checks contentStoragePercentThreshold].freeze

          def initialize
            super('Storage Check')
          end

          def run
            nodes_over_threshold = []
            current_threshold = DF::SystemProperties.dig(THRESHOLD_PROPERTY, DEFAULT_THRESHOLD).to_i

            DF::API::V1::Metrics::System.metrics.each do |node, metrics|
              percent = (metrics&.dig(:resources, :disk, :usage).to_f / metrics&.dig(:resources, :disk, :limit) * 100).floor
              nodes_over_threshold << "__#{node}:/data__ is at __#{percent}%__" if percent >= current_threshold
            rescue StandardError => e
              self.code = 1
              message_lines << "##### Unable to calculate storage usage percentage for node __#{node}__.\n"
              message_lines << "Error: #{e.message}\n"
              message_lines << "Metrics:\n```\n#{JSON.pretty_generate(metrics)}\n```"
            end
            return if nodes_over_threshold.empty?

            self.code = 1
            message_lines << "##### Nodes with disk usage over threshold (#{current_threshold}%)"
            message_lines << nodes_over_threshold.map { |n| "- #{n}" }

            self
          end
        end
      end
    end
  end
end
