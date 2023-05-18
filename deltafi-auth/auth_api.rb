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

$LOAD_PATH.unshift File.expand_path(File.join(File.dirname(__FILE__), 'lib'))

require 'sinatra'
require 'sinatra/quiet_logger'
require 'sequel'
require 'json'
require 'openssl'
require 'yaml'

class AuthApi < Sinatra::Application
  DOMAIN = ENV['DOMAIN']
  raise 'DOMAIN environment variable must be set.' if DOMAIN.nil?

  DOMAIN_PERMISSIONS = {
    "graphite.#{DOMAIN}" => 'MetricsView',
    "ingress.#{DOMAIN}" => 'DeltaFileIngress',
    "k8s.#{DOMAIN}" => 'Admin',
    "metrics.#{DOMAIN}" => 'MetricsView',
    DOMAIN => 'UIAccess'
  }.freeze

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
  if ENV['RACK_ENV'] == 'test'
    db = Sequel.sqlite
    Sequel.extension :migration
    Sequel::Migrator.run(db, 'db/migrations')
  else
    Sequel.connect("sqlite://#{db_location}")
  end

  %w[lib helpers models routes].each { |dir| Dir.glob("./#{dir}/*.rb").sort.each(&method(:require)) }

  # Default users and roles
  if Role.count.zero?
    Role.new(name: 'Admin', permissions: %w[Admin]).save
    Role.new(name: 'Ingress Only', permissions: %w[DeltaFileIngress]).save
    Role.new(name: 'Read Only', permissions: %w[
               DashboardView
               DeletePolicyRead
               DeltaFileContentView
               DeltaFileMetadataView
               EventRead
               FlowView
               IngressRoutingRuleRead
               MetricsView
               PluginCustomizationConfigView
               PluginImageRepoView
               PluginsView
               SnapshotRead
               StatusView
               SystemPropertiesRead
               UIAccess
               VersionsView
             ]).save
  end

  User.new(name: 'Admin', username: 'admin', role_ids: [1]).save if User.count.zero?

  error JSON::ParserError do
    { error: 'Error parsing JSON' }.to_json
  end

  error StandardError do
    build_error_response(env['sinatra.error'].message)
  end

  error Sinatra::NotFound do
    build_error_response('404 Not Found')
  end

  error Deltafi::AuthError do
    permission = env['sinatra.error'].permission
    req = "#{request.request_method} #{request.env['PATH_INFO']}"
    audit("request '#{req}' was denied due to missing permission '#{permission}'")
    build_error_response(env['sinatra.error'].message)
  end

  def build_response(object)
    object[:timestamp] = Time.now
    object.to_json
  end

  def build_error_response(message)
    build_response({ error: message })
  end

  run! if app_file == $PROGRAM_NAME
end
