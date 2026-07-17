// ABOUTME: Minimal S3-compatible client for content storage (MinIO or an external S3 service).
// ABOUTME: Implements PUT/GET/DELETE with AWS Signature V4 signing over cpp-httplib.

#pragma once

#include <algorithm>
#include <array>
#include <chrono>
#include <cstdint>
#include <functional>
#include <iomanip>
#include <istream>
#include <map>
#include <memory>
#include <sstream>
#include <string>
#include <thread>
#include <utility>
#include <vector>

#include "httplib.h"

#include <spdlog/spdlog.h>

#include "deltafi/internal/bounded_stream.hpp"
#include "deltafi/internal/crypto.hpp"
#include "deltafi/internal/tls.hpp"

namespace deltafi::internal {

// Format a time_point as YYYYMMDD for AWS signing.
inline std::string aws_date(const std::chrono::system_clock::time_point& tp) {
    auto t = std::chrono::system_clock::to_time_t(tp);
    std::tm tm{};
    gmtime_r(&t, &tm);
    std::array<char, 9> buf{};
    std::strftime(buf.data(), buf.size(), "%Y%m%d", &tm);
    return buf.data();
}

// Format a time_point as YYYYMMDDTHHmmssZ for AWS signing.
inline std::string aws_datetime(const std::chrono::system_clock::time_point& tp) {
    auto t = std::chrono::system_clock::to_time_t(tp);
    std::tm tm{};
    gmtime_r(&t, &tm);
    std::array<char, 17> buf{};
    std::strftime(buf.data(), buf.size(), "%Y%m%dT%H%M%SZ", &tm);
    return buf.data();
}

// URL-encode a path component (encodes everything except / and unreserved chars).
inline std::string uri_encode(const std::string& s) {
    std::ostringstream out;
    for (unsigned char c : s) {
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') ||
            c == '-' || c == '_' || c == '.' || c == '~' || c == '/') {
            out << c;
        } else {
            out << '%' << std::uppercase << std::hex << std::setfill('0') << std::setw(2)
                << static_cast<int>(c);
        }
    }
    return out.str();
}

// RFC-3986 encode a query key/value: encode everything but the unreserved chars
// (unlike uri_encode above, which preserves '/' for path components).
inline std::string uri_encode_query(const std::string& s) {
    std::ostringstream out;
    for (unsigned char c : s) {
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') ||
            c == '-' || c == '_' || c == '.' || c == '~') {
            out << c;
        } else {
            out << '%' << std::uppercase << std::hex << std::setfill('0') << std::setw(2)
                << static_cast<int>(c);
        }
    }
    return out.str();
}

// Build a SigV4 canonical query string: params sorted by key (std::map iterates in
// key order), each key and value RFC-3986 encoded, joined with '&'.
inline std::string canonical_query(const std::map<std::string, std::string>& params) {
    std::string out;
    for (const auto& [k, v] : params) {
        if (!out.empty()) out += "&";
        out += uri_encode_query(k) + "=" + uri_encode_query(v);
    }
    return out;
}

// AWS Signature V4 signing. Free function (params passed explicitly) so it can be
// called from a streaming reader's own thread without capturing an S3Client.
inline httplib::Headers sigv4_sign(const std::string& method, const std::string& path,
                                   const std::string& query, const std::string& payload,
                                   const std::string& access_key, const std::string& secret_key,
                                   const std::string& region, const std::string& host) {
    auto now = std::chrono::system_clock::now();
    auto date = aws_date(now);
    auto datetime = aws_datetime(now);

    auto payload_hash = sha256_hex(payload);
    std::string scope = date + "/" + region + "/s3/aws4_request";

    std::string canonical_headers = "host:" + host + "\nx-amz-content-sha256:" + payload_hash +
                                    "\nx-amz-date:" + datetime + "\n";
    std::string signed_headers = "host;x-amz-content-sha256;x-amz-date";

    std::string canonical_request = method + "\n" + path + "\n" + query + "\n" + canonical_headers +
                                    "\n" + signed_headers + "\n" + payload_hash;

    std::string string_to_sign =
        "AWS4-HMAC-SHA256\n" + datetime + "\n" + scope + "\n" + sha256_hex(canonical_request);

    auto k_date = hmac_sha256("AWS4" + secret_key, date);
    auto k_region = hmac_sha256(k_date, region);
    auto k_service = hmac_sha256(k_region, "s3");
    auto k_signing = hmac_sha256(k_service, "aws4_request");
    auto signature = hmac_sha256_hex(k_signing, string_to_sign);

    std::string authorization = "AWS4-HMAC-SHA256 Credential=" + access_key + "/" + scope +
                                ", SignedHeaders=" + signed_headers + ", Signature=" + signature;

    return {{"Authorization", authorization},
            {"x-amz-content-sha256", payload_hash},
            {"x-amz-date", datetime},
            {"Host", host}};
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

// Streaming reader over one or more segments. A producer thread issues a
// content-receiver GET per segment on its OWN httplib client (so a long-lived
// stream never blocks the shared client) and pushes chunks into the bounded base
// queue; abandoning the reader early aborts the in-flight GET.
class SegmentStreamBuf : public BoundedStreamBuf {
public:
    struct Signer {
        std::string access_key, secret_key, region, host;
        httplib::Headers operator()(const std::string& method, const std::string& path) const {
            return sigv4_sign(method, path, "", "", access_key, secret_key, region, host);
        }
    };

    SegmentStreamBuf(std::unique_ptr<httplib::Client> client, std::string bucket, Signer signer,
                     std::vector<S3Segment> segments)
        : client_(std::move(client)),
          bucket_(std::move(bucket)),
          signer_(std::move(signer)),
          segments_(std::move(segments)),
          producer_([this] { produce(); }) {}

    ~SegmentStreamBuf() override {
        request_stop();
        if (producer_.joinable()) producer_.join();
    }

    SegmentStreamBuf(const SegmentStreamBuf&) = delete;
    SegmentStreamBuf& operator=(const SegmentStreamBuf&) = delete;
    SegmentStreamBuf(SegmentStreamBuf&&) = delete;
    SegmentStreamBuf& operator=(SegmentStreamBuf&&) = delete;

private:
    void produce() {
        try {
            for (const auto& seg : segments_) {
                if (!fetch_segment(seg)) break;  // consumer abandoned the reader
            }
            mark_done();
        } catch (...) {
            mark_done(std::current_exception());
        }
    }

    bool fetch_segment(const S3Segment& seg) {
        std::string path = "/" + bucket_ + "/" + uri_encode(seg.object_path());
        httplib::Headers headers = signer_("GET", path);
        if (seg.offset > 0 || seg.size > 0) {
            headers.emplace("Range", "bytes=" + std::to_string(seg.offset) + "-" +
                                         std::to_string(seg.offset + seg.size - 1));
        }
        bool aborted = false;
        auto res = client_->Get(path, headers, [&](const char* data, size_t len) -> bool {
            if (!push(data, len)) {
                aborted = true;
                return false;  // consumer abandoned -> abort the GET
            }
            return true;
        });
        if (aborted) return false;
        if (res && res->status == 416) return true;  // empty object range -> empty
        if (!res || (res->status != 200 && res->status != 206)) {
            int code = res ? res->status : 0;
            throw std::runtime_error("S3 GET " + path + " failed (HTTP " + std::to_string(code) +
                                     ")");
        }
        return true;
    }

    std::unique_ptr<httplib::Client> client_;
    std::string bucket_;
    Signer signer_;
    std::vector<S3Segment> segments_;
    std::thread producer_;  // declared last: starts after all members are initialized
};

// An istream that owns its streaming streambuf.
class S3ContentStream : public std::istream {
public:
    S3ContentStream(std::unique_ptr<httplib::Client> client, std::string bucket,
                    SegmentStreamBuf::Signer signer, std::vector<S3Segment> segments)
        : std::istream(nullptr),
          buf_(std::move(client), std::move(bucket), std::move(signer), std::move(segments)) {
        rdbuf(&buf_);
    }

private:
    SegmentStreamBuf buf_;
};

// S3-compatible client for content storage (MinIO or an external S3 service).
// Uses AWS Signature V4 signing over HTTP(S) via cpp-httplib.
class S3Client {
public:
    S3Client(const std::string& endpoint, const std::string& access_key,
             const std::string& secret_key, const std::string& bucket, bool use_ssl = false)
        : access_key_(access_key), secret_key_(secret_key), bucket_(bucket), region_("us-east-1") {
        std::string ep = endpoint;
        if (ep.starts_with("https://")) {
            ep = ep.substr(8);
            use_ssl = true;
        } else if (ep.starts_with("http://")) {
            ep = ep.substr(7);
        }
        host_ = ep;

        // make_httplib_client applies mutual TLS from /certs (client cert + CA chain +
        // server verification) when the scheme is https and certs are present; a plaintext
        // client otherwise.
        std::string scheme = use_ssl ? "https" : "http";
        base_url_ = scheme + "://" + ep;
        client_ = make_httplib_client(base_url_);
        client_->set_connection_timeout(std::chrono::seconds(10));
        client_->set_read_timeout(std::chrono::seconds(30));
    }

    // The upload part size. The S3 multipart minimum (5 MiB) that production must respect is
    // enforced where S3_PART_SIZE is read; set_part_size only caps at the s3proxy non-chunked
    // limit so tests can use tiny parts to exercise the multipart path with small payloads.
    static constexpr size_t min_part_size = 5UL * 1024 * 1024;
    static constexpr size_t max_part_size = 32UL * 1024 * 1024;
    void set_part_size(size_t n) { part_size_ = std::clamp(n, size_t{1}, max_part_size); }

    // Save data and return a segment reference. Small content takes the single-PUT
    // fast path; anything larger than the part size is uploaded via multipart.
    S3Segment save(const std::string& did, const std::vector<uint8_t>& data,
                   const std::string& uuid) {
        S3Segment seg{.uuid = uuid, .offset = 0, .size = 0, .did = did};
        std::string path = "/" + bucket_ + "/" + uri_encode(seg.object_path());
        if (data.size() <= part_size_) {
            put_object(path, std::string(data.begin(), data.end()));
            seg.size = static_cast<int64_t>(data.size());
        } else {
            std::string body(data.begin(), data.end());
            std::istringstream in(body);
            seg.size = static_cast<int64_t>(multipart_upload(path, in, read_chunk(in, part_size_)));
        }
        return seg;
    }

    // Save from an input stream with bounded memory: the source is read in part-size
    // chunks. `size` is advisory (pass -1 if unknown); the returned segment size is the
    // actual number of bytes streamed.
    S3Segment save_from_reader(const std::string& did, std::istream& reader, int64_t /*size*/,
                               const std::string& uuid) {
        S3Segment seg{.uuid = uuid, .offset = 0, .size = 0, .did = did};
        std::string path = "/" + bucket_ + "/" + uri_encode(seg.object_path());
        std::string first = read_chunk(reader, part_size_);
        if (first.size() < part_size_) {
            // Whole source fit in one part (includes empty content) -> single PUT.
            put_object(path, first);
            seg.size = static_cast<int64_t>(first.size());
        } else {
            seg.size = static_cast<int64_t>(multipart_upload(path, reader, std::move(first)));
        }
        return seg;
    }

    // Save content produced by a writer callback: the action streams into the given
    // ostream, which is piped to the multipart uploader with bounded memory (at most
    // one part is held). An upload failure aborts the multipart and propagates.
    S3Segment save_from_writer(const std::string& did,
                               const std::function<void(std::ostream&)>& write_fn,
                               const std::string& uuid) {
        S3Segment seg{.uuid = uuid, .offset = 0, .size = 0, .did = did};
        std::string path = "/" + bucket_ + "/" + uri_encode(seg.object_path());
        MultipartWriteBuf buf(*this, path);
        std::ostream out(&buf);
        out.exceptions(std::ios::badbit);  // propagate upload errors out of write_fn
        try {
            write_fn(out);
            out.flush();
            seg.size = static_cast<int64_t>(buf.finish());
        } catch (...) {
            buf.abort();
            throw;
        }
        return seg;
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

    // Stream one or more segments incrementally with bounded memory. The istream
    // pulls from the socket via a dedicated client + background producer, so reading
    // only a prefix fetches only a bounded amount and never materializes the whole object.
    std::unique_ptr<std::istream> load_reader(const std::vector<S3Segment>& segments) {
        auto dedicated = make_httplib_client(base_url_);
        dedicated->set_connection_timeout(std::chrono::seconds(10));
        dedicated->set_read_timeout(std::chrono::seconds(30));
        SegmentStreamBuf::Signer signer{
            .access_key = access_key_, .secret_key = secret_key_, .region = region_, .host = host_};
        return std::make_unique<S3ContentStream>(std::move(dedicated), bucket_, signer, segments);
    }

    // Delete the object for each segment.
    void remove(const std::vector<S3Segment>& segments) {
        for (const auto& seg : segments) {
            std::string path = "/" + bucket_ + "/" + uri_encode(seg.object_path());
            auto headers = sign_request("DELETE", path, "", "");

            auto res = client_->Delete(path, headers);
            if (!res || (res->status != 204 && res->status != 200 && res->status != 404)) {
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
    std::string base_url_;
    std::unique_ptr<httplib::Client> client_;
    size_t part_size_ = min_part_size;

    // Read up to n bytes from the stream into a string (short read at EOF).
    static std::string read_chunk(std::istream& in, size_t n) {
        std::string buf(n, '\0');
        in.read(buf.data(), static_cast<std::streamsize>(n));
        buf.resize(static_cast<size_t>(in.gcount()));
        return buf;
    }

    // Throw if the response is missing or not a 2xx.
    static void require_2xx(const httplib::Result& res, const std::string& op,
                            const std::string& path) {
        if (!res || res->status < 200 || res->status >= 300) {
            int code = res ? res->status : 0;
            std::string msg = res ? res->body : "connection failed";
            throw std::runtime_error("S3 " + op + " " + path + " failed (HTTP " +
                                     std::to_string(code) + "): " + msg);
        }
    }

    // Single signed PUT of a fully-materialized body (fast path / small content).
    void put_object(const std::string& path, const std::string& body) {
        auto headers = sign_request("PUT", path, "", body);
        headers.emplace("Content-Type", "application/octet-stream");
        require_2xx(client_->Put(path, headers, body, "application/octet-stream"), "PUT", path);
    }

    // Multipart upload: `first` is the already-read first part (== part_size_); read
    // the rest in part-size chunks, aborting on any failure. Returns total bytes.
    size_t multipart_upload(const std::string& path, std::istream& reader, std::string first) {
        std::string upload_id = create_multipart_upload(path);
        std::vector<std::pair<int, std::string>> parts;
        size_t total = 0;
        int part_num = 1;
        try {
            for (std::string chunk = std::move(first); !chunk.empty();
                 chunk = read_chunk(reader, part_size_)) {
                parts.emplace_back(part_num, upload_part(path, upload_id, part_num, chunk));
                total += chunk.size();
                ++part_num;
            }
            complete_multipart_upload(path, upload_id, parts);
        } catch (...) {
            abort_multipart_upload(path, upload_id);
            throw;
        }
        return total;
    }

    std::string create_multipart_upload(const std::string& path) {
        std::string query = canonical_query({{"uploads", ""}});
        auto headers = sign_request("POST", path, query, "");
        auto res = client_->Post(path + "?" + query, headers, "", "application/octet-stream");
        require_2xx(res, "CreateMultipartUpload", path);
        return extract_xml_tag(res->body, "UploadId");
    }

    std::string upload_part(const std::string& path, const std::string& upload_id, int part_num,
                            const std::string& chunk) {
        std::string query =
            canonical_query({{"partNumber", std::to_string(part_num)}, {"uploadId", upload_id}});
        auto headers = sign_request("PUT", path, query, chunk);
        auto res = client_->Put(path + "?" + query, headers, chunk, "application/octet-stream");
        require_2xx(res, "UploadPart", path);
        return res->get_header_value("ETag");
    }

    void complete_multipart_upload(const std::string& path, const std::string& upload_id,
                                   const std::vector<std::pair<int, std::string>>& parts) {
        std::string body = "<CompleteMultipartUpload>";
        for (const auto& [num, etag] : parts) {
            body += "<Part><PartNumber>" + std::to_string(num) + "</PartNumber><ETag>" + etag +
                    "</ETag></Part>";
        }
        body += "</CompleteMultipartUpload>";
        std::string query = canonical_query({{"uploadId", upload_id}});
        auto headers = sign_request("POST", path, query, body);
        require_2xx(client_->Post(path + "?" + query, headers, body, "application/xml"),
                    "CompleteMultipartUpload", path);
    }

    void abort_multipart_upload(const std::string& path, const std::string& upload_id) noexcept {
        try {
            std::string query = canonical_query({{"uploadId", upload_id}});
            auto headers = sign_request("DELETE", path, query, "");
            client_->Delete(path + "?" + query, headers);
        } catch (...) {  // NOLINT(bugprone-empty-catch): abort is best-effort cleanup on failure
        }
    }

    // Push-driven multipart writer backing save_from_writer's ostream sink. Holds at
    // most one part; flushes a part once more than a part's worth is buffered (so the
    // final, possibly-partial, part is known). Nested for access to the S3 helpers.
    class MultipartWriteBuf : public std::streambuf {
    public:
        MultipartWriteBuf(S3Client& client, std::string path)
            : client_(client), path_(std::move(path)) {}

        // Complete the upload (or single PUT for content that fit in one part). Returns total
        // bytes.
        size_t finish() {
            if (upload_id_.empty()) {
                client_.put_object(path_, buf_);
                return buf_.size();
            }
            if (!buf_.empty()) flush_part(buf_);
            client_.complete_multipart_upload(path_, upload_id_, parts_);
            return total_;
        }

        void abort() noexcept {
            if (!upload_id_.empty()) client_.abort_multipart_upload(path_, upload_id_);
        }

    protected:
        std::streamsize xsputn(const char* s, std::streamsize n) override {
            buf_.append(s, static_cast<size_t>(n));
            drain();
            return n;
        }
        int_type overflow(int_type ch) override {
            if (!traits_type::eq_int_type(ch, traits_type::eof())) {
                buf_.push_back(static_cast<char>(traits_type::to_char_type(ch)));
            }
            drain();
            return ch;
        }

    private:
        // Flush full parts while more than one part's worth is buffered; the remainder
        // (<= part size) stays in buf_ as the eventual last part.
        void drain() {
            while (buf_.size() > client_.part_size_) {
                std::string part = buf_.substr(0, client_.part_size_);
                buf_.erase(0, client_.part_size_);
                flush_part(part);
            }
        }
        void flush_part(const std::string& part) {
            if (upload_id_.empty()) upload_id_ = client_.create_multipart_upload(path_);
            int n = ++part_num_;
            parts_.emplace_back(n, client_.upload_part(path_, upload_id_, n, part));
            total_ += part.size();
        }

        S3Client& client_;
        std::string path_;
        std::string buf_;
        std::string upload_id_;
        std::vector<std::pair<int, std::string>> parts_;
        size_t total_ = 0;
        int part_num_ = 0;
    };

    // Extract the inner text of the first <tag>…</tag> (minimal, no XML library).
    static std::string extract_xml_tag(const std::string& xml, const std::string& tag) {
        std::string open = "<" + tag + ">";
        std::string close = "</" + tag + ">";
        auto a = xml.find(open);
        if (a == std::string::npos) return "";
        a += open.size();
        auto b = xml.find(close, a);
        if (b == std::string::npos) return "";
        return xml.substr(a, b - a);
    }

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
        if (res && res->status == 416) return {};  // empty-object Range -> empty content
        if (!res || (res->status != 200 && res->status != 206)) {
            int code = res ? res->status : 0;
            std::string msg = res ? res->body : "connection failed";
            throw std::runtime_error("S3 GET " + path + " failed (HTTP " + std::to_string(code) +
                                     "): " + msg);
        }

        return std::vector<uint8_t>(res->body.begin(), res->body.end());
    }

    // AWS Signature V4 signing.
    httplib::Headers sign_request(const std::string& method, const std::string& path,
                                  const std::string& query, const std::string& payload) {
        return sigv4_sign(method, path, query, payload, access_key_, secret_key_, region_, host_);
    }
};

}  // namespace deltafi::internal
