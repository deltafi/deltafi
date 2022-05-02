#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
