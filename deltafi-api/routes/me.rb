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
  register Sinatra::Namespace

  namespace '/api/v1' do
    get '/me' do
      user_permissions = request.env['HTTP_X_USER_PERMISSIONS']&.split(',') || []
      user_name = request.env['HTTP_X_USER_NAME'] || 'Unknown'
      user_id = (request.env['HTTP_X_USER_ID'] || -1).to_i

      return { id: user_id, name: user_name, permissions: user_permissions }.to_json if auth_mode == 'disabled'

      response = DF::Auth.get_user(user_id)
      status response.code
      JSON.parse(response.body).to_json
    end
  end
end
