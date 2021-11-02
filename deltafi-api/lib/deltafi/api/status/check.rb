# frozen_string_literal: true

module Deltafi
  module API
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
          configmap_data = Deltafi::API.k8s_client.api('v1')
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
