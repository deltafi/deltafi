# frozen_string_literal: true

Dir[File.join(File.dirname(__FILE__), 'config', '*.rb')].each do |f|
  require "deltafi/api/config/#{File.basename(f).split('.')[0]}"
end