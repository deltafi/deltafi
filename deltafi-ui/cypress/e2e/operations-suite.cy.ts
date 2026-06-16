/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

{
  describe("Dashboard Page", () => {
    const baseUrl = "http://localhost:8080";

    describe("Shared Page Tests", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("loads the page with correct heading", () => {
        cy.get("h2").contains("Dashboard");
      });

      it("displays Refresh button", () => {
        cy.contains("Refresh").should("exist");
      });

      it("displays DeltaFile stats bar", () => {
        cy.get(".summary-stats-bar").should("exist");
        cy.get(".summary-stats-bar").contains("Total DeltaFiles");
        cy.get(".summary-stats-bar").contains("In-flight DeltaFiles");
      });

      it("displays Metrics panel", () => {
        cy.get("span.p-panel-title").contains("Metrics");
      });

      it("displays Installed Plugins panel", () => {
        cy.get("span.p-panel-title").contains("Installed Plugins");
      });

      it("displays External Links panel", () => {
        cy.get("span.p-panel-title").contains("External Links");
      });

      it("page container exists", () => {
        cy.get(".dashboard-page").should("exist");
      });

      it("renders correctly at default viewport", () => {
        cy.get(".dashboard-page").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get(".dashboard-page").should("be.visible");
      });

      it("can click refresh button", () => {
        cy.contains("Refresh").click();
        cy.get(".dashboard-page").should("exist");
      });
    });
  });
}

{
  describe("Events Page", () => {
    const baseUrl = "http://localhost:8080/events";

    describe("Shared Page Tests", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("loads the page with default settings", () => {
        cy.get("h2").contains("Events");
      });

      it("displays the Events panel", () => {
        cy.contains("Events").should("exist");
      });

      it("displays the data table", () => {
        cy.get(".p-datatable").should("exist");
      });

      it("displays the refresh button", () => {
        cy.contains("Refresh").should("exist");
      });

      it("displays timezone button", () => {
        cy.get("button").contains(/[A-Z]{2,4}/).should("exist");
      });

      it("displays all expected table columns", () => {
        cy.get(".p-datatable-thead").within(() => {
          cy.contains("Severity");
          cy.contains("Summary");
          cy.contains("Source");
          cy.contains("Timestamp");
        });
      });

      it("table structure exists", () => {
        cy.get(".p-datatable-thead").should("exist");
      });

      it("displays notification column header icon", () => {
        cy.get(".p-datatable-thead .pi-bell").should("exist");
      });

      it("displays acknowledged column header icon", () => {
        cy.get(".p-datatable-thead .pi-thumbs-up").should("exist");
      });

      it("page container exists", () => {
        cy.get(".events-page").should("exist");
      });

      it("events panel exists", () => {
        cy.get(".events-panel").should("exist");
      });

      it("displays search input", () => {
        cy.get('input[placeholder="Search"]').should("exist");
      });

      it("search input is interactive", () => {
        cy.get('input[placeholder="Search"]').should("be.visible").and("be.enabled");
      });

      it("severity column has filter menu", () => {
        cy.get(".p-datatable-column-filter-button").first().should("exist");
      });

      it("displays date picker", () => {
        cy.get(".time-range").should("exist");
      });

      it("displays severity badges in table", () => {
        cy.get(".p-datatable-tbody").should("exist");
      });

      it("renders correctly at default viewport", () => {
        cy.get(".events-page").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get(".events-page").should("be.visible");
      });

      it("shows context menu on right click", () => {
        cy.get(".events-panel").rightclick();
        cy.get(".p-contextmenu").should("be.visible");
      });

      it("context menu contains expected options", () => {
        cy.get(".p-contextmenu").within(() => {
          cy.contains("Clear Selected");
          cy.contains("Select All Visible");
          cy.contains("Clear Filters");
        });
        cy.get("body").click();
      });

      it("can clear selection from context menu", () => {
        cy.get(".events-panel").rightclick();
        cy.get(".p-contextmenu").should("be.visible");
        cy.contains("Clear Selected").click();
      });
    });
  });
}

{
  describe("Errors Page", () => {
    const baseUrl = "http://localhost:8080/errors";

    describe("Shared Page Tests", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("loads the page with correct heading", () => {
        cy.get("h2").contains("Errors");
      });

      it("displays Refresh button", () => {
        cy.contains("Refresh").should("exist");
      });

      it("displays filter sidebar tab", () => {
        cy.get(".filter-sidebar-tab").should("exist");
      });

      it("displays tabs", () => {
        cy.get(".p-tabs").should("exist");
      });

      it("displays All tab", () => {
        cy.contains("button.p-tab", "All").should("exist");
      });

      it("displays By Flow tab", () => {
        cy.contains("button.p-tab", "By Flow").should("exist");
      });

      it("displays By Message tab", () => {
        cy.contains("button.p-tab", "By Message").should("exist");
      });

      it("page container exists", () => {
        cy.get(".errors-page").should("exist");
      });

      it("renders correctly at default viewport", () => {
        cy.get(".errors-page").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get(".errors-page").should("be.visible");
      });

      it("All tab displays DeltaFiles panel", () => {
        cy.get("span.p-panel-title").contains("DeltaFiles");
      });

      it("can switch to By Flow tab", () => {
        cy.contains("button.p-tab", "By Flow").click();
        cy.get("span.p-panel-title").contains("DeltaFiles by Flow");
      });

      it("can switch to By Message tab", () => {
        cy.contains("button.p-tab", "By Message").click();
        cy.get("span.p-panel-title").contains("DeltaFiles by Message");
      });

      it("displays action menu button in panel header", () => {
        cy.get(".p-panel-header-icon").should("exist");
      });

      it("displays errors table", () => {
        cy.get("#errorsTable").should("exist");
      });

      it("displays By Flow panel when tab is clicked", () => {
        cy.contains("button.p-tab", "By Flow").click();
        cy.get("span.p-panel-title").contains("DeltaFiles by Flow");
      });

      it("displays By Flow action menu button in panel header", () => {
        cy.contains("button.p-tab", "By Flow").click();
        cy.get(".p-panel-header-icon").should("exist");
      });

      it("displays By Message panel when tab is clicked", () => {
        cy.contains("button.p-tab", "By Message").click();
        cy.get("span.p-panel-title").contains("DeltaFiles by Message");
      });

      it("displays By Message action menu button in panel header", () => {
        cy.contains("button.p-tab", "By Message").click();
        cy.get(".p-panel-header-icon").should("exist");
      });
    });
  });
}

{
  describe("Filtered Page", () => {
    const baseUrl = "http://localhost:8080/deltafile/filtered";

    describe("Shared Page Tests", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("loads the page with correct heading", () => {
        cy.get("h2").contains("Filtered");
      });

      it("displays Refresh button", () => {
        cy.contains("Refresh").should("exist");
      });

      it("displays filter sidebar", () => {
        cy.get(".filter-sidebar").should("exist");
      });

      it("displays filter sidebar tab", () => {
        cy.get(".filter-sidebar-tab").contains("Filters").should("exist");
      });

      it("displays tabs", () => {
        cy.get(".p-tabs").should("exist");
      });

      it("displays All tab", () => {
        cy.contains("button.p-tab", "All").should("exist");
      });

      it("displays By Flow tab", () => {
        cy.contains("button.p-tab", "By Flow").should("exist");
      });

      it("displays By Cause tab", () => {
        cy.contains("button.p-tab", "By Cause").should("exist");
      });

      it("page container exists", () => {
        cy.get(".filtered-page").should("exist");
      });

      it("renders correctly at default viewport", () => {
        cy.get(".filtered-page").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get(".filtered-page").should("be.visible");
      });

      it("All tab displays DeltaFiles panel", () => {
        cy.get("span.p-panel-title").contains("DeltaFiles");
      });

      it("can switch to By Flow tab", () => {
        cy.contains("button.p-tab", "By Flow").click();
        cy.get("span.p-panel-title").contains("DeltaFiles by Flow");
      });

      it("can switch to By Cause tab", () => {
        cy.contains("button.p-tab", "By Cause").click();
        cy.get("span.p-panel-title").contains("DeltaFiles by Cause");
      });
    });
  });
}

{
  describe("Queue Metrics Page", () => {
    const baseUrl = "http://localhost:8080/metrics/queues";

    describe("Shared Page Tests", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("loads the page with default settings", () => {
        cy.get("h2").contains("Queue Metrics");
        cy.contains("Action Queues").should("exist");
      });

      it("displays the Action Queues data table", () => {
        cy.get(".p-datatable").should("exist");
      });

      it("displays the Running Tasks panel", () => {
        cy.contains("Running Tasks").should("exist");
      });

      it("displays all expected table columns when not rolled up", () => {
        cy.get(".p-datatable-thead").first().within(() => {
          cy.contains("Flow");
          cy.contains("Type");
          cy.contains("Action");
          cy.contains("Action Class");
          cy.contains("Warm");
          cy.contains("Cold");
          cy.contains("Total");
          cy.contains("Oldest");
        });
      });

      it("shows queue data in the table", () => {
        cy.get(".p-datatable-tbody tr").should("have.length.greaterThan", 0);
      });

      it("displays the toggle switch", () => {
        cy.contains("Group by Action Class").should("exist");
        cy.get(".p-toggleswitch").should("exist");
      });

      it("displays running tasks count in header", () => {
        cy.contains(/Running Tasks \(\d+\)/).should("exist");
      });

      it("displays running task columns when expanded", () => {
        cy.get(".running-tasks-panel .p-datatable-thead").within(() => {
          cy.contains("DID");
          cy.contains("Action Class");
          cy.contains("Action");
          cy.contains("Duration");
          cy.contains("Started");
          cy.contains("Worker");
        });
      });

      it("DID links exist in running tasks", () => {
        cy.get(".running-tasks-panel .p-datatable").should("exist");
      });

      it("displays page content correctly", () => {
        cy.get(".queue-metrics-page").should("exist");
      });

      it("renders correctly at default viewport", () => {
        cy.get(".queue-metrics-page").should("be.visible");
        cy.get(".p-datatable").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get(".queue-metrics-page").should("be.visible");
        cy.get(".p-datatable").should("be.visible");
      });

      it("can sort by Flow column", () => {
        cy.get(".p-datatable-thead").contains("Flow").click();
        cy.get(".p-datatable-thead").contains("Flow").parent().find(".p-datatable-sort-icon").should("exist");
      });

      it("can sort by Warm column", () => {
        cy.get(".p-datatable-thead").contains("Warm").click();
        cy.get(".p-datatable-thead").contains("Warm").parent().find(".p-datatable-sort-icon").should("exist");
      });

      it("can sort by Cold column", () => {
        cy.get(".p-datatable-thead").contains("Cold").click();
        cy.get(".p-datatable-thead").contains("Cold").parent().find(".p-datatable-sort-icon").should("exist");
      });

      it("can sort by Total column", () => {
        cy.get(".p-datatable-thead").contains("Total").click();
        cy.get(".p-datatable-thead").contains("Total").parent().find(".p-datatable-sort-icon").should("exist");
      });

      it("can toggle the running tasks panel", () => {
        cy.get(".running-tasks-panel .p-datatable").should("be.visible");
        cy.contains("Running Tasks").click();
      });
    });

    describe("Group by Action Class Toggle", () => {
      it("can toggle group by action class", () => {
        cy.visit(baseUrl);
        cy.get(".p-datatable-thead").contains("Flow").should("exist");
        cy.get(".p-toggleswitch").click();
        cy.get(".p-datatable-thead").contains("Flow").should("not.exist");
        cy.get(".p-datatable-thead").contains("Action Class").should("exist");
      });
    });
  });
}

{
  describe("System Metrics Page", () => {
    const baseUrl = "http://localhost:8080/metrics/system";

    describe("Page Structure & Elements", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("loads the page with correct heading", () => {
        cy.get("h2").contains("System Metrics");
      });

      it("displays the Nodes panel", () => {
        cy.get("span.p-panel-title").contains("Nodes");
      });

      it("displays the data table", () => {
        cy.get(".p-datatable").should("exist");
      });

      it("page container exists", () => {
        cy.get(".system-metrics-page").should("exist");
      });

      it("renders correctly at default viewport", () => {
        cy.get(".system-metrics-page").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get(".system-metrics-page").should("be.visible");
      });
    });
  });
}

{
  describe("Page Not Found", () => {
    describe("Default Not Found", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit("http://localhost:8080/this-page-does-not-exist", { failOnStatusCode: false });
      });

      it("displays Page Not Found heading", () => {
        cy.get("h2").contains("Page Not Found");
      });

      it("displays appropriate message", () => {
        cy.contains("The page you are looking for cannot be found").should("exist");
      });

      it("renders correctly at default viewport", () => {
        cy.get("h2").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get("h2").should("be.visible");
      });
    });

    describe("Service Unavailable", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit("http://localhost:8080/not-found?service=Grafana", { failOnStatusCode: false });
      });

      it("displays service unavailable heading with service name", () => {
        cy.get("h2").contains("Grafana Unavailable");
      });

      it("displays service unavailable message", () => {
        cy.contains("service is currently offline or being updated").should("exist");
      });
    });
  });

  describe("Unauthorized Page", () => {
    const baseUrl = "http://localhost:8080/unauthorized";

    describe("Page Load", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("displays Access Denied heading", () => {
        cy.get("h2").contains("Access Denied");
      });

      it("displays unauthorized message", () => {
        cy.contains("You are not authorized to view the requested page").should("exist");
      });

      it("renders correctly at default viewport", () => {
        cy.get("h2").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get("h2").should("be.visible");
      });
    });
  });
}
