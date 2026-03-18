// ABOUTME: Timed ingress action that creates DeltaFiles on a schedule.
// ABOUTME: Demonstrates memo-based state tracking and conditional metadata/annotation logic.

#pragma once

#include <string>

#include <deltafi/plugin>

class HelloWorldCppTimedIngressAction {
public:
    deltafi::IngressResultType ingress(deltafi::ActionContext& context) {
        // Parse index from memo (defaults to 0, increments each run)
        int index = 0;
        if (!context.memo.empty()) {
            try {
                index = std::stoi(context.memo) + 1;
            } catch (...) {}
        }

        auto filename = context.flow_name + "-" + std::to_string(index);

        deltafi::IngressResult result(context);
        auto& item = result.create_item(filename);
        item.add_metadata("index", std::to_string(index));

        if (index % 3 == 1) {
            item.add_metadata("hello", "transform");
            item.add_annotation("helloMode", "basic");
        } else if (index % 3 == 2) {
            item.add_metadata("hello", "join");
            item.add_annotation("helloMode", "advanced");
            if (index % 2 == 0) {
                item.add_metadata("join-group", "even");
            } else {
                item.add_metadata("join-group", "odd");
            }
        }

        auto content_data = "Some content, part " + std::to_string(index);
        item.save_content(content_data, filename, "text/plain");

        result.set_memo(std::to_string(index));
        result.set_execute_immediate(false);
        result.set_status_message("Successfully created " + filename);
        result.log_info() << "Created timed ingress file: " << filename
                         << " (index=" << index << ")";
        return result;
    }
};

DELTAFI_ACTION(HelloWorldCppTimedIngressAction,
    "HelloWorldCppTimedIngressAction",
    "Create some DeltaFiles for hello-world consumption")
