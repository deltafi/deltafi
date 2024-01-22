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
        class FlowCheck < Status::Check
          QUERY = <<-QUERY
          query {
            getAllFlows {
              transform { sourcePlugin { artifactId groupId version } name flowStatus { state errors { message } } }
              egress { sourcePlugin { artifactId groupId version } name flowStatus { state errors { message } } }
            }
          }
          QUERY

          def initialize
            super('Flow Check')
          end

          def run
            flows_by_type = DF.graphql(QUERY).dig('data', 'getAllFlows')
            raise 'Error getting flow information from core.' if flows_by_type.nil?

            flows_by_type.each do |flow_type, flows|
              invalid_flows = flows.select { |flow| flow.dig('flowStatus', 'state') == 'INVALID' }
              next if invalid_flows.empty?

              self.code = 1
              message_lines << "##### Invalid #{flow_type.capitalize} Flows"
              invalid_flows.each do |flow|
                message_lines << " - __#{flow['name']}__ (#{build_plugin_coordinates(flow['sourcePlugin'])})"
                (flow.dig('flowStatus', 'errors') || []).each do |error|
                  message_lines << "   - #{error['message']}"
                end
              end
            end

            message_lines << "\nVisit the [Flows](/config/flows) page for more info." unless message_lines.empty?

            self
          end

          private

          def build_plugin_coordinates(object)
            [
              object['groupId'],
              object['artifactId'],
              object['version']
            ].join(':')
          end
        end
      end
    end
  end
end
