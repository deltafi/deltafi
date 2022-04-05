# frozen_string_literal: true

Dir[File.join(File.dirname(__FILE__), 'status', '*.rb')].each do |f|
  require "deltafi/monitor/status/#{File.basename(f).split('.')[0]}"
end
