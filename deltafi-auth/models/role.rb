#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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

require 'permission'

class Role < Sequel::Model
  many_to_many :users

  plugin :timestamps, update_on_create: true
  plugin :validation_helpers

  FIELDS = %i[name permissions].freeze

  def before_destroy
    remove_all_users
    super
  end

  def update(updates)
    updates.each do |field, value|
      raise "Unknown field '#{field}'" unless FIELDS.include?(field)

      send("#{field}=", value)
    end
  end

  def validate
    super
    validates_presence :name, message: 'cannot be empty'
    validates_unique :name, message: 'must be unique'
    validates_presence :permissions, message: 'cannot be empty'
    errors.add(:permissions, 'must contain only valid permissions') unless (permissions - Permission.all_names).empty?
  end

  def permissions
    permissions_csv.to_s.split(',')
  end

  def permissions=(array)
    self.permissions_csv = array.sort.join(',')
  end

  def to_api
    {
      id: id,
      name: name,
      permissions: permissions,
      created_at: created_at.utc.strftime('%FT%T.%3NZ'),
      updated_at: updated_at.utc.strftime('%FT%T.%3NZ')
    }
  end
end
