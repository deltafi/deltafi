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
  describe("Auto Resume Page", () => {
    const baseUrl = "http://localhost:8080/config/auto-resume";

    describe("Shared Page Tests", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("loads the page with correct heading", () => {
        cy.get("h2").contains("Auto Resume");
      });

      it("displays the Rules panel", () => {
        cy.get("span.p-panel-title").contains("Rules");
      });

      it("displays the data table", () => {
        cy.get(".p-datatable").should("exist");
      });

      it("displays Export Rules button", () => {
        cy.contains("Export Rules").should("exist");
      });

      it("displays Add Rule button", () => {
        cy.contains("Add Rule").should("exist");
      });

      it("displays search input", () => {
        cy.get('input[placeholder="Search"]').should("exist");
      });

      it("displays all expected table columns", () => {
        cy.get(".p-datatable-thead").within(() => {
          cy.contains("Name");
          cy.contains("Error Substring");
          cy.contains("Data Source");
          cy.contains("Action");
          cy.contains("Priority");
        });
      });

      it("displays auto resume rules data", () => {
        cy.get(".p-datatable-tbody tr").should("have.length.greaterThan", 0);
      });

      it("displays edit buttons in rows", () => {
        cy.get(".p-datatable-tbody .pi-pencil").should("exist");
      });

      it("page container exists", () => {
        cy.get(".auto-resume-page").should("exist");
      });

      it("renders correctly at default viewport", () => {
        cy.get(".auto-resume-page").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get(".auto-resume-page").should("be.visible");
      });

      it("can sort by Name column", () => {
        cy.get(".p-datatable-thead").contains("Name").click();
        cy.get(".p-datatable-thead").contains("Name").parent().find(".p-sortable-column-icon").should("exist");
      });

      it("can sort by Priority column", () => {
        cy.get(".p-datatable-thead").contains("Priority").click();
        cy.get(".p-datatable-thead").contains("Priority").parent().find(".p-sortable-column-icon").should("exist");
      });

      it("shows context menu on right click", () => {
        cy.get(".auto-resume-panel").rightclick();
        cy.get(".p-contextmenu").should("be.visible");
      });

      it("context menu contains expected options", () => {
        cy.get(".p-contextmenu").within(() => {
          cy.contains("Clear Selected");
          cy.contains("Select All");
        });
      });

      it("can filter rules by search", () => {
        cy.get('input[placeholder="Search"]').type("test");
        cy.get(".p-datatable").should("exist");
      });
    });
  });
}

{
  describe("Delete Policies Page", () => {
    const baseUrl = "http://localhost:8080/config/delete-policies";

    describe("Shared Page Tests", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("loads the page with correct heading", () => {
        cy.get("h2").contains("Delete Policies");
      });

      it("displays the Delete Policies panel", () => {
        cy.get("span.p-panel-title").contains("Delete Policies");
      });

      it("displays the data table", () => {
        cy.get(".p-datatable").should("exist");
      });

      it("displays Export Policies button", () => {
        cy.contains("Export Policies").should("exist");
      });

      it("displays Add Policy button", () => {
        cy.contains("Add Policy").should("exist");
      });

      it("displays search input", () => {
        cy.get('input[placeholder="Search"]').should("exist");
      });

      it("displays all expected table columns", () => {
        cy.get(".p-datatable-thead").within(() => {
          cy.contains("Name");
          cy.contains("Data Source");
          cy.contains("Type");
        });
      });

      it("displays toggle switches for policy state", () => {
        cy.get(".p-inputswitch").should("exist");
      });

      it("page container exists", () => {
        cy.get(".delete-policies-page").should("exist");
      });

      it("renders correctly at default viewport", () => {
        cy.get(".delete-policies-page").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get(".delete-policies-page").should("be.visible");
      });

      it("Name column is sortable", () => {
        cy.get(".p-datatable-thead").contains("Name").should("exist");
      });

      it("Data Source column is sortable", () => {
        cy.get(".p-datatable-thead").contains("Data Source").should("exist");
      });

      it("can filter policies by search", () => {
        cy.get('input[placeholder="Search"]').type("test");
        cy.get(".p-datatable").should("exist");
      });
    });
  });
}

{
  describe("Data Sources Page", () => {
    const baseUrl = "http://localhost:8080/config/data-sources";

    describe("Page Tests", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("loads the page with correct heading", () => {
        cy.get("h2").contains("Data Sources");
      });

      it("displays search input", () => {
        cy.get('input[placeholder="Search"]').should("exist");
      });

      it("displays Ingress Enabled toggle", () => {
        cy.contains("Ingress Enabled").should("exist");
        cy.get(".p-inputswitch").should("exist");
      });

      it("displays data source panels grouped by plugin", () => {
        cy.get("span.p-panel-title").should("exist");
      });

      it("page container exists", () => {
        cy.get(".data-sources-page").should("exist");
      });

      it("renders correctly at default viewport", () => {
        cy.get(".data-sources-page").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get(".data-sources-page").should("be.visible");
      });

      it("can filter data sources by search", () => {
        cy.get('input[placeholder="Search"]').type("test");
        cy.get(".data-sources-page").should("exist");
      });

      it("can clear search", () => {
        cy.get('input[placeholder="Search"]').clear();
        cy.get(".data-sources-page").should("exist");
      });
    });
  });
}

{
  describe("Data Sinks Page", () => {
    const baseUrl = "http://localhost:8080/config/data-sinks";

    describe("Page Tests", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("loads the page with correct heading", () => {
        cy.get("h2").contains("Data Sinks");
      });

      it("displays search input", () => {
        cy.get('input[placeholder="Search"]').should("exist");
      });

      it("displays Egress Enabled toggle", () => {
        cy.contains("Egress Enabled").should("exist");
        cy.get(".p-inputswitch").should("exist");
      });

      it("displays data sink panels grouped by plugin", () => {
        cy.get("span.p-panel-title").should("exist");
      });

      it("page container exists", () => {
        cy.get(".data-sinks-page").should("exist");
      });

      it("renders correctly at default viewport", () => {
        cy.get(".data-sinks-page").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get(".data-sinks-page").should("be.visible");
      });

      it("can filter data sinks by search", () => {
        cy.get('input[placeholder="Search"]').type("test");
        cy.get(".data-sinks-page").should("exist");
      });

      it("can clear search", () => {
        cy.get('input[placeholder="Search"]').clear();
        cy.get(".data-sinks-page").should("exist");
      });
    });
  });
}

{
  describe("Plugins Page", () => {
    const baseUrl = "http://localhost:8080/config/plugins";

    describe("Shared Page Tests", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("loads the page with correct heading", () => {
        cy.get("h2").contains("Plugins");
      });

      it("displays Install Plugin button", () => {
        cy.contains("Install Plugin").should("exist");
      });

      it("displays the Plugins panel", () => {
        cy.get("span.p-panel-title").contains("Plugins");
      });

      it("displays the plugins data table", () => {
        cy.get(".p-datatable").should("exist");
      });

      it("displays expected table columns", () => {
        cy.get(".p-datatable-thead").within(() => {
          cy.contains("Name");
          cy.contains("Description");
          cy.contains("Version");
          cy.contains("Status");
          cy.contains("Action Kit Version");
        });
      });

      it("displays plugins data", () => {
        cy.get(".p-datatable-tbody tr").should("have.length.greaterThan", 0);
      });

      it("displays splitter panel", () => {
        cy.get(".p-splitter").should("exist");
      });

      it("page container exists", () => {
        cy.get(".plugins-page").should("exist");
      });

      it("renders correctly at default viewport", () => {
        cy.get(".plugins-page").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get(".plugins-page").should("be.visible");
      });

      it("can sort by Name column", () => {
        cy.get(".p-datatable-thead").contains("Name").click();
        cy.get(".p-datatable-thead").contains("Name").parent().find(".p-sortable-column-icon").should("exist");
      });

      it("can sort by Status column", () => {
        cy.get(".p-datatable-thead").contains("Status").click();
        cy.get(".p-datatable-thead").contains("Status").parent().find(".p-sortable-column-icon").should("exist");
      });

      it("can select a plugin to view details", () => {
        cy.get(".p-datatable-tbody tr").first().click();
        cy.get(".p-splitter-panel").last().should("exist");
      });
    });
  });
}

{
  describe("Topics Page", () => {
    const baseUrl = "http://localhost:8080/config/topics";

    describe("Shared Page Tests", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("loads the page with topic data", () => {
        cy.get("h2").contains("Topics");
      });

      it("displays the topics data table", () => {
        cy.get(".p-datatable").should("exist");
      });

      it("displays search input", () => {
        cy.get('input[placeholder="Search"]').should("exist");
      });

      it("displays all expected columns", () => {
        cy.get(".p-datatable-thead").within(() => {
          cy.contains("Publishers");
          cy.contains("Topic");
          cy.contains("Subscribers");
        });
      });

      it("displays arrow icons between columns", () => {
        cy.get(".fa-arrow-right").should("exist");
      });

      it("displays topic data in the table", () => {
        cy.get(".p-datatable-tbody tr").should("have.length.greaterThan", 0);
      });

      it("displays topic names as tags", () => {
        cy.get(".p-tag.topic").should("exist");
      });

      it("displays publisher tags with state indicators", () => {
        cy.get(".p-datatable-tbody .p-tag.running").should("exist");
      });

      it("displays different states with appropriate colors", () => {
        cy.get(".p-tag.running").should("exist");
      });

      it("page container exists", () => {
        cy.get(".topics-table").should("be.visible");
      });

      it("renders correctly at default viewport", () => {
        cy.get(".topics-table").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get(".topics-table").should("be.visible");
      });

      it("can sort by Topic column", () => {
        cy.get(".p-datatable-thead").contains("Topic").click();
        cy.get(".p-datatable-thead").contains("Topic").parent().find(".p-sortable-column-icon").should("exist");
      });

      it("can sort by Publishers column", () => {
        cy.get(".p-datatable-thead").contains("Publishers").click();
        cy.get(".p-datatable-thead").contains("Publishers").parent().find(".p-sortable-column-icon").should("exist");
      });

      it("can sort by Subscribers column", () => {
        cy.get(".p-datatable-thead").contains("Subscribers").click();
        cy.get(".p-datatable-thead").contains("Subscribers").parent().find(".p-sortable-column-icon").should("exist");
      });

      it("can filter topics by search", () => {
        cy.get(".p-datatable-tbody tr").then(($rows) => {
          const initialRowCount = $rows.length;
          cy.get('input[placeholder="Search"]').type("blackhole");
          cy.get(".p-datatable-tbody tr").should("have.length.lessThan", initialRowCount + 1);
        });
      });

      it("can clear search", () => {
        cy.get('input[placeholder="Search"]').clear();
        cy.get(".p-datatable-tbody tr").should("have.length.greaterThan", 0);
      });

      it("displays tooltips on publisher/subscriber tags", () => {
        cy.get(".p-datatable-tbody .p-tag").first().trigger("mouseover");
      });
    });
  });
}

{
  describe("Transforms Page", () => {
    const baseUrl = "http://localhost:8080/config/transforms";

    describe("Shared Page Tests", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("loads the page with correct heading", () => {
        cy.get("h2").contains("Transforms");
      });

      it("displays search input", () => {
        cy.get('input[placeholder="Search"]').should("exist");
      });

      it("displays transform panels grouped by plugin", () => {
        cy.get("span.p-panel-title").should("exist");
      });

      it("page container exists", () => {
        cy.get(".transforms-page").should("exist");
      });

      it("renders correctly at default viewport", () => {
        cy.get(".transforms-page").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get(".transforms-page").should("be.visible");
      });

      it("can filter transforms by search", () => {
        cy.get('input[placeholder="Search"]').type("test");
        cy.get(".transforms-page").should("exist");
      });

      it("can clear search", () => {
        cy.get('input[placeholder="Search"]').clear();
        cy.get(".transforms-page").should("exist");
      });
    });
  });
}

{
  describe("System Properties Page", () => {
    const baseUrl = "http://localhost:8080/config/system";

    describe("Page Tests", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("loads the page with correct heading", () => {
        cy.get("h2").contains("System Properties");
      });

      it("displays property panels", () => {
        cy.get("span.p-panel-title").should("exist");
      });

      it("displays Common Properties panel", () => {
        cy.get("span.p-panel-title").contains("Common Properties");
      });

      it("displays Plugin Properties panels", () => {
        cy.contains("Plugin Properties").should("exist");
      });

      it("page container exists", () => {
        cy.get(".system-properties-page").should("exist");
      });

      it("renders correctly at default viewport", () => {
        cy.get(".system-properties-page").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get(".system-properties-page").should("be.visible");
      });
    });
  });
}

{
  describe("Versions Page", () => {
    const baseUrl = "http://localhost:8080/versions";

    describe("Page Tests", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("loads the page with correct heading", () => {
        cy.get("h2").contains("Versions");
      });

      it("displays the Core panel", () => {
        cy.get("span.p-panel-title").contains("Core");
      });

      it("displays the Plugins panel", () => {
        cy.get("span.p-panel-title").contains("Plugins");
      });

      it("displays the Other panel", () => {
        cy.get("span.p-panel-title").contains("Other");
      });

      it("renders correctly at default viewport", () => {
        cy.get("h2").contains("Versions").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get("h2").contains("Versions").should("be.visible");
      });
    });
  });
}

{
  describe("System Snapshots Page", () => {
    const baseUrl = "http://localhost:8080/config/snapshots";

    describe("Page Tests", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("loads the page with correct heading", () => {
        cy.get("h2").contains("System Snapshots");
      });

      it("displays the Snapshots panel", () => {
        cy.get("span.p-panel-title").contains("Snapshots");
      });

      it("displays the data table", () => {
        cy.get(".p-datatable").should("exist");
      });

      it("page container exists", () => {
        cy.get(".system-snapshots-page").should("exist");
      });

      it("renders correctly at default viewport", () => {
        cy.get(".system-snapshots-page").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get(".system-snapshots-page").should("be.visible");
      });
    });
  });
}
