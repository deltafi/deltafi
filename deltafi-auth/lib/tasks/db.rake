# frozen_string_literal: true

require 'sequel'
require 'logger'

namespace :db do
  desc 'Migrate database'
  task :migrate do
    logger = Logger.new($stdout)
    db_location = File.join(ENV['DATA_DIR'] || 'db', 'auth.sqlite3')
    logger.info "Migrating database located at #{db_location}"
    db = Sequel.connect("sqlite://#{db_location}")
    Sequel.extension :migration
    Sequel::Migrator.run(db, 'db/migrations')
  end
end
