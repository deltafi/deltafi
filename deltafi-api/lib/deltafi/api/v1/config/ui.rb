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

module Deltafi
  module API
    module V1
      module Config
        module UI
          class << self
            DOMAIN = ENV['DELTAFI_UI_DOMAIN']
            AUTH_MODE = ENV['AUTH_MODE']

            def config
              properties = DF.system_properties || {}

              config = properties[:ui] || {}
              config[:title] = properties[:systemName] || "DeltaFi"
              config[:domain] = DOMAIN
              config[:authMode] = AUTH_MODE
              config
            end
          end
        end
      end
    end
  end
end
