# frozen_string_literal: true

Dir[File.join(File.dirname(__FILE__), 'error_count', '*.rb')].each do |f|
  require "deltafi/monitor/error_count/#{File.basename(f).split('.')[0]}"
end
