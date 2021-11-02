# frozen_string_literal: true

Dir[File.join(File.dirname(__FILE__), 'status', '*.rb')].each do |f|
  require "deltafi/api/status/#{File.basename(f).split('.')[0]}"
end
