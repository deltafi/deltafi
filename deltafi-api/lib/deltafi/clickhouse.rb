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

require 'deltafi/logger'

module Deltafi
  module Clickhouse
    extend Deltafi::Logger
    logger.level = ::Logger::WARN

    def self.config
      {
        host: ENV['CLICKHOUSE_HOST'] || 'deltafi-clickhouse',
        port: ENV['CLICKHOUSE_PORT'] || '8123',
        database: ENV['CLICKHOUSE_DATABASE'] || 'deltafi',
        user: ENV['CLICKHOUSE_USER'] || 'default',
        password: ENV['CLICKHOUSE_PASSWORD'] || 'deltafi'
      }
    end

    def self.client
      info 'Establishing connection to clickhouse'
      conf = ClickHouse::Config.new do |c|
        c.logger = Clickhouse.logger
        # config.database = database
        c.timeout = 60
        c.open_timeout = 3
        c.ssl_verify = false
        # set to true to symbolize keys for SELECT and INSERT statements (type casting)
        c.symbolize_keys = true
        c.headers = {}

        # or provide connection options separately
        c.scheme = 'http'
        c.host = config[:host]
        c.port = config[:port]

        c.username = config[:user]
        c.password = config[:password]

        # if you want to add settings to all queries
        # c.global_params = { mutations_sync: 1 }

        # choose a ruby JSON parser (default one)
        # c.json_parser = ClickHouse::Middleware::ParseJson
        # or Oj parser
        # c.json_parser = ClickHouse::Middleware::ParseJsonOj

        # JSON.dump (default one)
        # c.json_serializer = ClickHouse::Serializer::JsonSerializer
        # or Oj.dump
        # c.json_serializer = ClickHouse::Serializer::JsonOjSerializer
      end

      # Connect and create database if it does not exist, then return connection to database
      ClickHouse::Connection.new(conf).create_database(config[:database], if_not_exists: true, engine: nil, cluster: nil)
      conf.database = config[:database]
      ClickHouse::Connection.new(conf)
    rescue StandardError => e
      error e.message
      warning 'Will retry connection in 10 seconds'
      sleep 10
      retry
    end
  end
end
