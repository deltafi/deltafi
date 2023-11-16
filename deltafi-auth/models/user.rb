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

require 'bcrypt'

class User < Sequel::Model
  many_to_many :roles

  include BCrypt
  plugin :timestamps, update_on_create: true
  plugin :validation_helpers

  FIELDS = %i[name dn username password role_ids].freeze

  def initialize(args)
    @role_ids = args.delete(:role_ids)
    super(args)
  end

  def after_save
    super
    update_roles(@role_ids) unless @role_ids.nil?
  end

  def before_destroy
    remove_all_roles
    super
  end

  def update(updates)
    updates.each do |field, value|
      raise "Unknown field '#{field}'" unless FIELDS.include?(field)

      if field == :role_ids
        update_roles(value)
      else
        send("#{field}=", value)
      end
    end
  end

  def update_roles(new_role_ids)
    current_role_ids = roles.map(&:id)
    (current_role_ids - new_role_ids).each { |id| remove_role(id) }
    (new_role_ids - current_role_ids).each do |id|
      add_role(id)
    rescue Sequel::NoMatchingRow
      raise "No such role with id #{id}"
    end
  end

  def password
    @password ||= Password.new(password_hash)
  end

  def password=(new_password)
    @password = Password.create(new_password, cost: 8)
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

    validates_presence :name, message: 'cannot be empty'
    validates_type String, :name
    validates_unique %i[dn username], message: 'must be unique'
    validates_unique :dn, message: 'must be unique'
    validates_unique :name, message: 'must be unique'
    validates_unique :username, message: 'must be unique'
  end

  def permissions
    roles.map(&:permissions).flatten.uniq.sort
  end

  def permissions_csv
    permissions.join(',')
  end

  def permission?(permission)
    return !([permission, 'Admin'] & permissions).empty?
  end

  def to_api
    {
      id: id,
      name: name,
      dn: dn,
      username: username,
      created_at: created_at.utc.strftime('%FT%T.%3NZ'),
      updated_at: updated_at.utc.strftime('%FT%T.%3NZ'),
      roles: roles.map(&:to_api),
      permissions: permissions
    }
  end
end
