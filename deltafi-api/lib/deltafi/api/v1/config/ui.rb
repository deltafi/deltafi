# frozen_string_literal: true

module Deltafi
  module API
    module V1
      module Config
        module UI
          class << self
            UI_CONFIGMAP = 'deltafi-ui-config'

            def config
              ui_config = DF.k8s_client.api('v1')
                            .resource('configmaps', namespace: DF::Common::K8S_NAMESPACE)
                            .get(UI_CONFIGMAP).data.config
              YAML.safe_load(ui_config)
            end
          end
        end
      end
    end
  end
end
