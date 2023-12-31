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

          HTTPARTY_PARAMS = { verify: false, basic_auth: BASIC_AUTH }.freeze

          def catalog
            HTTParty.get(CATALOG_URL, HTTPARTY_PARAMS).to_s
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
            catalog_response = HTTParty.get(CATALOG_URL, HTTPARTY_PARAMS)
            repositories = JSON.parse(catalog_response.to_s, symbolize_names: true)[:repositories]

            result = []

            repositories.each do |repository|
              response = HTTParty.get("#{REGISTRY_URL}/#{repository}/tags/list", HTTPARTY_PARAMS)
              tags = JSON.parse(response.to_s, symbolize_names: true)[:tags]
              result << { name: repository, tags: tags.sort } if tags.present?
            end
            result
          end

          def repository_tags(repository_name)
            response = HTTParty.get("#{REGISTRY_URL}/#{repository_name}/tags/list", HTTPARTY_PARAMS)
            json = JSON.parse(response.to_s, symbolize_names: true)
            raise "#{json[:errors].first[:message]} - #{json[:errors].first[:detail]}" if json[:errors]

            json[:tags] || []
          end

          def delete_repository(repository_name:)
            tags = repository_tags(repository_name)
            raise "No tags to delete in repository: #{repository_name}" if tags.nil? || tags.empty?

            tags.each do |tag|
              delete image_name: repository_name, image_tag: tag
            end

            "Successfully deleted repository #{repository_name} #{tags}"
          end

          def delete(image_name:, image_tag:)
            response = HTTParty.get("#{REGISTRY_URL}/#{image_name}/manifests/#{image_tag}",
                                    HTTPARTY_PARAMS.merge(
                                      { headers: { 'Accept' => 'application/vnd.docker.distribution.manifest.v2+json' } }
                                    ))

            unless response.success?
              puts response
              puts response.headers
              raise "Unable to locate image #{image_name}:#{image_tag} for deletion"
            end

            raise "Unable to locate digest for image #{image_name}:#{image_tag} for deletion" unless response&.headers && response.headers['docker-content-digest']

            digest = response.headers['docker-content-digest']
            response = HTTParty.delete("#{REGISTRY_URL}/#{image_name}/manifests/#{digest}", HTTPARTY_PARAMS)

            raise "Unable to delete image #{image_name}:#{image_tag} [#{response.code}]: #{response.body}" unless response.success?

            "Deletion of image #{image_name}:#{image_tag} successful"
          end

          def replace(image_name:, new_name:)
            image = (new_name || image_name).split(':')
            repository_name = image.first
            new_tag = image.last

            begin
              delete_tags = repository_tags(repository_name)
            rescue StandardError
              delete_tags = []
            end

            delete_tags.delete(new_tag)
            add image_name: image_name, new_name: new_name
            delete_tags&.each do |tag|
              delete image_name: repository_name, image_tag: tag
            end

            "Successfully replaced repository #{repository_name} with #{new_name || image_name}"
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
