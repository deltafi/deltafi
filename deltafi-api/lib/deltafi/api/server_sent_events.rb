# frozen_string_literal: true

Dir[File.join(File.dirname(__FILE__), 'server_sent_events', '*.rb')].each do |f|
  require "deltafi/api/server_sent_events/#{File.basename(f).split('.')[0]}"
end
