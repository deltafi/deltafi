#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
        class GrafanaAlertCheck < Status::Check

          def initialize
            super('Grafana Alert Check')
          end

          def run
            alerts = Deltafi::Grafana.alerts
            if(alerts && !alerts.empty?)
              self.code = 1
              message_lines << '##### Active alerts'
              alerts.each do |alert|
                name = alert.dig :labels, :alertname
                summary = alert.dig :annotations, :summary
                labels = alert.dig :labels
                message_lines << "- **#{name}**"
                message_lines << "  - *Summary*: #{summary}" if summary
                labels&.each do |k,v|
                  message_lines << "  - *#{k.capitalize}*: #{v}" unless [:'__alert_rule_uid__', :ref_id, :alertname, :datasource_uid, :grafana_folder].include? k
                end
              end
            end
            self
          end

        end
      end
    end
  end
end
