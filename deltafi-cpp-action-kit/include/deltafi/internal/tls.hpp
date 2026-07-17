// ABOUTME: Shared TLS cert auto-detection for the core, Valkey, and MinIO connections.
// ABOUTME: Reads CERT_PATH/KEY_PATH/CA_CHAIN_PATH and enforces both-cert-and-key-or-neither.

#pragma once

#include <filesystem>
#include <memory>
#include <stdexcept>
#include <string>

#include "httplib.h"

#include "deltafi/types.hpp"

namespace deltafi::internal {

// Default cert mount paths, matching the Java SslAutoConfiguration and Go action kit.
inline constexpr const char* default_cert_path = "/certs/tls.crt";
inline constexpr const char* default_key_path = "/certs/tls.key";
inline constexpr const char* default_ca_chain_path = "/certs/ca.crt";

// Resolved TLS configuration. When enabled, the client presents cert_path/key_path
// (mutual TLS) and verifies the server against ca_chain_path when that file exists.
struct TlsConfig {
    bool enabled = false;
    std::string cert_path;
    std::string key_path;
    std::string ca_chain_path;
};

// Resolve a TLS config from explicit paths: TLS is enabled when both the client
// cert and key exist on disk. Supplying one without the other is a misconfiguration.
inline TlsConfig resolve_tls(const std::string& cert_path, const std::string& key_path,
                             const std::string& ca_chain_path) {
    bool has_cert = std::filesystem::exists(cert_path);
    bool has_key = std::filesystem::exists(key_path);
    if (has_cert != has_key) {
        throw std::runtime_error("TLS misconfigured: found one of cert (" + cert_path +
                                 ") and key (" + key_path + ") but not both");
    }
    return TlsConfig{.enabled = has_cert && has_key,
                     .cert_path = cert_path,
                     .key_path = key_path,
                     .ca_chain_path = ca_chain_path};
}

// Detect the TLS config from CERT_PATH/KEY_PATH/CA_CHAIN_PATH (defaulting to the
// standard /certs mount paths).
inline TlsConfig detect_tls() {
    return resolve_tls(env_or_default("CERT_PATH", default_cert_path),
                       env_or_default("KEY_PATH", default_key_path),
                       env_or_default("CA_CHAIN_PATH", default_ca_chain_path));
}

// Apply the CA truststore and enable server-certificate verification on an
// httplib client. The client certificate/key are supplied at construction (see
// make_httplib_client). A no-op when TLS is disabled.
inline void apply_to_httplib(httplib::Client& client, const TlsConfig& cfg) {
    if (!cfg.enabled) {
        return;
    }
    if (std::filesystem::exists(cfg.ca_chain_path)) {
        client.set_ca_cert_path(cfg.ca_chain_path);
    }
    client.enable_server_certificate_verification(true);
}

// Build an httplib client for scheme_host_port with mutual TLS auto-detected from
// the standard cert mount. When enabled, the client presents its cert/key and
// verifies the server against the CA chain; otherwise it is a plaintext client.
inline std::unique_ptr<httplib::Client> make_httplib_client(const std::string& scheme_host_port) {
    auto cfg = detect_tls();
    auto client = cfg.enabled ? std::make_unique<httplib::Client>(scheme_host_port, cfg.cert_path,
                                                                  cfg.key_path)
                              : std::make_unique<httplib::Client>(scheme_host_port);
    apply_to_httplib(*client, cfg);
    return client;
}

}  // namespace deltafi::internal
