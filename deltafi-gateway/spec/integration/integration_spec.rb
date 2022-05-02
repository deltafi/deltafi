#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
#

# frozen_string_literal: true

require 'json'
require 'net/http'
require 'uri'

$did = nil

describe 'integration' do
  before(:all) do
    @uri = URI.parse('http://localhost:4000')
    @header = { 'Content-Type': 'application/json' }
    @http = Net::HTTP.new(@uri.host, @uri.port)
    @request = Net::HTTP::Post.new(@uri.request_uri, @header)
  end

  def post_graphql(graphql)
    @request.body = { 'query' => graphql }.to_json
    @http.request(@request)
  end

  graphql_files = Dir['spec/fixtures/full-flow/*'].sort

  it graphql_files[0] do
    body = JSON.parse(post_graphql(File.read(graphql_files[0])).body)
    error = body['errors']&.first&.fetch('message')
    expect(error).to be_nil, error
    $did = body['data']['ingress']['did'] if $did.nil?
  end

  graphql_files[1..-1].each do |graphql_file|
    it graphql_file do
      body = JSON.parse(post_graphql(File.read(graphql_file).gsub('%1$s', $did)).body)
      error = body['errors']&.first&.fetch('message')
      expect(error).to be_nil, error
    end
  end
end
