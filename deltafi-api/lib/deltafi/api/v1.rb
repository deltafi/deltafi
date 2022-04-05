# frozen_string_literal: true

Dir[File.join(File.dirname(__FILE__), 'v1', '*.rb')].each do |f|
  require "deltafi/api/v1/#{File.basename(f).split('.')[0]}"
end
