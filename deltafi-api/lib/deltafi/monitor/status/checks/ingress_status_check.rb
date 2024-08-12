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
require 'bytesize'

module Deltafi
  module Monitor
    module Status
      module Checks
        class IngressStatusCheck < Status::Check
          DEFAULT_REQUIRED_MEGABYTES = 1
          REQUIRED_MEGABYTES_PROPERTY = %w[ingressDiskSpaceRequirementInMb].freeze
          INGRESS_ENABLED_PROPERTY = %w[ingressEnabled].freeze
          FLOW_ERRORS_QUERY = 'query { dataSourceErrorsExceeded { name maxErrors currErrors } }'

          @@ingress_disabled_by_storage = false
          @@disabled_flows = []

          def initialize
            super('Ingress Status Check')
          end

          def run
            @required_bytes = required_bytes

            check_for_disabled_ingress
            check_for_storage_disabled_ingress
            check_for_disabled_flows

            self
          end

          private

          def check_for_disabled_ingress
            return if ingress_enabled?

            self.code = 1
            message_lines << "##### Ingress is disabled\n"
            message_lines << "Reenable the system property '#{INGRESS_ENABLED_PROPERTY}' to restart ingress."
          end

          def format_number(number)
            number.to_i.to_s.gsub(/\B(?=(\d{3})*\b)/, ',')
          end

          def check_for_storage_disabled_ingress
            remaining = remaining_bytes
            required = required_bytes

            return unless remaining && required

            if remaining >= required
              notify_storage_enabled
            else
              notify_storage_disabled remaining, required
              self.code = 1
              message_lines << "##### Ingress is disabled due to lack of content storage\n"
              message_lines << "Required bytes in content storage: #{format_number(required)} (#{ByteSize.new(required)})\n"
              message_lines << "Remaining bytes in content storage: #{format_number(remaining)} (#{ByteSize.new(remaining)})\n"
            end
          end

          def notify_storage_disabled(remaining, required)
            return if @@ingress_disabled_by_storage

            content_lines = [
              "- Remaining bytes in content storage: #{format_number(remaining)} (#{ByteSize.new(remaining)})",
              "- Required bytes: #{format_number(required)} (#{ByteSize.new(required)})"
            ]
            Deltafi::Events.generate 'Disabling ingress due to depleted content storage',
                                     content: content_lines.join("\n"),
                                     severity: 'warn',
                                     notification: true,
                                     source: 'ingress'
            @@ingress_disabled_by_storage = true
          end

          def notify_storage_enabled
            return unless @@ingress_disabled_by_storage

            Deltafi::Events.generate 'Ingress is re-enabled',
                                     severity: 'info',
                                     notification: true,
                                     source: 'ingress'
            @@ingress_disabled_by_storage = false
          end

          def required_bytes
            DF::SystemProperties.dig(REQUIRED_MEGABYTES_PROPERTY, DEFAULT_REQUIRED_MEGABYTES).to_i * 1_000_000
          end

          def remaining_bytes
            json = DF::API::V1::Metrics::System.content
            limit = json&.dig(:limit)&.to_i
            usage = json&.dig(:usage)&.to_i
            return unless limit && usage

            limit - usage
          end

          def ingress_enabled?
            DF::SystemProperties.dig(INGRESS_ENABLED_PROPERTY, 'true').to_s.casecmp('true').zero?
          end

          def check_for_disabled_flows
            disabled = current_disabled_flows

            (disabled.keys - @@disabled_flows).each do |error_flow|
              notify_flow_disabled(error_flow, disabled[error_flow][:currErrors], disabled[error_flow][:maxErrors])
            end

            (@@disabled_flows - disabled.keys).each do |fixed_flow|
              notify_flow_reenabled(fixed_flow)
            end

            @@disabled_flows = disabled.keys

            return if @@disabled_flows.empty?

            self.code = 1
            message_lines << "##### Ingress is disabled for flows with too many errors\n"
            message_lines << 'Acknowledge or resolve errors on these flows to continue:'
            disabled.each do |k, v|
              message_lines << "\n- #{k}: #{v[:currErrors]} errors, #{v[:maxErrors]} allowed"
            end
          end

          def notify_flow_disabled(flow, curr_errors, max_errors)
            Deltafi::Events.generate "Alert: Disabling ingress to flow #{flow} due to too many errors",
                                     content: "- Current errors for flow: #{curr_errors}\n- Maximum errors allowed: #{max_errors}\n",
                                     severity: 'warn',
                                     notification: true,
                                     source: 'ingress'
          end

          def notify_flow_reenabled(flow)
            Deltafi::Events.generate "Ingress is re-enabled for flow #{flow}",
                                     severity: 'info',
                                     notification: true,
                                     source: 'ingress'
          end

          def current_disabled_flows
            response = DF.graphql(FLOW_ERRORS_QUERY)
            parsed_response = JSON.parse(response.body, symbolize_names: true)
            raise StandardError, parsed_response[:errors]&.first&.dig(:message) if parsed_response.key?(:errors)

            flow_errors = parsed_response.dig(:data, :dataSourceErrorsExceeded) || []
            flow_errors.to_h do |r|
              [ r[:name], { maxErrors: r[:maxErrors], currErrors: r[:currErrors] } ]
            end
          end
        end
      end
    end
  end
end
