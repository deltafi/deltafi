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

module Deltafi
  module API
    module V1
      module Versions
        class << self
          def apps
            DF.cluster_mode? ? k8s_apps : standalone_apps
          end

          def k8s_apps
            pods = DF.k8s_client.api('v1')
                     .resource('pods', namespace: DF::Common::K8S_NAMESPACE)
                     .list(fieldSelector: { 'status.phase' => 'Running' })
            pods.map do |pod|
              app = pod.metadata.labels.app || pod.metadata.labels['app.kubernetes.io/name']
              pod.spec.containers.map do |container|
                image = container.image.split(':')
                image_tag = image.pop
                image_name = image.join(':')
                {
                  app: app,
                  container: container.name,
                  image: {
                    name: image_name,
                    tag: image_tag
                  },
                  group: pod.metadata.labels.group
                }
              end
            end.flatten.uniq.sort_by { |p| p[:app] }
          end

          def standalone_apps
            DF.core_rest_get("app/versions")
          end
        end
      end
    end
  end
end
