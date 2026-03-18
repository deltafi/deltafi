# ABOUTME: Build system for the DeltaFi C++ Hello World plugin.
# ABOUTME: Produces a single static executable linking the header-only action kit.

cmake_minimum_required(VERSION 3.20)
project(deltafi_cpp_hello_world VERSION 0.1.0 LANGUAGES CXX)

# Action kit: use local path if provided, otherwise fetch from gitlab
set(DELTAFI_ACTION_KIT_PATH "" CACHE PATH
    "Local path to deltafi-cpp-action-kit (leave empty to fetch from gitlab)")
set(DELTAFI_ACTION_KIT_TAG "main" CACHE STRING
    "Git tag/branch to fetch when not using a local path")

if(DELTAFI_ACTION_KIT_PATH)
    add_subdirectory(${DELTAFI_ACTION_KIT_PATH}
                     ${CMAKE_CURRENT_BINARY_DIR}/deltafi-cpp-action-kit)
else()
    include(FetchContent)
    FetchContent_Declare(deltafi_cpp_action_kit
        GIT_REPOSITORY https://gitlab.com/deltafi/deltafi.git
        GIT_TAG        ${DELTAFI_ACTION_KIT_TAG}
        SOURCE_SUBDIR  deltafi-cpp-action-kit
    )
    FetchContent_MakeAvailable(deltafi_cpp_action_kit)
endif()

# Plugin version from build arg or default
if(NOT DEFINED VERSION)
    set(VERSION "0.0.0-dev")
endif()

add_executable(deltafi-cpp-hello-world main.cpp)
target_link_libraries(deltafi-cpp-hello-world PRIVATE deltafi::action_kit)
target_compile_definitions(deltafi-cpp-hello-world PRIVATE
    VERSION="${VERSION}"
)
target_include_directories(deltafi-cpp-hello-world PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# Tests (Catch2 provided by action kit)
option(HELLO_WORLD_TESTS "Build hello-world unit tests" ON)
if(HELLO_WORLD_TESTS)
    deltafi_enable_testing()
    list(APPEND CMAKE_MODULE_PATH ${DELTAFI_CATCH2_EXTRAS})
    enable_testing()
    include(Catch)

    function(add_hello_world_test name)
        add_executable(${name} tests/${name}.cpp)
        target_link_libraries(${name} PRIVATE deltafi::action_kit Catch2::Catch2WithMain)
        target_include_directories(${name} PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
        target_compile_definitions(${name} PRIVATE
            VERSION="${VERSION}"
        )
        catch_discover_tests(${name})
    endfunction()

    add_hello_world_test(test_transform)
    add_hello_world_test(test_egress)
    add_hello_world_test(test_timed_ingress)
    add_hello_world_test(test_join)
    add_hello_world_test(test_split)
endif()
