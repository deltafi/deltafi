# frozen_string_literal: true

module Deltafi
  module API
    module V1
      module Versions
        class << self
          def apps
            pods = DF.k8s_client.api('v1')
                     .resource('pods', namespace: DF::Common::K8S_NAMESPACE)
                     .list(fieldSelector: { 'status.phase' => 'Running' })
            pods.map do |pod|
              app = pod.metadata.labels.app || pod.metadata.labels['app.kubernetes.io/name']
              pod.spec.containers.map do |container|
                image = container.image.split(':')
                {
                  app: app,
                  container: container.name,
                  image: {
                    name: image[0],
                    tag: image[1]
                  },
                  group: pod.metadata.labels.group
                }
              end
            end.flatten.uniq.sort_by { |p| p[:app] }
          end
        end
      end
    end
  end
end
