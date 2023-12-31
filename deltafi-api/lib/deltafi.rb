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
require 'k8s-ruby'
require 'redis'
require 'deltafi/logger'

module Deltafi
  extend Deltafi::Logger

  REDIS_RECONNECT_ATTEMPTS = 5
  REDIS_PASSWORD = ENV.fetch('REDIS_PASSWORD', nil)
  REDIS_URL = ENV['REDIS_URL']&.gsub(/^http/, 'redis') || 'redis://deltafi-redis-master:6379'
  BASE_URL = ENV['CORE_URL'] || 'http://deltafi-core-service'
  DELTAFI_MODE = ENV['DELTAFI_MODE'] || 'CLUSTER'

  def self.k8s_client
    debug "#{__method__} called from #{caller(1..1).first}"

    ENV['RUNNING_IN_CLUSTER'].nil? ? K8s::Client.config(K8s::Config.load_file(File.expand_path('~/.kube/config'))) : K8s::Client.in_cluster_config
  end

  def self.configure_mongoid
    return if defined?(@@mongo_configured) && @@mongo_configured

    host = ENV['MONGO_HOST'] || 'deltafi-mongodb'
    port = ENV['MONGO_PORT'] || '27017'
    database = ENV['MONGO_DATABASE'] || 'deltafi'
    user = ENV['MONGO_USER'] || 'mongouser'
    password = ENV.fetch('MONGO_PASSWORD', nil)
    auth_source = ENV['MONGO_AUTH_DATABASE'] || 'deltafi'

    Mongoid.load_configuration(
      {
        clients: {
          default: {
            hosts: ["#{host}:#{port}"],
            database: database,
            options: {
              user: user,
              password: password,
              auth_source: auth_source
            }
          }
        }
      }
    )

    Mongoid.raise_not_found_error = false

    @@mongo_configured = true
  end

  def self.graphql(query)
    debug "#{__method__} called from #{caller(1..1).first}"
    graphql_url = File.join(BASE_URL, 'graphql')

    response = HTTParty.post(graphql_url,
                             body: { query: query }.to_json,
                             headers: {
                               'Content-Type' => 'application/json',
                               'X-User-Permissions' => 'Admin',
                               'X-User-Name' => 'Admin'
                             })

    raise "#{response.code} error from core: #{response.message}" unless response.success?

    errors = (response.parsed_response['errors'] || []).map { |e| e['message'] }
    raise "Errors reported from core:\n#{errors.join("\n")}" unless errors.empty?

    response
  end

  def self.core_rest_get(endpoint)
    debug "#{__method__} called from #{caller(1..1).first}"
    core_url = File.join(BASE_URL, endpoint)

    response = HTTParty.get(core_url,
                            headers: {
                              'Content-Type' => 'application/json',
                              'X-User-Permissions' => 'Admin',
                              'X-User-Name' => 'Admin'
                            })

    raise "#{response.code} error from core: #{response.message}" unless response.success?

    JSON.parse(response.body, symbolize_names: true)
  end

  def self.redis
    return @@redis if defined?(@@redis) && @@redis

    debug "#{__method__} called from #{caller(1..1).first}"

    @@redis = ConnectionPool::Wrapper.new(size: 10, timeout: 2) do
      Redis.new(
        url: REDIS_URL,
        password: REDIS_PASSWORD,
        reconnect_attempts: REDIS_RECONNECT_ATTEMPTS
      )
    end
  end

  def self.running_in_cluster?
    ENV['RUNNING_IN_CLUSTER'] == 'true'
  end

  def self.cluster_mode?
    DELTAFI_MODE == 'CLUSTER'
  end
end

DF = Deltafi

Dir[File.join(File.dirname(__FILE__), 'deltafi', '*.rb')].each do |f|
  require "deltafi/#{File.basename(f).split('.')[0]}"
end
