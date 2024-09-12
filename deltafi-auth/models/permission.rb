#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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

$LOAD_PATH.unshift File.expand_path(File.join(File.dirname(__FILE__), '../lib'))

require 'csv'
require 'deltafi'

class Permission
  extend Deltafi::Logger

  @@permissions = []

  def self.all
    @@permissions
  end

  def self.all_names
    all.map { |p| p[:name] }
  end

  def self.load
    debug 'Loading permissions.csv'
    @@permissions = CSV.table('permissions.csv').map do |row|
      unless row.size == 3 && row[1].match?(/^[a-zA-Z]*$/)
        warn "Ignoring malformed permission: #{row}"
        next
      end

      row.to_hash
    end.compact
  end
end

Permission.load
