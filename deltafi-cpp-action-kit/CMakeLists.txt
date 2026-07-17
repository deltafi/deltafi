# ABOUTME: Root build system for the DeltaFi C++ Action Kit header-only library.
# ABOUTME: Defines the interface library target, include paths, and optional test builds.

cmake_minimum_required(VERSION 3.20)
project(deltafi_cpp_action_kit LANGUAGES CXX)

set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Derive version from git tags (e.g. "2.52.0" on tag, "2.52.0-3-gabcdef1" after)
execute_process(
    COMMAND git describe --tags --always
    WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
    OUTPUT_VARIABLE DELTAFI_ACTION_KIT_GIT_VERSION
    OUTPUT_STRIP_TRAILING_WHITESPACE
    ERROR_QUIET
    RESULT_VARIABLE _git_result
)
if(NOT _git_result EQUAL 0 OR DELTAFI_ACTION_KIT_GIT_VERSION STREQUAL "")
    # Fallback: read VERSION file (written during Docker staging)
    if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/VERSION")
        file(STRINGS "${CMAKE_CURRENT_SOURCE_DIR}/VERSION" DELTAFI_ACTION_KIT_GIT_VERSION LIMIT_COUNT 1)
    else()
        set(DELTAFI_ACTION_KIT_GIT_VERSION "0.0.0-dev")
    endif()
endif()

# TLS for the core/artifact/lookup HTTP clients uses cpp-httplib's OpenSSL support.
# On macOS, Homebrew's OpenSSL is keg-only, so point CMake at it when present.
if(APPLE AND NOT OPENSSL_ROOT_DIR)
    execute_process(COMMAND brew --prefix openssl@3
        OUTPUT_VARIABLE _brew_openssl OUTPUT_STRIP_TRAILING_WHITESPACE ERROR_QUIET)
    if(_brew_openssl)
        set(OPENSSL_ROOT_DIR "${_brew_openssl}")
    endif()
endif()
find_package(OpenSSL REQUIRED)

# Header-only interface library (no compiled artifacts)
add_library(deltafi_action_kit INTERFACE)
add_library(deltafi::action_kit ALIAS deltafi_action_kit)

target_include_directories(deltafi_action_kit INTERFACE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/vendor
    ${CMAKE_CURRENT_SOURCE_DIR}/vendor/asio
    ${CMAKE_CURRENT_SOURCE_DIR}/vendor/spdlog
)

target_link_libraries(deltafi_action_kit INTERFACE OpenSSL::SSL OpenSSL::Crypto)

# On macOS, cpp-httplib loads trusted roots from the system keychain, which pulls
# in the Security and CoreFoundation frameworks. Linux uses the on-disk CA store.
if(APPLE)
    target_link_libraries(deltafi_action_kit INTERFACE "-framework CoreFoundation" "-framework Security")
endif()

target_compile_features(deltafi_action_kit INTERFACE cxx_std_20)

target_compile_definitions(deltafi_action_kit INTERFACE
    ASIO_STANDALONE
    # Enable cpp-httplib's OpenSSL-backed HTTPS client for mutual TLS to core.
    CPPHTTPLIB_OPENSSL_SUPPORT
    # Use C++20 std::format instead of the vendored bundled fmt, whose consteval
    # format-string checking is rejected by newer clang (e.g. clang-tidy 21).
    SPDLOG_USE_STD_FORMAT
    DELTAFI_ACTION_KIT_VERSION="${DELTAFI_ACTION_KIT_GIT_VERSION}"
)

option(DELTAFI_WERROR "Treat warnings as errors (intended for CI)" OFF)

# Apply the project's warning flags to a consumer target. Header-only kit ->
# warnings are meaningful only on the targets that compile against it.
function(deltafi_apply_warnings target)
    # -Wno-missing-field-initializers: GCC's -Wextra warns when a C++20 designated
    # initializer omits fields (which are value-initialized by design). Clang does
    # not; disable it so the idiom works uniformly under -Werror.
    target_compile_options(${target} PRIVATE -Wall -Wextra -Wpedantic -Wno-missing-field-initializers)
    if(DELTAFI_WERROR)
        target_compile_options(${target} PRIVATE -Werror)
    endif()
endfunction()

set(DELTAFI_SANITIZE "" CACHE STRING "Sanitizer: address | thread | undefined | (empty)")

# Apply the selected sanitizer's compile+link flags to a consumer target.
function(deltafi_apply_sanitizers target)
    if(DELTAFI_SANITIZE STREQUAL "")
        return()
    endif()
    target_compile_options(${target} PRIVATE -fsanitize=${DELTAFI_SANITIZE} -fno-omit-frame-pointer -g)
    target_link_options(${target} PRIVATE -fsanitize=${DELTAFI_SANITIZE})
endfunction()

# Vendored Catch2 v3.13.0 (amalgamated + test discovery)
# Expose the extras path so consuming plugins can use catch_discover_tests.
# The Catch2 library target is created on demand via deltafi_enable_testing().
set(DELTAFI_CATCH2_EXTRAS "${CMAKE_CURRENT_SOURCE_DIR}/vendor/catch2/extras"
    CACHE INTERNAL "Path to Catch2 CMake extras for catch_discover_tests")
set(_DELTAFI_CATCH2_SRC "${CMAKE_CURRENT_SOURCE_DIR}/vendor/catch2/extras/catch_amalgamated.cpp"
    CACHE INTERNAL "")
set(_DELTAFI_CATCH2_INC "${CMAKE_CURRENT_SOURCE_DIR}/vendor/catch2/extras"
    CACHE INTERNAL "")

function(deltafi_enable_testing)
    if(NOT TARGET Catch2)
        add_library(Catch2 STATIC ${_DELTAFI_CATCH2_SRC})
        target_include_directories(Catch2 PUBLIC ${_DELTAFI_CATCH2_INC})
        target_compile_features(Catch2 PUBLIC cxx_std_20)
        add_library(Catch2::Catch2WithMain ALIAS Catch2)
    endif()
endfunction()

# Tests (build with -DBUILD_TESTING=ON)
option(BUILD_TESTING "Build unit tests" OFF)
if(BUILD_TESTING)
    deltafi_enable_testing()
    enable_testing()
    add_subdirectory(tests)
endif()
