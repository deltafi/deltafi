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

require 'httparty'
require 'k8s-ruby'
require 'redis'
require 'deltafi/logger'

module Deltafi
  extend Deltafi::Logger

  REDIS_RECONNECT_ATTEMPTS = 1_000_000_000
  REDIS_RETRY_COUNT = 30
  @@system_properties = nil

  def self.k8s_client
    ENV['RUNNING_IN_CLUSTER'].nil? ? K8s::Client.config(K8s::Config.load_file(File.expand_path('~/.kube/config'))) : K8s::Client.in_cluster_config
  end

  def self.graphql(query)
    graphql_url = ENV['DELTAFI_GRAPHQL_URL'] ||
                  cached_system_properties['graphql.urls.core'] ||
                  'http://deltafi-core-service/graphql'

    HTTParty.post(graphql_url,
                  body: { query: query }.to_json,
                  headers: { 'Content-Type' => 'application/json' })
  end

  def self.redis_client
    redis_password = ENV['REDIS_PASSWORD']
    redis_url = ENV['REDIS_URL'] ||
                cached_system_properties['redis.url']&.gsub(/^http/, 'redis') ||
                'redis://deltafi-redis-master:6379'

    retries = 0
    begin
      Redis.new(
        url: redis_url,
        password: redis_password,
        reconnect_attempts: REDIS_RECONNECT_ATTEMPTS,
        reconnect_delay: 1,
        reconnect_delay_max: 5
      )
    rescue Errno::EALREADY => e
      raise e if retries >= REDIS_RETRY_COUNT

      error e.message
      e.backtrace.each { |line| error line }
      sleep 1
      retries += 1
      retry
    end
  end

  def self.cached_system_properties
    if @@system_properties.nil? || @@system_properties.keys.empty?
      system_properties
    else
      @@system_properties
    end
  end

  def self.system_properties
    @@system_properties ||= {}
    return @@system_properties unless running_in_cluster?

    base_url = ENV['DELTAFI_CONFIG_URL'] || 'http://deltafi-core-service'
    config_url = File.join(base_url, 'config/application/default')

    begin
      response = HTTParty.get(config_url,
                              headers: { 'Content-Type' => 'application/json' })

      response.parsed_response['propertySources'].reverse_each do |property_source|
        property_source['source'].each do |name, value|
          @@system_properties[name] = value.to_s.start_with?('$') ? ENV[value.gsub(/[${}]/, '')] : value
        end
      end
    rescue StandardError => e
      puts e
    end

    return @@system_properties
  end

  def self.running_in_cluster?
    ENV['RUNNING_IN_CLUSTER'] == 'true'
  end
end

DF = Deltafi

Dir[File.join(File.dirname(__FILE__), 'deltafi', '*.rb')].each do |f|
  require "deltafi/#{File.basename(f).split('.')[0]}"
end
