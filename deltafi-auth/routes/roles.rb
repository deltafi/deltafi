#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
  get '/roles' do
    authorize! :RoleRead

    Role.all.map(&:to_api).to_json
  end

  get '/roles/:id' do
    authorize! :RoleRead

    id = params['id'].to_i
    role = Role[id]

    if role.nil?
      status 404
      return error("Role with ID #{id} not found.")
    end

    role.to_api.to_json
  end

  post '/roles' do
    authorize! :RoleCreate

    role = Role.new(read_body)

    unless role.valid?
      status 400
      return { validation_errors: role.errors }.to_json
    end

    role.save

    role.to_api.to_json
  end

  put '/roles/:id' do
    authorize! :RoleUpdate

    id = params['id'].to_i
    role = Role[id]

    if role.nil?
      status 404
      return error("Role with ID #{id} not found.")
    end

    role.update(read_body)

    unless role.valid?
      status 400
      return { validation_errors: role.errors }.to_json
    end

    role.save
    audit("updated role #{role.name}")

    Role[id].to_api.to_json
  end

  delete '/roles/:id' do
    authorize! :RoleDelete

    id = params['id'].to_i
    if id == 1
      status 403
      return error('Unable to delete the admin role.')
    end

    role = Role[id]

    if role.nil?
      status 404
      return error("Role with ID #{id} not found.")
    end

    role.destroy
    audit("deleted role #{role.name}")

    return role.to_api.to_json
  end
end
