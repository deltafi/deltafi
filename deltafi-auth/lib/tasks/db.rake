# frozen_string_literal: true

$LOAD_PATH.unshift File.expand_path(File.join(File.dirname(__FILE__), '../../models'))

require 'sequel'
require 'logger'

@db_location = File.join(ENV['DATA_DIR'] || 'db', 'auth.sqlite3')
@db = Sequel.connect("sqlite://#{@db_location}")
@logger = Logger.new($stdout)

namespace :db do
  desc 'Migrate database'
  task :migrate do
    @logger.info "Migrating database located at #{@db_location}"
    Sequel.extension :migration
    Sequel::Migrator.run(@db, 'db/migrations')
  end

  desc 'Seed database'
  task :seed do
    @logger.info "Seeding database located at #{@db_location}"

    require 'user'
    require 'role'

    if Role.count.zero?
      @logger.info 'Creating default roles'
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
                 PluginImageRepoView
                 PluginsView
                 SnapshotRead
                 StatusView
                 SystemPropertiesRead
                 UIAccess
                 VersionsView
               ]).save
    end

    if User.count.zero?
      @logger.info 'Creating default admin user'
      User.new(name: 'Admin', username: 'admin', role_ids: [1]).save
    end
  end
end
