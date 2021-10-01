# frozen_string_literal: true

module Deltafi
  module API
    NAMESPACE = 'deltafi'
  end
end

Dir[File.join(File.dirname(__FILE__), 'api', '*.rb')].each do |f|
  require "deltafi/api/#{File.basename(f).split('.')[0]}"
end