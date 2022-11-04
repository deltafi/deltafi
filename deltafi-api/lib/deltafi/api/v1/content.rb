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

require 'aws-sdk-s3'
require 'filesize'

module Deltafi
  module API
    module V1
      module Content
        MINIO_BUCKET = 'storage'
        MINIO_REGION = 'us-east-1'

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
            raise e, "Content not found: #{e.message}"
          rescue Aws::S3::Errors::Forbidden => e
            raise e, "Access denied: #{e.message}"
          end

          def empty_output(media_type)
            output = Aws::S3::Types::GetObjectOutput.new
            output.content_length = 0
            output.content_type = media_type
            output
          end

          def minio_object_options(content_segment)
            key = File.join(content_segment[:did], content_segment[:uuid])
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

            properties = DF.system_properties
            Aws.config.update(
              endpoint: properties['minio.url'],
              access_key_id: properties['minio.access-key'],
              secret_access_key: properties['minio.secret-key'],
              force_path_style: true,
              region: MINIO_REGION
            )
            @minio_client = Aws::S3::Client.new
          end

          def content_reference_size(content_reference)
            content_reference[:segments].map { |segment| head_segment(segment).content_length }.sum
          end

          def verify_content_reference(content_reference)
            %i[mediaType size segments].each do |key|
              raise "Invalid content reference: #{key} required" unless content_reference[key]
            end

            content_reference[:segments].each do |segment|
              %i[did uuid size offset].each do |key|
                raise "Invalid content reference: #{key} required for each segment" unless segment[key]
              end
            end
          end
        end
      end
    end
  end
end
