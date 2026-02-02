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

describe("Plugins Page", () => {
  const baseUrl = "http://localhost:8080/config/plugins";

  // Read-only tests - share a single page visit
  describe("Page Structure & Elements", { testIsolation: false }, () => {
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
  });

  // Sorting tests
  describe("Table Sorting", { testIsolation: false }, () => {
    retryableBefore(() => {
      cy.visit(baseUrl);
    });

    it("can sort by Name column", () => {
      cy.get(".p-datatable-thead").contains("Name").click();
      cy.get(".p-datatable-thead")
        .contains("Name")
        .parent()
        .find(".p-sortable-column-icon")
        .should("exist");
    });

    it("can sort by Status column", () => {
      cy.get(".p-datatable-thead").contains("Status").click();
      cy.get(".p-datatable-thead")
        .contains("Status")
        .parent()
        .find(".p-sortable-column-icon")
        .should("exist");
    });
  });

  // Plugin selection
  describe("Plugin Selection", () => {
    it("can select a plugin to view details", () => {
      cy.visit(baseUrl);
      cy.get(".p-datatable-tbody tr").first().click();
      cy.get(".p-splitter-panel").last().should("exist");
    });
  });

  // Responsive Layout
  describe("Responsive Layout", () => {
    it("renders correctly at default viewport", () => {
      cy.visit(baseUrl);
      cy.get(".plugins-page").should("be.visible");
    });

    it("renders correctly at smaller viewport", () => {
      cy.viewport(1024, 768);
      cy.visit(baseUrl);
      cy.get(".plugins-page").should("be.visible");
    });
  });
});
