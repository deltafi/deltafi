// ABOUTME: Content storage types and S3 operations.
// ABOUTME: Defines ContentServiceBase interface, ContentService (S3), and save/load helpers.

#pragma once

#include <cstdint>
#include <functional>
#include <istream>
#include <memory>
#include <ostream>
#include <string>
#include <string_view>
#include <vector>

#include "deltafi/types.hpp"
#include "deltafi/internal/s3_client.hpp"

namespace deltafi {

// Generate a UUID v4 string.
inline std::string generate_uuid() {
    static thread_local std::mt19937 gen{std::random_device{}()};
    std::uniform_int_distribution<uint32_t> dist(0, 15);
    std::uniform_int_distribution<uint32_t> dist2(8, 11);

    static constexpr std::string_view hex = "0123456789abcdef";
    std::string uuid(36, '-');
    for (int i = 0; i < 36; ++i) {
        if (i == 8 || i == 13 || i == 18 || i == 23) continue;
        if (i == 14) {
            uuid[i] = '4';
            continue;
        }
        if (i == 19) {
            uuid[i] = hex[dist2(gen)];
            continue;
        }
        uuid[i] = hex[dist(gen)];
    }
    return uuid;
}

// Abstract interface for content storage operations.
class ContentServiceBase {
public:
    virtual ~ContentServiceBase() = default;
    ContentServiceBase() = default;
    ContentServiceBase(const ContentServiceBase&) = delete;
    ContentServiceBase& operator=(const ContentServiceBase&) = delete;
    ContentServiceBase(ContentServiceBase&&) = delete;
    ContentServiceBase& operator=(ContentServiceBase&&) = delete;
    virtual Segment save(const std::string& did, const std::vector<uint8_t>& data) = 0;
    virtual Segment save_from_reader(const std::string& did, std::istream& reader,
                                     int64_t size) = 0;
    virtual Segment save_from_writer(const std::string& did,
                                     const std::function<void(std::ostream&)>& write_fn) = 0;
    virtual std::vector<uint8_t> load(const std::vector<Segment>& segments) = 0;
    virtual std::unique_ptr<std::istream> load_reader(const std::vector<Segment>& segments) = 0;
    virtual void remove(const std::vector<Segment>& segments) = 0;
};

// S3-compatible content storage implementation.
class ContentService : public ContentServiceBase {
public:
    // part_size overrides the S3 multipart part size when > 0 (bytes); 0 keeps the client default.
    ContentService(const std::string& endpoint, const std::string& access_key,
                   const std::string& secret_key, const std::string& bucket, bool use_ssl = false,
                   std::size_t part_size = 0)
        : client_(endpoint, access_key, secret_key, bucket, use_ssl) {
        if (part_size > 0) client_.set_part_size(part_size);
    }

    Segment save(const std::string& did, const std::vector<uint8_t>& data) override {
        auto uuid = generate_uuid();
        auto s3seg = client_.save(did, data, uuid);
        return Segment{
            .uuid = s3seg.uuid, .offset = s3seg.offset, .size = s3seg.size, .did = s3seg.did};
    }

    Segment save_from_reader(const std::string& did, std::istream& reader, int64_t size) override {
        auto uuid = generate_uuid();
        auto s3seg = client_.save_from_reader(did, reader, size, uuid);
        return Segment{
            .uuid = s3seg.uuid, .offset = s3seg.offset, .size = s3seg.size, .did = s3seg.did};
    }

    Segment save_from_writer(const std::string& did,
                             const std::function<void(std::ostream&)>& write_fn) override {
        auto uuid = generate_uuid();
        auto s3seg = client_.save_from_writer(did, write_fn, uuid);
        return Segment{
            .uuid = s3seg.uuid, .offset = s3seg.offset, .size = s3seg.size, .did = s3seg.did};
    }

    std::vector<uint8_t> load(const std::vector<Segment>& segments) override {
        return client_.load(to_s3(segments));
    }

    // Stream content incrementally with bounded memory (pulls from the socket).
    std::unique_ptr<std::istream> load_reader(const std::vector<Segment>& segments) override {
        return client_.load_reader(to_s3(segments));
    }

    void remove(const std::vector<Segment>& segments) override { client_.remove(to_s3(segments)); }

private:
    static std::vector<internal::S3Segment> to_s3(const std::vector<Segment>& segments) {
        std::vector<internal::S3Segment> s3segs;
        s3segs.reserve(segments.size());
        for (const auto& seg : segments) {
            s3segs.push_back(
                {.uuid = seg.uuid, .offset = seg.offset, .size = seg.size, .did = seg.did});
        }
        return s3segs;
    }

    internal::S3Client client_;
};

// Implement ActionContent load methods (declared in types.hpp).
inline std::vector<uint8_t> ActionContent::load_bytes() const {
    if (!content_service_) {
        throw std::runtime_error("ActionContent has no content service");
    }
    return content_service_->load(segments);
}

inline std::string ActionContent::load_string() const {
    auto data = load_bytes();
    return std::string(data.begin(), data.end());
}

inline std::unique_ptr<std::istream> ActionContent::load_reader() const {
    if (!content_service_) {
        throw std::runtime_error("ActionContent has no content service");
    }
    return content_service_->load_reader(segments);
}

// Set the content service pointer on all content items.
inline void bind_content_service(std::vector<ActionContent>& content, ContentServiceBase* cs) {
    for (auto& c : content) c.content_service_ = cs;
}

// Load all content bytes via the context's content service.
inline std::vector<uint8_t> load_bytes(const ActionContent& content, ContentServiceBase& cs) {
    return cs.load(content.segments);
}

// Load content as a string via the context's content service.
inline std::string load_string(const ActionContent& content, ContentServiceBase& cs) {
    auto data = cs.load(content.segments);
    return std::string(data.begin(), data.end());
}

// Save data and return an ActionContent with a single segment.
// The segment is tracked on the ActionContext for orphan cleanup.
inline ActionContent save_content(ContentServiceBase& cs, ActionContext& actx,
                                  const std::vector<uint8_t>& data, const std::string& name,
                                  const std::string& media_type) {
    auto seg = cs.save(actx.did, data);
    actx.track_segment(seg);
    ActionContent c{.name = name, .media_type = media_type, .segments = {seg}, .tags = {}};
    c.content_service_ = &cs;
    return c;
}

// Save a string as content.
inline ActionContent save_content_string(ContentServiceBase& cs, ActionContext& actx,
                                         const std::string& s, const std::string& name,
                                         const std::string& media_type) {
    std::vector<uint8_t> data(s.begin(), s.end());
    return save_content(cs, actx, data, name, media_type);
}

// Save content from an input stream. Use -1 for size if unknown.
inline ActionContent save_content_from_reader(ContentServiceBase& cs, ActionContext& actx,
                                              std::istream& reader, int64_t size,
                                              const std::string& name,
                                              const std::string& media_type) {
    auto seg = cs.save_from_reader(actx.did, reader, size);
    actx.track_segment(seg);
    ActionContent c{.name = name, .media_type = media_type, .segments = {seg}, .tags = {}};
    c.content_service_ = &cs;
    return c;
}

// Save content the action streams into a writer callback (an ostream sink), piped to
// the uploader with bounded memory.
inline ActionContent save_content_from_writer(ContentServiceBase& cs, ActionContext& actx,
                                              const std::function<void(std::ostream&)>& write_fn,
                                              const std::string& name,
                                              const std::string& media_type) {
    auto seg = cs.save_from_writer(actx.did, write_fn);
    actx.track_segment(seg);
    ActionContent c{.name = name, .media_type = media_type, .segments = {seg}, .tags = {}};
    c.content_service_ = &cs;
    return c;
}

// Load content as a streaming reader.
inline std::unique_ptr<std::istream> load_reader(const ActionContent& content,
                                                 ContentServiceBase& cs) {
    return cs.load_reader(content.segments);
}

// Implement ActionContext::child_context (needs generate_uuid from this header).
inline ActionContext ActionContext::child_context() const {
    ActionContext child;
    child.did = generate_uuid();
    child.delta_file_name = delta_file_name;
    child.data_source = data_source;
    child.flow_name = flow_name;
    child.flow_id = flow_id;
    child.action_name = action_name;
    child.action_version = action_version;
    child.hostname = hostname;
    child.system_name = system_name;
    child.start_time = start_time;
    child.memo = memo;
    child.content_service = content_service;
    child.lookup_client = lookup_client;
    child.artifact_client = artifact_client;
    return child;
}

}  // namespace deltafi
