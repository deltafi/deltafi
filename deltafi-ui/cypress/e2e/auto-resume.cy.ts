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

describe("Auto Resume Page", () => {
  const baseUrl = "http://localhost:8080/config/auto-resume";

  // Read-only tests - share a single page visit
  describe("Page Structure & Elements", { testIsolation: false }, () => {
    before(() => {
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
  });

  // Sorting tests
  describe("Table Sorting", { testIsolation: false }, () => {
    before(() => {
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

    it("can sort by Priority column", () => {
      cy.get(".p-datatable-thead").contains("Priority").click();
      cy.get(".p-datatable-thead")
        .contains("Priority")
        .parent()
        .find(".p-sortable-column-icon")
        .should("exist");
    });
  });

  // Context Menu tests
  describe("Context Menu", { testIsolation: false }, () => {
    before(() => {
      cy.visit(baseUrl);
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
  });

  // Search functionality
  describe("Search Functionality", () => {
    it("can filter rules by search", () => {
      cy.visit(baseUrl);
      cy.get('input[placeholder="Search"]').type("test");
      cy.get(".p-datatable").should("exist");
    });
  });

  // Responsive Layout
  describe("Responsive Layout", () => {
    it("renders correctly at default viewport", () => {
      cy.visit(baseUrl);
      cy.get(".auto-resume-page").should("be.visible");
    });

    it("renders correctly at smaller viewport", () => {
      cy.viewport(1024, 768);
      cy.visit(baseUrl);
      cy.get(".auto-resume-page").should("be.visible");
    });
  });
});
