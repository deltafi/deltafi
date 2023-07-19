#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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

require 'aws-sdk-s3'
require 'filesize'

module Deltafi
  module API
    module V1
      module Content
        MINIO_BUCKET = 'storage'
        MINIO_REGION = 'us-east-1'
        REQUIRED_KEYS = %i[did uuid size offset].freeze

        class << self
          def get_segment(content_segment, &block)
            return empty_output(content_segment[:mediaType]) if content_segment[:size].to_i < 1

            minio_options = minio_object_options(content_segment)
            minio_client.get_object(minio_options, &block)
          rescue Aws::S3::Errors::NoSuchKey => e
            raise "Content not found: #{e.message}"
          rescue Aws::S3::Errors::InvalidRange => e
            raise "Invalid content segment: #{e.message}"
          rescue Aws::S3::Errors::InvalidAccessKeyId => e
            raise "Invalid access key: #{e.message}"
          rescue Aws::S3::Errors::NoSuchBucket => e
            raise "Content storage not configured properly: #{e.message}"
          end

          def head_segment(content_segment)
            return empty_output(content_segment[:mediaType]) if content_segment[:size].to_i < 1

            minio_options = minio_object_options(content_segment)
            minio_client.head_object(minio_options)
          rescue Aws::S3::Errors::NotFound => e
            parent_did = content_segment[:did]
            deltafile = begin
              query_deltafile(parent_did)
            rescue StandardError => ee
              raise ee, "Parent DeltaFile (#{parent_did}) has been deleted."
            end
            raise e, "Parent DeltaFile (#{parent_did}) content has been deleted. Reason for this deletion: #{deltafile['contentDeletedReason']}" if deltafile['contentDeleted']

            raise e, "Content not found: #{e.message}"
          rescue Aws::S3::Errors::Forbidden => e
            raise e, "Access denied: #{e.message}"
          end

          def query_deltafile(did)
            query = <<-QUERY
              {
                deltaFile(did: "#{did}") {
                  contentDeleted
                  contentDeletedReason
                }
              }
            QUERY
            Deltafi.graphql(query).parsed_response.dig('data', 'deltaFile')
          end

          def empty_output(media_type)
            output = Aws::S3::Types::GetObjectOutput.new
            output.content_length = 0
            output.content_type = media_type
            output
          end

          def minio_object_options(content_segment)
            key = File.join(content_segment[:did][0..2], content_segment[:did], content_segment[:uuid])
            byte_start = content_segment[:offset].to_i
            byte_end = byte_start + content_segment[:size].to_i - 1
            range = "bytes=#{byte_start}-#{byte_end}"
            {
              bucket: MINIO_BUCKET,
              key: key,
              range: range
            }
          end

          def minio_client
            return @minio_client unless @minio_client.nil?

            minio_url = ENV['MINIO_URL'] || 'http://deltafi-minio:9000'
            minio_access_key = ENV.fetch('MINIO_ACCESSKEY', nil)
            minio_secret_key = ENV.fetch('MINIO_SECRETKEY', nil)

            Aws.config.update(
              endpoint: minio_url,
              access_key_id: minio_access_key,
              secret_access_key: minio_secret_key,
              force_path_style: true,
              region: MINIO_REGION
            )
            @minio_client = Aws::S3::Client.new
          end

          def content_size(content)
            content[:segments].sum { |segment| head_segment(segment).content_length }
          end

          def verify_content(content)
            %i[mediaType size segments].each do |key|
              raise "Invalid content: #{key} required" unless content[key]
            end

            content[:segments].each do |segment|
              REQUIRED_KEYS.each do |key|
                raise "Invalid content: #{key} required for each segment" unless segment[key]
              end
            end
          end
        end
      end
    end
  end
end
