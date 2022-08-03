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

require 'bcrypt'

class User < Sequel::Model
  include BCrypt
  plugin :timestamps, update_on_create: true
  plugin :validation_helpers

  FIELDS = %i[name dn domains username password].freeze

  def update(updates)
    updates.each do |field, value|
      raise "Unknown field '#{field}'" unless FIELDS.include?(field)

      send("#{field}=", value)
    end
  end

  def password
    @password ||= Password.new(password_hash)
  end

  def password=(new_password)
    @password = Password.create(new_password)
    self.password_hash = @password
  end

  def common_name
    return nil if dn.nil?

    OpenSSL::X509::Name.parse(dn).to_a.find { |p| p[0] == 'CN' }[1]
  end

  def validate
    super

    errors.add(:dn, 'or username required') if dn.nil? && username.nil?

    if dn
      begin
        self.dn = OpenSSL::X509::Name.parse(dn).to_s(OpenSSL::X509::Name::COMPAT)
      rescue StandardError
        errors.add(:dn, 'must be a valid Distinguished Name')
      end
    end

    validates_presence %i[name domains], message: 'cannot be empty'
    validates_type String, %i[name domains]
    validates_unique %i[dn username], message: 'must be unique'
    validates_unique :dn, message: 'must be unique'
    validates_unique :name, message: 'must be unique'
    validates_unique :username, message: 'must be unique'
  end

  def can_access?(url)
    domain = url.split('/')[2].split(':')[0]
    domains.split(',').any? do |dp|
      escaped = Regexp.escape(dp.lstrip).gsub('\*', '.*?')
      regexp = Regexp.new("^#{escaped}$", Regexp::IGNORECASE)
      regexp.match?(domain)
    end
  end

  def to_api
    {
      id: id,
      name: name,
      dn: dn,
      username: username,
      domains: domains,
      created_at: created_at.utc.strftime("%FT%T.%3NZ"),
      updated_at: updated_at.utc.strftime("%FT%T.%3NZ")
    }
  end
end
