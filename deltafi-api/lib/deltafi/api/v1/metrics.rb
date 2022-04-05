# frozen_string_literal: true

Dir[File.join(File.dirname(__FILE__), 'metrics', '*.rb')].each do |f|
  require "deltafi/api/v1/metrics/#{File.basename(f).split('.')[0]}"
end
