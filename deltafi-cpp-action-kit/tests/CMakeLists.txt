# ABOUTME: Test build configuration for the DeltaFi C++ Action Kit.
# ABOUTME: Uses vendored Catch2 amalgamated and builds unit test executables.

list(APPEND CMAKE_MODULE_PATH ${DELTAFI_CATCH2_EXTRAS})
include(Catch)

function(add_deltafi_test name)
    add_executable(${name} ${name}.cpp)
    target_link_libraries(${name} PRIVATE deltafi::action_kit Catch2::Catch2WithMain)
    catch_discover_tests(${name})
endfunction()

add_deltafi_test(test_crypto)
add_deltafi_test(test_resp_client)
add_deltafi_test(test_params)
add_deltafi_test(test_queue_reconnect)
