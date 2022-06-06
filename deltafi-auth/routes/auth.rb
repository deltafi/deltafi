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

class AuthApi < Sinatra::Application
  REQUIRED_HEADERS=['SSL_CLIENT_SUBJECT_DN', 'X_ORIGINAL_URL']

  get "/auth/?" do
    content_type 'text/plain'

    REQUIRED_HEADERS.each do |header|
      deny("400 Bad Request: Missing required header: #{header}", 400) unless request.env.has_key?("HTTP_#{header}")
    end

    client_dn = request.env['HTTP_SSL_CLIENT_SUBJECT_DN']
    original_url = request.env['HTTP_X_ORIGINAL_URL']
    domain = original_url.split('/')[2].split(':')[0]

    begin
      formatted_client_dn = OpenSSL::X509::Name.parse(client_dn).to_s(OpenSSL::X509::Name::COMPAT)
    rescue StandardError
      deny("401 Unauthorized: Invalid Distinguished Name: #{client_dn}", 401)
    end

    user = User[{dn: formatted_client_dn}]
    deny("401 Unauthorized: '#{formatted_client_dn}' -> '#{original_url}'", 401) unless user
    deny("403 Forbidden: '#{formatted_client_dn}' -> '#{original_url}'", 403) unless user.can_access?(domain)

    response.headers['X-User-ID'] = user.id.to_s
    logger.info "Authorized: '#{formatted_client_dn}' -> '#{original_url}'"
    return
  end

  def deny(message, code)
    logger.warn(message)
    halt code, message
  end

  def error(message, code = 500)
    logger.error message
    halt code, message
  end
end