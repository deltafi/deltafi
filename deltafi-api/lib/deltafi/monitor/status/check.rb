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

require 'date'

module Deltafi
  module Monitor
    module Status
      class Check
        attr_accessor :code, :description, :message_lines, :timestamp

        CONFIGMAP = 'deltafi-status-checks'

        def initialize(description)
          self.description = description
          # 0 = Success
          # 1 = Warning
          # 2 = Error
          self.code = 0
          self.message_lines = []
          self.timestamp = Time.now
        end

        def name
          description
        end

        def message
          message_lines.flatten.join("\n")
        end

        def to_hash
          {
            description: description,
            code: code,
            message: message,
            timestamp: timestamp
          }
        end

        def config
          class_name = self.class.name.split('::').last
          configmap_data = DF.k8s_client.api('v1')
                             .resource('configmaps', namespace: 'deltafi')
                             .get(CONFIGMAP).data

          if configmap_data.respond_to?(class_name)
            configmap_data.send(class_name)
          else
            puts "No configuration found for #{class_name}"
            {}
          end
        end

        def run
          raise "#{self.class} should override the `run` method"
        end
      end
    end
  end
end
