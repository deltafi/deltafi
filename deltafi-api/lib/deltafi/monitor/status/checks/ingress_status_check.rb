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
        class IngressStatusCheck < Status::Check
          DEFAULT_REQUIRED_MEGABYTES = 1
          REQUIRED_MEGABYTES_PROPERTY = %w[ingress diskSpaceRequirementInMb].freeze
          INGRESS_ENABLED_PROPERTY = %w[ingress enabled].freeze

          @@ingress_disabled_by_storage = false

          def initialize
            super('Ingress Status Check')
          end

          def run
            @required_bytes = required_bytes

            check_for_disabled_ingress
            check_for_storage_disabled_ingress

            self
          end

          private

          def check_for_disabled_ingress
            return if ingress_enabled?

            self.code = 1
            message_lines << "##### Ingress is disabled\n"
            message_lines << "Reenable the system property '#{INGRESS_ENABLED_PROPERTY}' to restart ingress."
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
              message_lines << "Required bytes in content storage: #{required}\n"
              message_lines << "Remaining bytes in content storage: #{remaining}\n"
            end
          end

          def notify_storage_disabled(remaining, required)
            return if @@ingress_disabled_by_storage

            Deltafi::Events.generate 'Alert: Disabling ingress due to depleted content storage',
                                     content: "- Remaining bytes in content storage: #{remaining}\n- Required bytes: #{required}\n",
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
        end
      end
    end
  end
end
