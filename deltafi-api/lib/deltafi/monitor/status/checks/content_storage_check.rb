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

require 'deltafi'
require 'deltafi/monitor/status/check'

module Deltafi
  module Monitor
    module Status
      module Checks
        class ContentStorageCheck < Status::Check
          DEFAULT_THRESHOLD = 90
          THRESHOLD_PROPERTY = %w[checks contentStoragePercentThreshold].freeze

          def initialize
            super('Content Storage Check')
          end

          def run
            @threshold = threshold
            @usage = usage

            if @usage >= @threshold
              self.code = 1
              message_lines << "##### Content storage usage (#{@usage.round}%) is over the configured threshold (#{@threshold}%):\n"
              message_lines << "\n_Threshold property: #{THRESHOLD_PROPERTY}_"
            end

            self
          end

          private

          def threshold
            DF.system_property(THRESHOLD_PROPERTY, DEFAULT_THRESHOLD).to_i
          end

          def usage
            content = DF::API::V1::Metrics::System.content
            raise "Unable to get content storage metrics!\n\n#{content}" unless content&.values&.all?(&:positive?)

            content[:usage] / content[:limit] * 100
          end
        end
      end
    end
  end
end
