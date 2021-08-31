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
