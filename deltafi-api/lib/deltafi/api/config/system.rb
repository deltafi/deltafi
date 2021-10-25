# frozen_string_literal: true

module Deltafi
  module API
    module Config
      module System
        class << self
          def config
            {
              domain: ENV['DELTAFI_INGRESS_DOMAIN']
            }
          end
        end
      end
    end
  end
end
