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

require 'English'
require 'deltafi/logger'
require 'httparty'

module Deltafi
  module API
    module V1
      module Registry
        extend Deltafi::Logger
        class << self
          DOCKER_REGISTRY = 'deltafi-docker-registry:5000'
          REGISTRY_URL = "http://#{DOCKER_REGISTRY}/v2"
          CATALOG_URL = "#{REGISTRY_URL}/_catalog"
          DOCKER_USERNAME = 'deltafi' # FIXME
          DOCKER_PASSWORD = 'password' # FIXME
          BASIC_AUTH = { username: DOCKER_USERNAME, password: DOCKER_PASSWORD }.freeze

          def catalog
            HTTParty.get(CATALOG_URL, basic_auth: BASIC_AUTH).to_s
          end

          def upload(tarball:, image_name:)
            result = `
              skopeo copy --insecure-policy --dest-tls-verify=false --dest-creds #{DOCKER_USERNAME}:#{DOCKER_PASSWORD} \
              docker-archive:#{tarball} \
              docker://#{DOCKER_REGISTRY}/#{image_name}
            `

            logger.info result
            raise "Unable to upload image #{image_name} (#{$CHILD_STATUS.exitstatus})\n#{result}" unless $CHILD_STATUS.success?

            "Successful upload of image #{image_name}"
          end

          def list
            catalog_response = HTTParty.get(CATALOG_URL, basic_auth: BASIC_AUTH)
            repositories = JSON.parse(catalog_response.to_s, symbolize_names: true)[:repositories]

            result = []

            repositories.each do |repository|
              response = HTTParty.get("#{REGISTRY_URL}/#{repository}/tags/list", basic_auth: BASIC_AUTH)
              tags = JSON.parse(response.to_s, symbolize_names: true)[:tags]
              result << { name: repository, tags: tags.sort } if tags.present?
            end
            result
          end

          def delete(image_name:, image_tag:)
            response = HTTParty.get("#{REGISTRY_URL}/#{image_name}/manifests/#{image_tag}",
                                    headers: { 'Accept' => 'application/vnd.docker.distribution.manifest.v2+json' },
                                    basic_auth: BASIC_AUTH)

            unless response.success?
              puts response
              puts response.headers
              raise "Unable to locate image #{image_name}:#{image_tag} for deletion"
            end

            raise "Unable to locate digest for image #{image_name}:#{image_tag} for deletion" unless response&.headers && response.headers['docker-content-digest']

            digest = response.headers['docker-content-digest']
            response = HTTParty.delete("#{REGISTRY_URL}/#{image_name}/manifests/#{digest}", basic_auth: BASIC_AUTH)

            raise "Unable to delete image #{image_name}:#{image_tag} [#{response.code}]: #{response.body}" unless response.success?

            "Deletion of image #{image_name}:#{image_tag} successful"
          end

          def add(image_name:, new_name:)
            new_name ||= image_name

            result = `
              skopeo copy --insecure-policy --dest-tls-verify=false --dest-creds #{DOCKER_USERNAME}:#{DOCKER_PASSWORD} \
              docker://#{image_name} \
              docker://#{DOCKER_REGISTRY}/#{new_name}
            `

            logger.info result
            raise "Unable to upload image #{image_name} (#{$CHILD_STATUS.exitstatus})\n#{result}" unless $CHILD_STATUS.success?

            "Successful add of image #{new_name || image_name} #{"from #{image_name}" if image_name != new_name}"
          end
        end
      end
    end
  end
end
