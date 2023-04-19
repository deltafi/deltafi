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
require 'timeout'

module Deltafi
  module Monitor
    module Status
      class Check
        include Deltafi::Logger

        attr_accessor :code, :description, :message_lines, :timestamp, :timeout

        CONFIGMAP = 'deltafi-status-checks'
        INTERVAL = 5

        def initialize(description)
          self.description = description
          # 0 = Success
          # 1 = Warning
          # 2 = Error
          self.code = 0
          self.message_lines = []
          self.timestamp = Time.now
          self.timeout = 60
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
          configmap_data = DF.cluster_mode? ? get_configmap : {}

          if configmap_data.respond_to?(class_name)
            configmap_data.send(class_name)
          else
            puts "No configuration found for #{class_name}"
            {}
          end
        end

        def get_configmap
          DF.k8s_client.api('v1')
            .resource('configmaps', namespace: 'deltafi')
            .get(CONFIGMAP).data
        end

        def run
          raise "#{self.class} should override the `run` method"
        end

        def run_check
          debug 'Running check'

          begin
            Timeout.timeout(timeout) do
              run
            end
          rescue Timeout::Error => e
            msg = "Timeout occurred while running check. Check took longer than #{timeout} seconds to complete."
            backtrace = backtrace_lines(e)
            error msg
            error backtrace
            message_lines << msg
            message_lines << "```\n#{backtrace}\n```"
            self.code = 1
          rescue StandardError => e
            msg = 'Error occurred while running check.'
            backtrace = backtrace_lines(e)
            error msg
            error e.message
            error e.backtrace.join("\n")
            message_lines << msg
            message_lines << "\n\t#{e.message}"
            message_lines << "```\n#{backtrace}\n```"
            self.code = 2
          end

          to_hash
        end

        def backtrace_lines(exception)
          exception.backtrace.take_while { |line| !line.match?('block in run_check') }.join("\n")
        end
      end
    end
  end
end
