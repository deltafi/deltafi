// ABOUTME: SHA256 and HMAC-SHA256 functions for AWS Signature V4 signing.
// ABOUTME: Wraps PicoSHA2 for hashing and implements RFC 2104 HMAC on top.

#pragma once

#include <algorithm>
#include <cstdint>
#include <iomanip>
#include <sstream>
#include <string>
#include <vector>

#include "picosha2.h"

namespace deltafi::internal {

// SHA256 hash of data, returned as lowercase hex string.
inline std::string sha256_hex(const std::string& data) {
    return picosha2::hash256_hex_string(data);
}

// SHA256 hash of raw bytes, returned as lowercase hex string.
inline std::string sha256_hex(const std::vector<uint8_t>& data) {
    return picosha2::hash256_hex_string(data.begin(), data.end());
}

// SHA256 hash of data, returned as raw bytes.
inline std::vector<uint8_t> sha256_raw(const std::string& data) {
    std::vector<uint8_t> hash(picosha2::k_digest_size);
    picosha2::hash256(data.begin(), data.end(), hash.begin(), hash.end());
    return hash;
}

// HMAC-SHA256 per RFC 2104. Returns raw bytes.
inline std::vector<uint8_t> hmac_sha256(const std::vector<uint8_t>& key,
                                         const std::string& data) {
    constexpr size_t block_size = 64;

    // If key is longer than block size, hash it
    std::vector<uint8_t> k;
    if (key.size() > block_size) {
        k.resize(picosha2::k_digest_size);
        picosha2::hash256(key.begin(), key.end(), k.begin(), k.end());
    } else {
        k = key;
    }

    // Pad key to block size
    k.resize(block_size, 0x00);

    // Inner pad: key XOR 0x36
    std::vector<uint8_t> i_key_pad(block_size);
    for (size_t i = 0; i < block_size; ++i) {
        i_key_pad[i] = k[i] ^ 0x36;
    }

    // Outer pad: key XOR 0x5c
    std::vector<uint8_t> o_key_pad(block_size);
    for (size_t i = 0; i < block_size; ++i) {
        o_key_pad[i] = k[i] ^ 0x5c;
    }

    // Inner hash: SHA256(i_key_pad || data)
    std::vector<uint8_t> inner_input(i_key_pad.begin(), i_key_pad.end());
    inner_input.insert(inner_input.end(), data.begin(), data.end());
    std::vector<uint8_t> inner_hash(picosha2::k_digest_size);
    picosha2::hash256(inner_input.begin(), inner_input.end(),
                      inner_hash.begin(), inner_hash.end());

    // Outer hash: SHA256(o_key_pad || inner_hash)
    std::vector<uint8_t> outer_input(o_key_pad.begin(), o_key_pad.end());
    outer_input.insert(outer_input.end(), inner_hash.begin(), inner_hash.end());
    std::vector<uint8_t> result(picosha2::k_digest_size);
    picosha2::hash256(outer_input.begin(), outer_input.end(),
                      result.begin(), result.end());

    return result;
}

// HMAC-SHA256 with a string key. Returns raw bytes.
inline std::vector<uint8_t> hmac_sha256(const std::string& key,
                                         const std::string& data) {
    std::vector<uint8_t> key_bytes(key.begin(), key.end());
    return hmac_sha256(key_bytes, data);
}

// Convert raw bytes to lowercase hex string.
inline std::string to_hex(const std::vector<uint8_t>& bytes) {
    std::ostringstream ss;
    for (auto b : bytes) {
        ss << std::hex << std::setfill('0') << std::setw(2)
           << static_cast<int>(b);
    }
    return ss.str();
}

// HMAC-SHA256 returning lowercase hex string.
inline std::string hmac_sha256_hex(const std::string& key,
                                    const std::string& data) {
    return to_hex(hmac_sha256(key, data));
}

// HMAC-SHA256 with raw key, returning lowercase hex string.
inline std::string hmac_sha256_hex(const std::vector<uint8_t>& key,
                                    const std::string& data) {
    return to_hex(hmac_sha256(key, data));
}

} // namespace deltafi::internal
