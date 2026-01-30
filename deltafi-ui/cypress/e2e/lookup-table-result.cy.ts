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

describe("Lookup Table Result Page", () => {
  const baseUrl = "http://localhost:8080/admin/lookup-table/country-codes";

  // Read-only tests - share a single page visit
  describe("Page Structure & Elements", { testIsolation: false }, () => {
    before(() => {
      cy.visit(baseUrl);
    });

    it("loads the page with lookup table name", () => {
      cy.get("h2").contains("Lookup Tables");
      cy.get("h2").contains("country-codes");
    });

    it("displays breadcrumb navigation", () => {
      cy.get(".fa-angle-right").should("exist");
    });

    it("displays the Results panel", () => {
      cy.contains("Results").should("exist");
    });

    it("displays the data table", () => {
      cy.get(".p-datatable").should("exist");
    });

    it("displays filter section", () => {
      cy.contains("Filter:").should("exist");
    });

    it("displays clear filter button", () => {
      cy.get(".p-column-filter-menu-button").should("exist");
    });

    it("displays add filter chip button", () => {
      cy.get(".add-column-value-btn").should("exist");
    });

    it("displays paginator", () => {
      cy.get(".p-paginator").should("exist");
    });

    it("displays table data", () => {
      cy.get(".p-datatable-tbody tr").should("have.length.greaterThan", 0);
    });

    it("displays key columns with asterisk", () => {
      cy.get(".p-datatable-thead").contains("code *");
    });

    it("displays Last Updated column", () => {
      cy.get(".p-datatable-thead").contains("Last Updated");
    });

    it("displays column selector", () => {
      cy.get(".p-multiselect").should("exist");
    });

    it("displays row editor column", () => {
      cy.get(".p-row-editor-init").should("exist");
    });

    it("displays delete row button", () => {
      cy.get(".p-datatable-tbody button").should("exist");
    });
  });

  // Menu tests
  describe("Context Menu", () => {
    it("displays menu button", () => {
      cy.visit(baseUrl);
      cy.get(".fa-bars").should("exist");
    });
  });

  // Navigation test
  describe("Navigation", () => {
    it("breadcrumb links back to lookup tables list", () => {
      cy.visit(baseUrl);
      cy.get('a[href="/admin/lookup-tables/"]').should("exist");
    });
  });

  // Responsive Layout
  describe("Responsive Layout", () => {
    it("renders correctly at default viewport", () => {
      cy.visit(baseUrl);
      cy.get(".lookup-tables-results-page").should("be.visible");
    });

    it("renders correctly at smaller viewport", () => {
      cy.viewport(1024, 768);
      cy.visit(baseUrl);
      cy.get(".lookup-tables-results-page").should("be.visible");
    });
  });
});
