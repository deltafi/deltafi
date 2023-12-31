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

require 'httparty'

module Deltafi
  module Auth
    extend self

    AUTH_URL = ENV['DELTAFI_AUTH_URL'] || 'http://deltafi-auth-service'

    def self.proxy
      lambda do
        path = request.path.gsub('/api/v1', '')
        url = File.join(AUTH_URL, path)
        options = {
          headers: {
            'X-User-Name': request.env['HTTP_X_USER_NAME'],
            'X-User-Permissions': request.env['HTTP_X_USER_PERMISSIONS']
          }
        }
        options[:body] = request.body.read if %w[POST PUT].include?(request.request_method)
        response = HTTParty.send(request.request_method.downcase, url, options)
        status response.code
        return response.body
      end
    end
  end
end
