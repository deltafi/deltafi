// ABOUTME: Tests TLS cert auto-detection: both-or-neither enforcement and enable/disable.
// ABOUTME: Uses temp files; does not touch env vars or perform a real handshake.

#include <filesystem>
#include <fstream>

#include <catch2/catch_test_macros.hpp>

#include <deltafi/internal/tls.hpp>

using namespace deltafi::internal;

namespace {

// A private temp dir per test, cleaned up on scope exit.
struct TempCerts {
    std::filesystem::path dir;
    explicit TempCerts(const std::string& name)
        : dir(std::filesystem::temp_directory_path() / ("deltafi-tls-" + name)) {
        std::filesystem::remove_all(dir);
        std::filesystem::create_directories(dir);
    }
    ~TempCerts() {
        std::error_code ec;
        std::filesystem::remove_all(dir, ec);
    }
    TempCerts(const TempCerts&) = delete;
    TempCerts& operator=(const TempCerts&) = delete;
    TempCerts(TempCerts&&) = delete;
    TempCerts& operator=(TempCerts&&) = delete;
    std::string path(const std::string& file) const { return (dir / file).string(); }
    void write(const std::string& file) const { std::ofstream(dir / file) << "pem"; }
};

}  // namespace

TEST_CASE("resolve_tls enables mTLS when cert and key both exist") {
    TempCerts certs("both");
    certs.write("tls.crt");
    certs.write("tls.key");
    certs.write("ca.crt");

    auto cfg = resolve_tls(certs.path("tls.crt"), certs.path("tls.key"), certs.path("ca.crt"));

    CHECK(cfg.enabled);
    CHECK(cfg.cert_path == certs.path("tls.crt"));
    CHECK(cfg.key_path == certs.path("tls.key"));
    CHECK(cfg.ca_chain_path == certs.path("ca.crt"));
}

TEST_CASE("resolve_tls stays plaintext when neither cert nor key exists") {
    TempCerts certs("neither");

    auto cfg = resolve_tls(certs.path("tls.crt"), certs.path("tls.key"), certs.path("ca.crt"));

    CHECK_FALSE(cfg.enabled);
}

TEST_CASE("resolve_tls throws when only the cert is present") {
    TempCerts certs("cert-only");
    certs.write("tls.crt");

    CHECK_THROWS_AS(resolve_tls(certs.path("tls.crt"), certs.path("tls.key"), certs.path("ca.crt")),
                    std::runtime_error);
}

TEST_CASE("resolve_tls throws when only the key is present") {
    TempCerts certs("key-only");
    certs.write("tls.key");

    CHECK_THROWS_AS(resolve_tls(certs.path("tls.crt"), certs.path("tls.key"), certs.path("ca.crt")),
                    std::runtime_error);
}

TEST_CASE("resolve_tls enables TLS even without a CA chain (system roots)") {
    TempCerts certs("no-ca");
    certs.write("tls.crt");
    certs.write("tls.key");

    auto cfg = resolve_tls(certs.path("tls.crt"), certs.path("tls.key"), certs.path("ca.crt"));

    CHECK(cfg.enabled);
    CHECK_FALSE(std::filesystem::exists(cfg.ca_chain_path));
}
