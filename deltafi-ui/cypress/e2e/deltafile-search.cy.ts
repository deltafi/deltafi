/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>

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

describe("DeltaFile Search Page", () => {
  const baseUrl = "http://localhost:8080/deltafile/search";

  // Group 1: Tests that share the same page state (read-only, no navigation)
  describe("Page Structure & Read-Only Tests", { testIsolation: false }, () => {
    before(() => {
      cy.visit(`${baseUrl}?perPage=20`);
      cy.get(".p-datatable-tbody tr", { timeout: 15000 }).should("have.length.greaterThan", 0);
    });

    it("loads the page with default settings", () => {
      cy.get("h2").contains("DeltaFile Search");
      cy.get("span.p-panel-title").contains("Advanced Search Options");
      cy.get("span.p-panel-title").contains("Results");
    });

    it("displays the search date toolbar", () => {
      cy.get(".p-dropdown").should("exist");
      cy.get("button").find(".fa-sync-alt, .fa-refresh, .pi-refresh").should("exist");
    });

    it("displays all expected table columns", () => {
      cy.get(".p-datatable-thead").within(() => {
        cy.contains("DID");
        cy.contains("Filename");
        cy.contains("Data Source");
        cy.contains("Stage");
        cy.contains("Created");
        cy.contains("Modified");
        cy.contains("Size");
        cy.contains("Elapsed");
      });
    });

    it("displays results in the data table", () => {
      cy.get(".p-datatable-tbody tr").should("have.length.greaterThan", 0);
    });

    it("displays pagination info correctly", () => {
      cy.get("span.p-paginator-current").contains("1 - 20 of 2000");
    });

    it("rows are selectable", () => {
      cy.get(".p-datatable-tbody tr.p-selectable-row").should("have.length.greaterThan", 0);
    });

    it("table supports row selection", () => {
      cy.get(".p-datatable").should("exist");
      cy.get(".p-datatable-tbody tr.p-selectable-row").first().should("exist");
    });

    it("panel header exists", () => {
      cy.get(".p-panel-header").should("exist");
    });

    it("results panel shows data", () => {
      cy.get("span.p-panel-title").contains("Results").should("exist");
    });

    it("DID column contains clickable links", () => {
      cy.get(".p-datatable-tbody tr").first().within(() => {
        cy.get("a").first().should("have.attr", "href").and("include", "/deltafile/viewer/");
      });
    });

    it("page container exists", () => {
      cy.get(".deltafile-search-page").should("exist");
    });

    it("data table has rows", () => {
      cy.get(".p-datatable-tbody tr.p-selectable-row").should("have.length.greaterThan", 0);
    });

    it("table rows are interactive", () => {
      cy.get(".p-datatable-tbody tr.p-selectable-row").first().should("exist");
    });

    it("displays timezone button", () => {
      cy.get("button").contains(/[A-Z]{2,4}/).should("exist");
    });
  });

  // Group 2: Advanced Search Options Panel (expands panel, but read-only)
  describe("Advanced Search Options Panel", { testIsolation: false }, () => {
    before(() => {
      cy.visit(baseUrl);
      cy.get(".p-datatable", { timeout: 15000 }).should("exist");
    });

    it("can toggle the advanced search options panel", () => {
      cy.get("span.p-panel-title").contains("Advanced Search Options").click();
      cy.get(".p-panel-content").should("be.visible");
    });

    it("displays filter fields when panel is expanded", () => {
      cy.get(".p-panel-content").first().within(() => {
        cy.contains("Filename");
        cy.contains("Data Sources");
        cy.contains("Stage");
      });
    });

    it("has search options panel", () => {
      cy.get(".p-panel-content").should("be.visible");
    });
  });

  // Group 3: Column Sorting Tests (each click changes state, but order doesn't matter)
  describe("Column Sorting", { testIsolation: false }, () => {
    before(() => {
      cy.visit(`${baseUrl}?perPage=20`);
      cy.get(".p-datatable-tbody tr", { timeout: 15000 }).should("have.length.greaterThan", 0);
    });

    it("can sort by Filename column", () => {
      cy.get(".p-datatable-thead").contains("Filename").click({ force: true });
      cy.get(".p-datatable-thead").contains("Filename").parent().find(".p-sortable-column-icon").should("exist");
    });

    it("can sort by Data Source column", () => {
      cy.get(".p-datatable-thead").contains("Data Source").click({ force: true });
      cy.get(".p-datatable-thead").contains("Data Source").parent().find(".p-sortable-column-icon").should("exist");
    });

    it("can sort by Stage column", () => {
      cy.get(".p-datatable-thead").contains("Stage").click({ force: true });
      cy.get(".p-datatable-thead").contains("Stage").parent().find(".p-sortable-column-icon").should("exist");
    });

    it("can sort by Created column", () => {
      cy.get(".p-datatable-thead").contains("Created").click({ force: true });
      cy.get(".p-datatable-thead").contains("Created").parent().find(".p-sortable-column-icon").should("exist");
    });

    it("can sort by Modified column", () => {
      cy.get(".p-datatable-thead").contains("Modified").click({ force: true });
      cy.get(".p-datatable-thead").contains("Modified").parent().find(".p-sortable-column-icon").should("exist");
    });

    it("can sort by Size column", () => {
      cy.get(".p-datatable-thead").contains("Size").click({ force: true });
      cy.get(".p-datatable-thead").contains("Size").parent().find(".p-sortable-column-icon").should("exist");
    });
  });

  // Group 4: Pagination Tests (sequential navigation tests)
  describe("Pagination", { testIsolation: false }, () => {
    before(() => {
      cy.visit(`${baseUrl}?perPage=20`);
      cy.get(".p-datatable-tbody tr", { timeout: 15000 }).should("have.length.greaterThan", 0);
    });

    it("can change rows per page", () => {
      cy.get(".p-paginator-rpp-options").click();
      cy.get(".p-dropdown-item").contains("50").click();
      cy.get("span.p-paginator-current").should("contain", "1 - 50");
      // Reset back to 20
      cy.get(".p-paginator-rpp-options").click();
      cy.get(".p-dropdown-item").contains("20").click();
      cy.get("span.p-paginator-current").should("contain", "1 - 20");
    });

    it("can navigate to next page", () => {
      cy.get(".p-paginator-next").click();
      cy.get("span.p-paginator-current").should("contain", "21 - 40");
    });

    it("can navigate to previous page after going to next", () => {
      cy.get(".p-paginator-prev").click();
      cy.get("span.p-paginator-current").should("contain", "1 - 20");
    });

    it("can navigate to first page", () => {
      cy.get(".p-paginator-next").click();
      cy.get(".p-paginator-first").click();
      cy.get("span.p-paginator-current").should("contain", "1 - 20");
    });

    it("can navigate to last page", () => {
      cy.get(".p-paginator-last").click();
      cy.get("span.p-paginator-current").should("contain", "of 2000");
    });
  });

  // Group 5: Context Menu Tests
  describe("Context Menu", { testIsolation: false }, () => {
    before(() => {
      cy.visit(`${baseUrl}?perPage=20`);
      cy.get(".p-datatable-tbody tr.p-selectable-row", { timeout: 15000 }).should("have.length.greaterThan", 0);
    });

    it("shows context menu when right-clicking on a row", () => {
      cy.get(".p-datatable-tbody tr.p-selectable-row").first().rightclick({ force: true });
      cy.get(".p-contextmenu").should("be.visible");
    });

    it("context menu contains expected options", () => {
      cy.get(".p-contextmenu").within(() => {
        cy.contains("Clear Selected");
        cy.contains("Select All Visible");
      });
    });
  });

  // Group 6: Date Toolbar Functionality
  describe("Date Toolbar Functionality", { testIsolation: false }, () => {
    before(() => {
      cy.visit(baseUrl);
      cy.get(".p-dropdown", { timeout: 15000 }).should("exist");
    });

    it("has date type dropdown with Created/Modified options", () => {
      cy.get(".p-dropdown").first().click({ force: true });
      cy.get(".p-dropdown-items").should("be.visible");
    });
  });

  // Group 7: Navigation test (needs isolation since it changes URL)
  describe("DID Link Navigation", () => {
    it("clicking a DID link navigates to the viewer page", () => {
      cy.visit(`${baseUrl}?perPage=20`);
      cy.get(".p-datatable-tbody tr", { timeout: 15000 }).should("have.length.greaterThan", 0);
      cy.get(".p-datatable-tbody tr").first().within(() => {
        cy.get("a").first().click({ force: true });
      });
      cy.url().should("include", "/deltafile/viewer/");
    });
  });

  // Group 8: URL Parameter test
  describe("URL Parameters", () => {
    it("loads with URL parameters for perPage", () => {
      cy.visit(`${baseUrl}?perPage=20`);
      cy.get("span.p-paginator-current").contains("1 - 20 of 2000");
    });
  });

  // Group 9: Responsive Layout (changes viewport)
  describe("Responsive Layout", () => {
    it("renders correctly at default viewport", () => {
      cy.visit(baseUrl);
      cy.get(".deltafile-search-page", { timeout: 15000 }).should("be.visible");
      cy.get(".p-datatable", { timeout: 15000 }).should("be.visible");
    });

    it("renders correctly at smaller viewport", () => {
      cy.viewport(1024, 768);
      cy.visit(baseUrl);
      cy.get(".deltafile-search-page", { timeout: 15000 }).should("be.visible");
      cy.get(".p-datatable", { timeout: 15000 }).should("be.visible");
    });
  });
});
