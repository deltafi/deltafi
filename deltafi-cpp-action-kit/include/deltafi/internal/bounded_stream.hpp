// ABOUTME: Bounded producer/consumer streambuf base for streaming HTTP response bodies.
// ABOUTME: A producer thread pushes chunks into a size-bounded queue; the streambuf consumer pulls.

#pragma once

#include <condition_variable>
#include <cstddef>
#include <deque>
#include <exception>
#include <mutex>
#include <streambuf>
#include <string>

namespace deltafi::internal {

// Shared machinery for a streaming read: a background producer (owned by the derived
// class) calls push() for each chunk it fetches; the streambuf consumer pulls via
// underflow(). Memory is bounded to max_buffered regardless of total size, and the
// consumer abandoning the reader signals the producer (via push() returning false)
// to abort. The derived class owns the producer thread — it starts it last in its
// constructor and, in its destructor, calls request_stop() then joins the thread.
class BoundedStreamBuf : public std::streambuf {
public:
    BoundedStreamBuf(const BoundedStreamBuf&) = delete;
    BoundedStreamBuf& operator=(const BoundedStreamBuf&) = delete;
    BoundedStreamBuf(BoundedStreamBuf&&) = delete;
    BoundedStreamBuf& operator=(BoundedStreamBuf&&) = delete;
    ~BoundedStreamBuf() override = default;

protected:
    explicit BoundedStreamBuf(size_t max_buffered = 1UL * 1024 * 1024)
        : max_buffered_(max_buffered) {}

    // --- producer side (call only from the producer thread) ---

    // Push a chunk, blocking while the queue is full. Returns false if the consumer
    // has abandoned the reader — the producer should stop/abort when it sees false.
    bool push(const char* data, size_t len) {
        std::unique_lock lock(mu_);
        not_full_.wait(lock, [this] { return buffered_ < max_buffered_ || stop_; });
        if (stop_) return false;
        queue_.emplace_back(data, len);
        buffered_ += len;
        not_empty_.notify_one();
        return true;
    }

    bool stopped() {
        std::scoped_lock lock(mu_);
        return stop_;
    }

    // Signal that the producer finished. Pass an exception to surface a mid-stream
    // failure (rethrown from the consumer's next underflow → istream badbit).
    void mark_done(const std::exception_ptr& error = nullptr) {
        std::scoped_lock lock(mu_);
        error_ = error;
        done_ = true;
        not_empty_.notify_all();
    }

    // --- consumer / lifecycle side ---

    // Ask the producer to stop; the derived destructor calls this before joining.
    void request_stop() {
        {
            std::scoped_lock lock(mu_);
            stop_ = true;
        }
        not_full_.notify_all();
        not_empty_.notify_all();
    }

    int_type underflow() override {
        std::unique_lock lock(mu_);
        not_empty_.wait(lock, [this] { return !queue_.empty() || done_; });
        if (queue_.empty()) {
            if (error_) std::rethrow_exception(error_);  // istream sets badbit
            return traits_type::eof();
        }
        current_ = std::move(queue_.front());
        queue_.pop_front();
        buffered_ -= current_.size();
        lock.unlock();
        not_full_.notify_all();
        char* base = current_.data();
        setg(base, base, base + current_.size());
        return traits_type::to_int_type(*gptr());
    }

private:
    const size_t max_buffered_;
    std::mutex mu_;
    std::condition_variable not_empty_;
    std::condition_variable not_full_;
    std::deque<std::string> queue_;
    std::string current_;
    size_t buffered_ = 0;
    bool done_ = false;
    bool stop_ = false;
    std::exception_ptr error_;
};

}  // namespace deltafi::internal
