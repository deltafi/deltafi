# frozen_string_literal: true

module Deltafi
  module API
    module Versions
      class << self
        def apps
          pods = Deltafi::API.k8s_client.api('v1').resource('pods', namespace: NAMESPACE).list
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
                }
              }
            end
          end.flatten.sort_by { |p| p[:app] }
        end
      end
    end
  end
end
