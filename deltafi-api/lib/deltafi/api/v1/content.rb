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
          def get(content_reference)
            minio_options = minio_object_options(content_reference)
            minio_client.get_object(minio_options) do |chunk|
              yield(chunk)
            end
          rescue Aws::S3::Errors::NoSuchKey => e
            raise "Content not found: #{e.message}"
          rescue Aws::S3::Errors::InvalidRange => e
            raise "Invalid content reference: #{e.message}"
          rescue Aws::S3::Errors::InvalidAccessKeyId => e
            raise "Invalid access key: #{e.message}"
          rescue Aws::S3::Errors::NoSuchBucket => e
            raise "Content storage not configured properly: #{e.message}"
          end

          def head(content_reference)
            minio_options = minio_object_options(content_reference)
            minio_client.head_object(minio_options)
          rescue Aws::S3::Errors::NotFound => e
            raise e, "Content not found: #{e.message}"
          rescue Aws::S3::Errors::Forbidden => e
            raise e, "Access denied: #{e.message}"
          end

          def minio_object_options(content_reference)
            key = File.join(content_reference[:did], content_reference[:uuid])
            byte_start = content_reference[:offset].to_i
            byte_end = byte_start + content_reference[:size].to_i - 1
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
        end
      end
    end
  end
end
