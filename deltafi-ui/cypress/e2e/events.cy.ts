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

describe("Events Page", () => {
  const baseUrl = "http://localhost:8080/events";

  // Read-only tests - share a single page visit
  describe("Page Structure & Elements", { testIsolation: false }, () => {
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
      cy.get(".p-column-filter-menu-button").first().should("exist");
    });

    it("displays date picker", () => {
      cy.get(".time-range").should("exist");
    });

    it("displays severity badges in table", () => {
      cy.get(".p-datatable-tbody").should("exist");
    });
  });

  // Context Menu tests
  describe("Context Menu", { testIsolation: false }, () => {
    retryableBefore(() => {
      cy.visit(baseUrl);
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
      // Close the context menu by clicking elsewhere
      cy.get("body").click();
    });

    it("can clear selection from context menu", () => {
      cy.get(".events-panel").rightclick();
      cy.get(".p-contextmenu").should("be.visible");
      cy.contains("Clear Selected").click();
    });
  });

  // Viewport tests - need separate visits
  describe("Responsive Layout", () => {
    it("renders correctly at default viewport", () => {
      cy.visit(baseUrl);
      cy.get(".events-page").should("be.visible");
    });

    it("renders correctly at smaller viewport", () => {
      cy.viewport(1024, 768);
      cy.visit(baseUrl);
      cy.get(".events-page").should("be.visible");
    });
  });
});
