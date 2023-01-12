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

class ApiServer < Sinatra::Base
  helpers do
    def audit(message)
      user = request.env['HTTP_X_USER_NAME'] || 'system'
      audit_message = {
        timestamp: Time.now.utc.strftime('%FT%T.%3NZ'),
        loggerName: 'AUDIT',
        level: 'INFO',
        user: user,
        message: message
      }.to_json
      puts audit_message
    end

    def auth_mode
      ENV.fetch('AUTH_MODE')
    rescue KeyError
      raise 'AUTH_MODE environment variable must be set!'
    end

    def authorize!(permission)
      user_permissions = request.env['HTTP_X_USER_PERMISSIONS']&.split(',') || []

      raise Deltafi::AuthError.new(permission: permission) if ([permission.to_s, 'Admin'] & user_permissions).empty?
    end

    def read_json_body
      request.body.rewind
      JSON.parse(request.body.read, symbolize_names: true)
    rescue StandardError => e
      raise "Unable to parse request body as JSON. #{e.message}"
    end

    def error(error_message, status_code = 500)
      status status_code
      { error: error_message, timestamp: Time.now }.to_json
    end
  end
end
