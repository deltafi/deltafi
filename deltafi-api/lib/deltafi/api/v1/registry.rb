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
          CATALOG_URL = "http://#{DOCKER_REGISTRY}/v2/_catalog"
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
        end
      end
    end
  end
end
