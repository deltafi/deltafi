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

$LOAD_PATH.unshift File.expand_path(File.join(File.dirname(__FILE__), '../../models'))

require 'deltafi'
require 'timers'
require 'fileutils'
require 'deltafile'
require 'click_house'

module Deltafi
  class ClickhouseETL
    attr_reader :clickhouse, :last_update, :lag, :interval, :limit

    include Deltafi::Logger

    DELTAFILE_TABLE_NAME = 'deltafiles'

    def initialize(interval: 10.seconds, lag: 30.seconds, limit: 1000, delete_ttl: '14 DAY')
      @interval = interval.to_i.seconds # Interval in seconds for each ETL sync
      @lag = lag.to_i.seconds           # Maximum number of records to batch from MongoDB to Clickhouse at once
      @limit = limit.to_i               # Number of seconds before now for deltafile query to allow all mongo insertions to settle out
      @clickhouse = DF.clickhouse_client
      @deltafile_ttl = "timestamp + INTERVAL #{delete_ttl} DELETE"

      create_clickhouse_table
      # initialize last update from last clickhouse entry
      # Note: Clickhouse has 1 second granularity timestamp, so there will be the possibility of up to 1 second of data overlap.  The small number of duplicates will be taken
      # care of by Clickhouse merge engine
      @last_update = (@clickhouse.select_value("SELECT update_timestamp FROM #{DELTAFILE_TABLE_NAME} ORDER BY update_timestamp DESC LIMIT 1") || '1985-10-26 01:21:00'.to_datetime) - 1.second

      info "Last update detected on #{precise_last_update}"
      Process.setproctitle('Clickhouse ETL')

      info 'Clickhouse ETL initialized'
    end

    def run
      info 'Starting ETL loop'
      begin
        periodic_timer(@interval) do
          sync
        end
      rescue StandardError => e
        error "ETL loop Error: #{e.message}"
        sleep 10
        retry
      end
    end

    private

    def sync
      end_timestamp = Time.now - @lag.seconds
      query = Deltafile.terminal.where(:modified.gt => @last_update).and(:modified.lt => end_timestamp).order(modified: :asc).batch_size(@limit)

      info "Syncing #{query.count} deltafiles since #{precise_last_update}" unless query.empty?
      buffer = []
      last_modified = @last_update
      query.each do |deltafile|
        buffer << extract_row(deltafile)
        last_modified = deltafile.modified
        if buffer.size >= @limit
          clickhouse_insert(buffer)
          @last_update = deltafile.modified
        end
      end
      unless buffer.empty?
        clickhouse_insert buffer
        @last_update = last_modified
      end

      info "Synced to #{precise_last_update}"
    rescue StandardError => e
      error "Unable to complete ETL sync: #{e.message}"
    end

    DELTAFILE_COLUMNS = %i[update_timestamp timestamp flow did files ingressBytes totalBytes errored filtered egressed annotations].freeze
    def extract_row(deltafile)
      [
        deltafile.modified.to_i,
        deltafile.created.to_i,
        deltafile.sourceInfo[:flow],
        deltafile.id,
        1,
        deltafile.ingressBytes,
        deltafile.totalBytes,
        deltafile.stage == 'ERROR' ? 1 : 0,
        deltafile.filtered ? 1 : 0,
        deltafile.egressed ? 1 : 0,
        deltafile.annotations
      ]
    end

    def clickhouse_insert(buffer)
      info "Writing #{buffer.size} deltafiles to clickhouse"
      @clickhouse.insert(DELTAFILE_TABLE_NAME, columns: DELTAFILE_COLUMNS, values: buffer) unless buffer.empty?
      buffer.clear
    end

    def create_clickhouse_table
      @clickhouse.execute <<~SQL
        CREATE TABLE IF NOT EXISTS #{DELTAFILE_TABLE_NAME}
        (
          timestamp DateTime,
          update_timestamp DateTime,
          flow String,
          did String,
          files UInt64,
          ingressBytes UInt64,
          totalBytes UInt64,
          errored UInt64,
          filtered UInt64,
          egressed UInt64,
          annotations Map(String,String)
        )
        ENGINE = ReplacingMergeTree
        ORDER BY (flow, did, timestamp)
        PARTITION BY toYYYYMMDD(timestamp)
        TTL #{@deltafile_ttl}
      SQL

      @clickhouse.execute <<~SQL
        ALTER TABLE #{DELTAFILE_TABLE_NAME} MODIFY TTL #{@deltafile_ttl}
      SQL
    rescue StandardError => e
      error e.message
      sleep 10
    end

    def periodic_timer(seconds)
      timer = Timers::Group.new
      timer.now_and_every(seconds) do
        yield
      rescue StandardError => e
        error e.message
        error e.backtrace.join("\n")
      end
      loop { timer.wait }
    end

    def precise_last_update
      @last_update.strftime('%Y-%m-%d %H:%M:%S.%L')
    end
  end
end
