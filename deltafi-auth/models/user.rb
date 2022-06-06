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

class User < Sequel::Model
  plugin :timestamps, update_on_create: true
  plugin :validation_helpers

  FIELDS = [:name, :dn, :domains]

  def update(updates)
    updates.each do |field, value|
      raise "Unknown field '#{field}'" unless FIELDS.include?(field)

      self[field] = value
    end
  end

  def validate
    super
    begin
      self.dn = OpenSSL::X509::Name.parse(dn).to_s(OpenSSL::X509::Name::COMPAT)
    rescue StandardError
      errors.add(:dn, 'must be a valid Distinguished Name')
    end

    validates_presence [:name, :dn, :domains], message: 'cannot be empty'
    validates_type String, [:name, :dn, :domains]
    validates_unique :dn, message: 'must be unique'
    validates_unique :name, message: 'must be unique'
  end

  def can_access?(domain)
    domains.split(',').any? do |dp|
      escaped = Regexp.escape(dp.lstrip).gsub('\*','.*?')
      regexp = Regexp.new("^#{escaped}$", Regexp::IGNORECASE)
      regexp.match?(domain)
    end
  end

  def to_api
    {
      id: id,
      name: name,
      dn: dn,
      domains: domains,
      created_at: created_at,
      updated_at: updated_at,
    }
  end
end
