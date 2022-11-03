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
  helpers do
    def basic_auth!
      unless basic_authenticated?
        headers['WWW-Authenticate'] = 'Basic realm="Restricted"'
        deny("401 Unauthorized: '#{@auth.credentials[0]}' -> '#{@original_url}'", 401)
      end

      return if authorized?

      headers['WWW-Authenticate'] = 'Basic realm="Restricted"'
      deny("403 Forbidden: '#{@user.username}' -> '#{@original_url}'", 403)
    end

    def cert_auth!
      deny("401 Unauthorized: '#{@formatted_client_dn}' -> '#{@original_url}'", 401) unless cert_authenticated?
      deny("403 Forbidden: '#{@formatted_client_dn}' -> '#{@original_url}'", 403) unless authorized?
    end

    def authorized?
      @user.can_access?(@original_url)
    end

    def basic_authenticated?
      @auth ||= Rack::Auth::Basic::Request.new(request.env)
      unless @auth.provided? && @auth.basic? && @auth.credentials
        headers['WWW-Authenticate'] = 'Basic realm="Restricted"'
        deny('401 Unauthorized', 401)
      end

      @user = User[{ username: @auth.credentials[0] }]
      return false if @user.nil?

      @user.password == @auth.credentials[1]
    end

    def cert_authenticated?
      begin
        @formatted_client_dn = OpenSSL::X509::Name.parse(@client_dn).to_s(OpenSSL::X509::Name::COMPAT)
      rescue StandardError
        deny("401 Unauthorized: Invalid Distinguished Name: #{@client_dn}", 401)
      end

      @user = User[{ dn: @formatted_client_dn }]
      !@user.nil?
    end

    def verify_headers(headers)
      headers.each do |header|
        deny("400 Bad Request: Missing required header: #{header}", 400) unless request.env.key?("HTTP_#{header}")
      end
    end

    def read_body
      request.body.rewind
      JSON.parse(request.body.read, symbolize_names: true)
    end

    def deny(message, code)
      logger.warn(message)
      halt code, message
    end

    def error(e)
      { error: e }.to_json
    end

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

    def authorize!(permission)
      user_permissions = request.env['HTTP_X_USER_PERMISSIONS']&.split(',') || []

      raise Deltafi::AuthError.new(permission: permission) if ([permission.to_s, 'Admin'] & user_permissions).empty?
    end
  end
end
