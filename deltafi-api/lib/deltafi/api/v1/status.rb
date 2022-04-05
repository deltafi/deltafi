# frozen_string_literal: true

module Deltafi
  module API
    module V1
      module Status
        extend self

        def status
          JSON.parse(DF.redis_client.get(DF::Common::STATUS_REDIS_KEY))
        end
      end
    end
  end
end
