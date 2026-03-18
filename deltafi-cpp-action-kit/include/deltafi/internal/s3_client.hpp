// ABOUTME: Minimal S3-compatible client for MinIO content storage.
// ABOUTME: Implements PUT/GET/DELETE with AWS Signature V4 signing over cpp-httplib.

#pragma once

#include <chrono>
#include <cstdint>
#include <iomanip>
#include <istream>
#include <sstream>
#include <string>
#include <vector>

#include "httplib.h"

#include <spdlog/spdlog.h>

#include "deltafi/internal/crypto.hpp"

namespace deltafi::internal {

// Format a time_point as YYYYMMDD for AWS signing.
inline std::string aws_date(
    const std::chrono::system_clock::time_point& tp) {
    auto t = std::chrono::system_clock::to_time_t(tp);
    std::tm tm{};
    gmtime_r(&t, &tm);
    char buf[9];
    std::strftime(buf, sizeof(buf), "%Y%m%d", &tm);
    return buf;
}

// Format a time_point as YYYYMMDDTHHmmssZ for AWS signing.
inline std::string aws_datetime(
    const std::chrono::system_clock::time_point& tp) {
    auto t = std::chrono::system_clock::to_time_t(tp);
    std::tm tm{};
    gmtime_r(&t, &tm);
    char buf[17];
    std::strftime(buf, sizeof(buf), "%Y%m%dT%H%M%SZ", &tm);
    return buf;
}

// URL-encode a path component (encodes everything except / and unreserved chars).
inline std::string uri_encode(const std::string& s) {
    std::ostringstream out;
    for (unsigned char c : s) {
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') ||
            (c >= '0' && c <= '9') || c == '-' || c == '_' ||
            c == '.' || c == '~' || c == '/') {
            out << c;
        } else {
            out << '%' << std::uppercase << std::hex << std::setfill('0')
                << std::setw(2) << static_cast<int>(c);
        }
    }
    return out.str();
}

// Segment reference for stored objects (mirrors Go's Segment type).
struct S3Segment {
    std::string uuid;
    int64_t offset = 0;
    int64_t size = 0;
    std::string did;

    std::string object_path() const {
        std::string prefix = did;
        if (prefix.size() > 3) prefix = prefix.substr(0, 3);
        return prefix + "/" + did + "/" + uuid;
    }
};

// S3-compatible client for content storage in MinIO.
// Uses AWS Signature V4 signing over plain HTTP via cpp-httplib.
class S3Client {
public:
    S3Client(const std::string& endpoint, const std::string& access_key,
             const std::string& secret_key, const std::string& bucket,
             bool use_ssl = false)
        : access_key_(access_key), secret_key_(secret_key),
          bucket_(bucket), region_("us-east-1") {
        std::string ep = endpoint;
        if (ep.starts_with("https://")) {
            ep = ep.substr(8);
            use_ssl = true;
        } else if (ep.starts_with("http://")) {
            ep = ep.substr(7);
        }
        host_ = ep;

        if (use_ssl) {
            client_ = std::make_unique<httplib::Client>("https://" + ep);
        } else {
            client_ = std::make_unique<httplib::Client>("http://" + ep);
        }
        client_->set_connection_timeout(std::chrono::seconds(10));
        client_->set_read_timeout(std::chrono::seconds(30));
    }

    // Save data and return a segment reference with a generated UUID.
    S3Segment save(const std::string& did, const std::vector<uint8_t>& data,
                   const std::string& uuid) {
        S3Segment seg{uuid, 0, static_cast<int64_t>(data.size()), did};
        std::string path = "/" + bucket_ + "/" + uri_encode(seg.object_path());

        std::string body(data.begin(), data.end());
        auto headers = sign_request("PUT", path, "", body);
        headers.emplace("Content-Type", "application/octet-stream");

        auto res = client_->Put(path, headers, body, "application/octet-stream");
        if (!res || res->status < 200 || res->status >= 300) {
            int code = res ? res->status : 0;
            std::string msg = res ? res->body : "connection failed";
            throw std::runtime_error(
                "S3 PUT " + path + " failed (HTTP " +
                std::to_string(code) + "): " + msg);
        }
        return seg;
    }

    // Save data from an input stream. The size parameter must match the number
    // of bytes available. Use -1 if unknown (data will be buffered to determine size).
    S3Segment save_from_reader(const std::string& did, std::istream& reader,
                               int64_t size, const std::string& uuid) {
        // Read all data from the stream — cpp-httplib requires the full body
        // for signing and upload.
        std::vector<uint8_t> data;
        if (size > 0) {
            data.resize(static_cast<size_t>(size));
            reader.read(reinterpret_cast<char*>(data.data()),
                        static_cast<std::streamsize>(size));
            auto read_count = reader.gcount();
            data.resize(static_cast<size_t>(read_count));
        } else {
            std::istreambuf_iterator<char> begin(reader), end;
            data.assign(begin, end);
        }
        return save(did, data, uuid);
    }

    // Load and concatenate data from one or more segments.
    std::vector<uint8_t> load(const std::vector<S3Segment>& segments) {
        std::vector<uint8_t> result;
        for (const auto& seg : segments) {
            auto data = load_segment(seg);
            result.insert(result.end(), data.begin(), data.end());
        }
        return result;
    }

    // Delete the object for each segment.
    void remove(const std::vector<S3Segment>& segments) {
        for (const auto& seg : segments) {
            std::string path = "/" + bucket_ + "/" + uri_encode(seg.object_path());
            auto headers = sign_request("DELETE", path, "", "");

            auto res = client_->Delete(path, headers);
            if (!res || (res->status != 204 && res->status != 200
                         && res->status != 404)) {
                int code = res ? res->status : 0;
                spdlog::warn("S3 DELETE {} failed (HTTP {})", path, code);
            }
        }
    }

private:
    std::string access_key_;
    std::string secret_key_;
    std::string bucket_;
    std::string region_;
    std::string host_;
    std::unique_ptr<httplib::Client> client_;

    // Load a single segment, using Range header for offset/size.
    std::vector<uint8_t> load_segment(const S3Segment& seg) {
        std::string path = "/" + bucket_ + "/" + uri_encode(seg.object_path());

        httplib::Headers headers;
        if (seg.offset > 0 || seg.size > 0) {
            std::string range = "bytes=" + std::to_string(seg.offset) + "-" +
                                std::to_string(seg.offset + seg.size - 1);
            headers.emplace("Range", range);
        }

        auto signed_headers = sign_request("GET", path, "", "");
        signed_headers.insert(headers.begin(), headers.end());

        auto res = client_->Get(path, signed_headers);
        if (!res || (res->status != 200 && res->status != 206)) {
            int code = res ? res->status : 0;
            std::string msg = res ? res->body : "connection failed";
            throw std::runtime_error(
                "S3 GET " + path + " failed (HTTP " +
                std::to_string(code) + "): " + msg);
        }

        return std::vector<uint8_t>(res->body.begin(), res->body.end());
    }

    // AWS Signature V4 signing.
    httplib::Headers sign_request(const std::string& method,
                                  const std::string& path,
                                  const std::string& query,
                                  const std::string& payload) {
        auto now = std::chrono::system_clock::now();
        auto date = aws_date(now);
        auto datetime = aws_datetime(now);

        auto payload_hash = sha256_hex(payload);
        std::string scope = date + "/" + region_ + "/s3/aws4_request";

        // Canonical headers (must be sorted)
        std::string canonical_headers =
            "host:" + host_ + "\n"
            "x-amz-content-sha256:" + payload_hash + "\n"
            "x-amz-date:" + datetime + "\n";
        std::string signed_headers = "host;x-amz-content-sha256;x-amz-date";

        // Canonical request
        std::string canonical_request =
            method + "\n" +
            path + "\n" +
            query + "\n" +
            canonical_headers + "\n" +
            signed_headers + "\n" +
            payload_hash;

        // String to sign
        std::string string_to_sign =
            "AWS4-HMAC-SHA256\n" +
            datetime + "\n" +
            scope + "\n" +
            sha256_hex(canonical_request);

        // Signing key chain
        auto k_date = hmac_sha256("AWS4" + secret_key_, date);
        auto k_region = hmac_sha256(k_date, region_);
        auto k_service = hmac_sha256(k_region, "s3");
        auto k_signing = hmac_sha256(k_service, "aws4_request");

        auto signature = hmac_sha256_hex(k_signing, string_to_sign);

        std::string authorization =
            "AWS4-HMAC-SHA256 Credential=" + access_key_ + "/" + scope +
            ", SignedHeaders=" + signed_headers +
            ", Signature=" + signature;

        return {
            {"Authorization", authorization},
            {"x-amz-content-sha256", payload_hash},
            {"x-amz-date", datetime},
            {"Host", host_}
        };
    }
};

} // namespace deltafi::internal
