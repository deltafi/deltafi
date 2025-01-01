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

module Deltafi
  module Client
    class HttPartyWrapper
      include HTTParty

      user_name = ENV['DELTAFI_USER']
      user_password = ENV['DELTAFI_PASSWORD']

      # work around cert issues if running against local.deltafi.org
      default_options.update(verify: false)

      default_options.update(basic_auth: { username: user_name, password: user_password }) unless user_name.nil? || user_password.nil?
    end
  end
end
