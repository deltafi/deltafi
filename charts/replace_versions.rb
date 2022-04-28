#!/usr/bin/env ruby

if ARGV.size != 1
  puts "USAGE: #{__FILE__} version"
  exit 1
end

version = ARGV.first
values_file = File.join(__dir__, 'deltafi', 'values.yaml')
values = File.read(values_file)
['gateway', 'config-server', 'core-domain', 'core-actions', 'ingress'].each do |image|
  values.gsub!(/image: .*deltafi-#{image}:.*$/, "image: deltafi-#{image}:#{version}")
end

File.write(values_file, values)

