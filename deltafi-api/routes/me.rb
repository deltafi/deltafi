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
      return { name: 'Anonymous' }.to_json if auth_mode == 'disabled'

      id = request.env['HTTP_X_USER_ID'].to_i
      response = DF::Auth.get_user(id)
      status response.code
      response.body
    end
  end
end
