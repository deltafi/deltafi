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

require "sinatra/base"
require 'yaml'
require 'k8s-ruby'
require 'sinatra/quiet_logger'

class Auth < Sinatra::Base
  NAMESPACE = ENV['NAMESPACE']
  SECRET = ENV['SECRET']
  SUBDOMAIN_REGEX = %r{^https://([a-zA-Z0-9_]*)\.}.freeze

  configure :production, :development, :test do
    enable :logging
    set :quiet_logger_prefixes, %w(probe)
  end

  register Sinatra::QuietLogger

  get('/probe') {}

  get '/auth' do
    logger.debug request.env

    client_dn = request.env['HTTP_SSL_CLIENT_SUBJECT_DN']
    original_url = request.env['HTTP_X_ORIGINAL_URL']

    if [client_dn, original_url].include?(nil)
      error = "Missing required headers"
      logger.error error
      halt 400, error
    end

    begin
      unless self.class.allowed?(client_dn, original_url)
        error = "Unauthorized request: \"#{client_dn}\" -> #{original_url}"
        logger.warn error
        halt 401, error
      end
    rescue StandardError => e
      logger.error e.message
      halt 500, e.message
    end
  end

  class << self
    def secret_yaml
      client = K8s::Client.in_cluster_config
      base64 = client.api('v1').resource('secrets', namespace: NAMESPACE).get(SECRET).data.allowed
      raise "Secret missing 'allowed' key" if base64.nil?
      Base64.strict_decode64(base64)
    end

    def allowed?(client_dn, original_url)
      subdomain = SUBDOMAIN_REGEX.match(original_url).captures[0]
      allowed = YAML.safe_load(secret_yaml)
      allowed_subdomain = allowed[subdomain] || []
      allowed_all = allowed['all'] || []
      (allowed_subdomain | allowed_all).include?(client_dn)
    end
  end

  run! if __FILE__ == $0
end
