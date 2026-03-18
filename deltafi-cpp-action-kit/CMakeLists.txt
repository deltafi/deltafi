# ABOUTME: Root build system for the DeltaFi C++ Action Kit header-only library.
# ABOUTME: Defines the interface library target, include paths, and optional test builds.

cmake_minimum_required(VERSION 3.20)
project(deltafi_cpp_action_kit LANGUAGES CXX)

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

# Header-only interface library (no compiled artifacts)
add_library(deltafi_action_kit INTERFACE)
add_library(deltafi::action_kit ALIAS deltafi_action_kit)

target_include_directories(deltafi_action_kit INTERFACE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/vendor
    ${CMAKE_CURRENT_SOURCE_DIR}/vendor/asio
    ${CMAKE_CURRENT_SOURCE_DIR}/vendor/spdlog
)

target_compile_features(deltafi_action_kit INTERFACE cxx_std_20)

target_compile_definitions(deltafi_action_kit INTERFACE
    ASIO_STANDALONE
    DELTAFI_ACTION_KIT_VERSION="${DELTAFI_ACTION_KIT_GIT_VERSION}"
)

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
