// ABOUTME: Tests the block-YAML-subset parser via ActionOptions::from_yaml.
// ABOUTME: Uses the real hello-world transform-action options plus comment/quote/colon edge cases.

#include <string>

#include <catch2/catch_test_macros.hpp>

#include "deltafi/options.hpp"

using deltafi::ActionOptions;

TEST_CASE("from_yaml parses the full transform action options", "[simpleyaml]") {
    const std::string yaml =
        R"(description: Add some content noting that we did a really good job
inputSpec:
  contentSummary: Expects a single text content item to transform
outputSpec:
  contentSummary: Produces a single text content item with appended message
  annotationsSummary: Adds language annotation
  annotationDescriptions:
    - key: language
      description: The language of the transformed content
  metadataDescriptions:
    - key: transformKey
      description: Marker indicating transform was applied
    - key: RGB Rows
      description: Color values from scheduled lookup table
    - key: McLaren Drivers
      description: Driver names from CSV lookup table
filters:
  - description: Filter due to did_filter_prefix match
errors:
  - description: Error due to did_error_prefix match
  - description: No content provided
  - description: No RGB results found
)";

    auto o = ActionOptions::from_yaml(yaml);

    CHECK(o.description == "Add some content noting that we did a really good job");
    REQUIRE(o.input_spec.has_value());
    REQUIRE(o.output_spec.has_value());
    // NOLINTNEXTLINE(bugprone-unchecked-optional-access): guarded by REQUIRE above
    const auto& in = *o.input_spec;
    // NOLINTNEXTLINE(bugprone-unchecked-optional-access): guarded by REQUIRE above
    const auto& out = *o.output_spec;

    CHECK(in.content_summary == "Expects a single text content item to transform");
    CHECK(out.content_summary == "Produces a single text content item with appended message");
    CHECK(out.annotations_summary == "Adds language annotation");

    REQUIRE(out.annotation_descriptions.size() == 1);
    CHECK(out.annotation_descriptions[0].key == "language");
    CHECK(out.annotation_descriptions[0].description == "The language of the transformed content");

    REQUIRE(out.metadata_descriptions.size() == 3);
    CHECK(out.metadata_descriptions[0].key == "transformKey");
    CHECK(out.metadata_descriptions[2].key == "McLaren Drivers");
    CHECK(out.metadata_descriptions[2].description == "Driver names from CSV lookup table");

    REQUIRE(o.filters.size() == 1);
    CHECK(o.filters[0].description == "Filter due to did_filter_prefix match");
    REQUIRE(o.errors.size() == 3);
    CHECK(o.errors[2].description == "No RGB results found");
}

TEST_CASE("from_yaml parses a description-only document", "[simpleyaml]") {
    auto o = ActionOptions::from_yaml("description: Hello pretends to egress\n");
    CHECK(o.description == "Hello pretends to egress");
    CHECK_FALSE(o.input_spec.has_value());
    CHECK_FALSE(o.output_spec.has_value());
}

TEST_CASE("from_yaml handles comments, quotes, and colons inside values", "[simpleyaml]") {
    const std::string yaml =
        R"(# a leading comment line
description: "Quoted: with a colon"
errors:
  - description: 'Error: bad input'
)";
    auto o = ActionOptions::from_yaml(yaml);
    CHECK(o.description == "Quoted: with a colon");
    REQUIRE(o.errors.size() == 1);
    CHECK(o.errors[0].description == "Error: bad input");
}
