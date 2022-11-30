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

require 'forwardable'

class BasicUser
  include Deltafi::EntityResolver
  extend Forwardable

  attr_reader :user, :identifiers

  def_delegators :@user, :username, :name, :id

  def initialize(username, password)
    user = User[{ username: username }]
    return unless user&.password == password

    @user = user
    @identifiers = resolve(username)
  end

  def authenticated?
    !@user.nil?
  end

  def roles
    identifiers.map { |identifier| User[{ username: identifier }]&.roles }.compact.flatten
  end

  def permissions
    roles.map(&:permissions).flatten.uniq.sort
  end

  def permissions_csv
    permissions.join(',')
  end

  def has_permission?(permission)
    return !([permission, 'Admin'] & permissions).empty?
  end
end
