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

class CertUser
  include Deltafi::EntityResolver

  attr_reader :dn, :identifiers

  def initialize(dn)
    begin
      @dn = OpenSSL::X509::Name.parse(dn).to_s(OpenSSL::X509::Name::COMPAT)
    rescue StandardError
      raise 'Error parsing DN'
    end

    @identifiers = resolve(dn)
  end

  def common_name
    OpenSSL::X509::Name.parse(dn).to_a.find { |p| p[0] == 'CN' }[1]
  end

  def name
    common_name
  end

  def roles
    identifiers.map { |identifier| User[{ dn: identifier }]&.roles }.compact.flatten
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
