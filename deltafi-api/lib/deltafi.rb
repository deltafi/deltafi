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
require 'mongo'
require 'deltafi/logger'

module Deltafi
  extend Deltafi::Logger

  REDIS_RECONNECT_ATTEMPTS = 1_000_000_000
  REDIS_RETRY_COUNT = 30
  BASE_URL = ENV['CORE_URL'] || 'http://deltafi-core-service'
  @@system_properties = nil

  def self.k8s_client
    debug "#{__method__} called from #{caller[0]}"
    ENV['RUNNING_IN_CLUSTER'].nil? ? K8s::Client.config(K8s::Config.load_file(File.expand_path('~/.kube/config'))) : K8s::Client.in_cluster_config
  end

  def self.mongo_config
    {
      host: ENV['MONGO_HOST'] || 'deltafi-mongodb',
      port: ENV['MONGO_PORT'] || '27017',
      database: ENV['MONGO_DATABASE'] || 'deltafi',
      auth_source: ENV['MONGO_AUTH_DATABASE'] || 'deltafi',
      user: ENV['MONGO_USER'] || 'mongouser',
      password: ENV.fetch('MONGO_PASSWORD', nil)
    }
  end

  def self.mongo_client
    debug "#{__method__} called from #{caller[0]}"
    config = mongo_config
    @@mongo_client ||= Mongo::Client.new(["#{config[:host]}:#{config[:post]}"],
                                         database: config[:database],
                                         user: config[:user],
                                         password: config[:password],
                                         auth_source: config[:auth_source])
    @@mongo_client.reconnect if @@mongo_client.cluster.servers.empty?
    @@mongo_client
  end

  def self.graphql(query)
    debug "#{__method__} called from #{caller[0]}"
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

  def self.redis_client
    debug "#{__method__} called from #{caller[0]}"
    redis_password = ENV.fetch('REDIS_PASSWORD', nil)
    redis_url = ENV['REDIS_URL']&.gsub(/^http/, 'redis') || 'redis://deltafi-redis-master:6379'

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
    debug "#{__method__} called from #{caller[0]}"
    if @@system_properties.nil? || @@system_properties.keys.empty?
      system_properties
    else
      @@system_properties
    end
  end

  def self.system_properties
    debug "#{__method__} called from #{caller[0]}"
    @@system_properties ||= {}
    return @@system_properties unless running_in_cluster?

    begin
      debug "Querying mongo for system properties"
      @@system_properties = mongo_client[:deltaFiProperties].find.limit(1).first || {}
    rescue StandardError => e
      puts e
    end

    return @@system_properties
  end

  def self.system_property(dig_path = [], default_value = nil)
    debug "#{__method__} called from #{caller[0]}"
    system_properties.dig(*dig_path) || default_value
  end

  def self.running_in_cluster?
    ENV['RUNNING_IN_CLUSTER'] == 'true'
  end
end

DF = Deltafi

Dir[File.join(File.dirname(__FILE__), 'deltafi', '*.rb')].each do |f|
  require "deltafi/#{File.basename(f).split('.')[0]}"
end
