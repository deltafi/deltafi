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
require 'deltafi/events'

module Deltafi
  module Monitor
    module Status
      module Checks
        class GrafanaAlertCheck < Status::Check
          # Static list  of previous alerts used for statekeeping (minimize repeat events and generate clear events)
          @@previous_alerts = []

          FILTERED_LABELS = %i[__alert_rule_uid__ ref_id alertname datasource_uid grafana_folder].freeze

          def initialize
            super('Grafana Alert Check')
          end

          def run
            alerts = Deltafi::Grafana.alerts

            if alerts && !alerts.empty?
              alerts.each do |alert|
                name = alert.dig :labels, :alertname
                summary = alert.dig :annotations, :summary
                labels = alert[:labels]
                labels[:summary] = summary

                generate_new_event name, labels, labels[:severity]
              end
            end

            clear_old_alerts alerts

            self
          end

          def generate_new_event(name, labels, severity)
            return if @@previous_alerts.include?(name)

            # Event content is a bullet list of all labels on the alert, excluding filtered labels
            content = labels&.reject { |l| FILTERED_LABELS.include? l }&.map do |k, v|
              "- *#{k.capitalize}*: #{v}"
            end&.join("\n")

            Deltafi::Events.generate "Alert: #{name}",
                                     content: content,
                                     severity: severity,
                                     notification: true,
                                     source: 'Grafana'
          end

          def clear_old_alerts(alerts)
            alert_list = alerts&.map { |a| a.dig :labels, :alertname } || []

            @@previous_alerts.reject { |i| alert_list.include? i }.each do |old|
              Deltafi::Events.generate "Alert cleared: #{old}",
                                       severity: 'success',
                                       notification: true,
                                       source: 'Grafana'
            end

            @@previous_alerts = alert_list
          end
        end
      end
    end
  end
end
