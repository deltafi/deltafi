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

require 'sinatra'
require 'sinatra/quiet_logger'
require 'sequel'
require 'json'
require 'openssl'

class AuthApi < Sinatra::Application
  set :show_exceptions, :after_handler
  set :protection, except: [:json_csrf]

  configure :production, :development, :test do
    enable :logging
    set :quiet_logger_prefixes, %w[probe]
  end

  register Sinatra::QuietLogger

  before do
    content_type 'application/json'
  end

  get('/probe') {}

  db_location = File.join(ENV['DATA_DIR'] || 'db', 'auth.sqlite3')
  db = ENV['RACK_ENV'] == 'test' ? Sequel.sqlite : Sequel.connect("sqlite://#{db_location}")
  Sequel.extension :migration
  Sequel::Migrator.run(db, 'db/migrations')

  %w[helpers models routes].each { |dir| Dir.glob("./#{dir}/*.rb").sort.each(&method(:require)) }

  User.new(name: 'Admin', username: 'admin', domains: '*').save if User.count.zero?

  error JSON::ParserError do
    { error: 'Error parsing JSON' }.to_json
  end

  error StandardError do
    { error: env['sinatra.error'].message }.to_json
  end

  run! if app_file == $PROGRAM_NAME
end
