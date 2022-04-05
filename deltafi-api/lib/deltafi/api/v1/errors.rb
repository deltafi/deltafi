# frozen_string_literal: true

Dir[File.join(File.dirname(__FILE__), 'errors', '*.rb')].each do |f|
  require "deltafi/api/v1/errors/#{File.basename(f).split('.')[0]}"
end
