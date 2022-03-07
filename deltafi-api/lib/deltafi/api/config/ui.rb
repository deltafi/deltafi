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

            return {
              domain: ui_config.domain,
              title: ui_config.title,
              useUTC: ui_config.useUTC == 'true',
              securityBanner: YAML.safe_load(ui_config.securityBanner) || { enabled: false },
              externalLinks: YAML.safe_load(ui_config.externalLinks) || [],
              deltaFileLinks: YAML.safe_load(ui_config.deltaFileLinks) || []
            }
          end
        end
      end
    end
  end
end
