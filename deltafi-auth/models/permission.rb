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

require 'csv'

class Permission
  extend Deltafi::Logger

  def self.all
    CSV.table('permissions.csv').map do |row|
      unless row.size == 3 && row[1].match?(/^[a-zA-Z]*$/)
        warn "Ignoring malformed permission: #{row}"
        next
      end

      row.to_hash
    end.compact
  end

  def self.all_names
    all.map { |p| p[:name] }
  end
end
