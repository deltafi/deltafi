// ABOUTME: Parameter definitions for the HelloWorldCppTransformAction.
// ABOUTME: Demonstrates nested structs, optional fields, lists, and maps in DELTAFI_PARAMS.

#pragma once

#include <deltafi/plugin>

struct Structure1 {
    bool s1_boolean = false;
    int s1_integer = 0;

    DELTAFI_PARAMS(Structure1,
        FIELD(s1_boolean, "Description of s1Boolean"),
        FIELD(s1_integer, "Description of s1Integer"))
};

struct Structure2 {
    std::vector<std::string> s2_string_list;
    std::map<std::string, std::string> s2_map;

    DELTAFI_PARAMS(Structure2,
        FIELD(s2_string_list, "Description of s2StringList"),
        FIELD(s2_map, "Description of s2Map"))
};

struct Structure3 {
    std::optional<Structure2> s2_instance_in_s3;
    std::vector<Structure1> s1_list_in_s3;

    DELTAFI_PARAMS(Structure3,
        FIELD(s2_instance_in_s3, "Description of s2InstanceInS3"),
        FIELD(s1_list_in_s3, "Description of s1ListInS3"))
};

struct HelloWorldCppTransformActionParams {
    bool convert_to_upper = false;
    bool always_error_on_prefix_hit = true;
    bool always_filter_on_prefix_hit = true;
    std::string did_error_prefix = "44";
    std::string did_filter_prefix = "2";
    std::optional<Structure1> structure1;
    std::optional<Structure2> structure2;
    std::vector<Structure3> structure3_list;

    DELTAFI_PARAMS(HelloWorldCppTransformActionParams,
        FIELD(convert_to_upper,
            "If TRUE, transformation will convert content to uppercase. Default is FALSE."),
        FIELD(always_error_on_prefix_hit,
            "If TRUE, when a DeltaFile with a DID starting with 'didErrorPrefix' is being processed, "
            "an ERROR will always occur. If FALSE, the ERROR occurs randomly with a 50% probability. "
            "Default is TRUE (always error)."),
        FIELD(always_filter_on_prefix_hit,
            "If TRUE, when a DeltaFile with a DID starting with 'didFilterPrefix' is being processed, "
            "a FILTER will always occur. If FALSE, the FILTER occurs randomly with a 50% probability. "
            "Default is TRUE (always filter)."),
        FIELD(did_error_prefix,
            "The prefix for DIDs that can cause an ERROR to occur. Default is '44'."),
        FIELD(did_filter_prefix,
            "The prefix for DIDs that can cause a FILTER to occur. Default is '2'."),
        FIELD(structure1, "Structure 1 instance"),
        FIELD(structure2, "Structure 2 instance"),
        FIELD(structure3_list, "Structure3 List"))
};
