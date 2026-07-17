# Vendored dependency versions

Header-only third-party dependencies vendored into the C++ action kit. When bumping one, replace
its file(s), run the full suite (dev/asan/tsan + `-DDELTAFI_WERROR=ON` + `make fmt`/`make lint`),
and update the version here.

| Dependency | Version | Files | Upstream |
|---|---|---|---|
| nlohmann/json | 3.12.0 | `nlohmann/json.hpp` | https://github.com/nlohmann/json |
| spdlog | 1.17.0 | `spdlog/spdlog/` (tree) | https://github.com/gabime/spdlog |
| Catch2 | 3.15.2 | `catch2/extras/catch_amalgamated.{hpp,cpp}`, `Catch.cmake`, `CatchAddTests.cmake` | https://github.com/catchorg/Catch2 |
| BS_thread_pool | 5.1.0 | `BS_thread_pool.hpp` | https://github.com/bshoshany/thread-pool |
| picosha2 | 1.0.1 | `picosha2.h` | https://github.com/okdshin/PicoSHA2 |
| cpp-httplib | 0.50.1 | `httplib.h` | https://github.com/yhirose/cpp-httplib |
| asio (standalone) | 1.38.1 | `asio/` (tree) | https://github.com/chriskohlhoff/asio |

Notes:
- spdlog is built with `SPDLOG_USE_STD_FORMAT` (C++20 `std::format`), so its bundled `fmt` is not
  compiled — this avoids the consteval-format-string errors newer clang emits against older bundled fmt.
- BS_thread_pool v5 made `thread_pool` a template; the kit uses `BS::thread_pool<>`.
- cpp-httplib 0.50.1 renamed `MultipartFormDataItems` to `UploadFormDataItems` (same fields).
- TLS uses cpp-httplib's OpenSSL support (`CPPHTTPLIB_OPENSSL_SUPPORT`) and asio-ssl (`asio/ssl.hpp`).
