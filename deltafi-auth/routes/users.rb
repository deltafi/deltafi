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

class AuthApi < Sinatra::Application
  get '/users/?' do
    authorize! :UserRead

    User.all.map(&:to_api).to_json
  end

  get '/users/:id' do
    authorize! :UserRead

    id = params['id'].to_i
    user = User[id]

    if user.nil?
      status 404
      return error("User with ID #{id} not found.")
    end

    user.to_api.to_json
  end

  post '/users' do
    authorize! :UserCreate

    user = User.new(read_body)

    unless user.valid?
      status 400
      return { validation_errors: user.errors }.to_json
    end

    user.save
    audit("created user #{user.common_name || user.username}")

    return user.to_api.to_json
  end

  put '/users/:id' do
    authorize! :UserUpdate

    id = params['id'].to_i
    user = User[id]

    if user.nil?
      status 404
      return error("User with ID #{id} not found.")
    end

    user.update(read_body)

    unless user.valid?
      status 400
      return { validation_errors: user.errors }.to_json
    end

    user.save
    audit("updated user #{user.common_name || user.username}")

    return User[id].to_api.to_json
  end

  delete '/users/:id' do
    authorize! :UserDelete

    id = params['id'].to_i
    if id == 1
      status 403
      return error('Unable to delete admin user.')
    end

    user = User[id]

    if user.nil?
      status 404
      return error("User with ID #{id} not found.")
    end

    user.destroy
    audit("deleted user #{user.common_name || user.username}")

    return user.to_api.to_json
  end
end
