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

        def generate_metric(type:, name:, value:, timestamp: DateTime.now.strftime('%Q'), source: 'api', tags: {})
          metric = {
            timestamp: DateTime.now.strftime('%Q'),
            metric: {
              source: source,
              name: name,
              value: value,
              type: type,
              timestamp: timestamp,
              tags: tags
            }
          }
          puts metric.to_json
        end

        def run
          raise "#{self.class} should override the `run` method"
        end
      end
    end
  end
end
