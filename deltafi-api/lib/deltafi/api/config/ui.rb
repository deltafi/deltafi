# frozen_string_literal: true

module Deltafi
  module API
    module Config
      module UI
        class << self
          def config
            {
              domain: ENV['DELTAFI_UI_DOMAIN'] || 'example.deltafi.org',
              title: ENV['DELTAFI_UI_TITLE'] || 'DeltaFi'
            }
          end
        end
      end
    end
  end
end
