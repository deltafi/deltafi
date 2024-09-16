#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
  get '/cert-auth/?' do
    content_type 'text/plain'

    verify_headers(%w[SSL_CLIENT_SUBJECT_DN X_ORIGINAL_URL])
    @client_dn = request.env['HTTP_SSL_CLIENT_SUBJECT_DN']
    @original_url = request.env['HTTP_X_ORIGINAL_URL']

    cert_auth!

    response.headers['X-User-ID'] = @user.dn
    response.headers['X-User-Name'] = @user.common_name
    response.headers['X-User-Permissions'] = @user.permissions_csv
    response.headers['X-Metrics-Role'] = @user.metrics_role
    logger.info "Authorized: '#{@user.dn}' -> '#{@original_url}'"
    return
  end
end
