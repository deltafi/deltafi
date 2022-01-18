# frozen_string_literal: true

module Deltafi
  module API
    module Config
      module UI
        class << self
          UI_CONFIGMAP = 'deltafi-ui-config'

          def config
            ui_config = Deltafi::API.k8s_client.api('v1')
                                    .resource('configmaps', namespace: NAMESPACE)
                                    .get(UI_CONFIGMAP).data
            links = YAML.safe_load(ui_config.links)
            security_banner = YAML.safe_load(ui_config.securityBanner) || { enabled: false }

            {
              domain: ui_config.domain,
              title: ui_config.title,
              securityBanner: security_banner,
              dashboard: {
                links: links
              }
            }
          end
        end
      end
    end
  end
end
