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

  REDIS_RECONNECT_ATTEMPTS = 1_000_000_000
  REDIS_RETRY_COUNT = 30
  BASE_URL = ENV['CORE_URL'] || 'http://deltafi-core-service'
  DELTAFI_MODE = ENV['DELTAFI_MODE'] || 'CLUSTER'

  def self.k8s_client
    debug "#{__method__} called from #{caller(1..1).first}"
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

  def self.clickhouse_config
    {
      host: ENV['CLICKHOUSE_HOST'] || 'deltafi-clickhouse',
      port: ENV['CLICKHOUSE_PORT'] || '8123',
      database: ENV['CLICKHOUSE_DATABASE'] || 'deltafi',
      user: ENV['CLICKHOUSE_USER'] || 'default',
      password: ENV['CLICKHOUSE_PASSWORD'] || 'deltafi'
    }
  end

  def self.clickhouse_client
    info 'Establishing connection to clickhouse'
    conf = ClickHouse::Config.new do |config|
      config.logger = logger
      # config.database = database
      config.timeout = 60
      config.open_timeout = 3
      config.ssl_verify = false
      # set to true to symbolize keys for SELECT and INSERT statements (type casting)
      config.symbolize_keys = true
      config.headers = {}

      # or provide connection options separately
      config.scheme = 'http'
      config.host = clickhouse_config[:host]
      config.port = clickhouse_config[:port]

      config.username = clickhouse_config[:user]
      config.password = clickhouse_config[:password]

      # if you want to add settings to all queries
      # config.global_params = { mutations_sync: 1 }

      # choose a ruby JSON parser (default one)
      # config.json_parser = ClickHouse::Middleware::ParseJson
      # or Oj parser
      # config.json_parser = ClickHouse::Middleware::ParseJsonOj

      # JSON.dump (default one)
      # config.json_serializer = ClickHouse::Serializer::JsonSerializer
      # or Oj.dump
      # config.json_serializer = ClickHouse::Serializer::JsonOjSerializer
    end

    # Connect and create database if it does not exist, then return connection to database
    ClickHouse::Connection.new(conf).create_database(clickhouse_config[:database], if_not_exists: true, engine: nil, cluster: nil)
    conf.database = clickhouse_config[:database]
    ClickHouse::Connection.new(conf)
  rescue StandardError => e
    error e.message
    warning 'Will retry connection in 10 seconds'
    sleep 10
    retry
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

  def self.redis_client
    debug "#{__method__} called from #{caller(1..1).first}"
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
