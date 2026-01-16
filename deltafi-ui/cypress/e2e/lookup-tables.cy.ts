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

describe("Lookup Table Page", () => {
  const baseUrl = "http://localhost:8080/admin/lookup-tables";

  // Read-only tests - share a single page visit
  describe("Page Structure & Elements", { testIsolation: false }, () => {
    before(() => {
      cy.visit(baseUrl);
    });

    it("loads the page with default settings", () => {
      cy.get("h2").contains("Lookup Tables");
    });

    it("displays the Lookup Table panel", () => {
      cy.get("span.p-panel-title").contains("Lookup Table");
    });

    it("displays the data table", () => {
      cy.get(".p-datatable").should("exist");
    });

    it("displays all expected table columns", () => {
      cy.get(".p-datatable-thead").within(() => {
        cy.contains("Name");
        cy.contains("Key Columns");
        cy.contains("Columns");
        cy.contains("Total Rows");
        cy.contains("Backing Service Active");
        cy.contains("Refresh Duration");
        cy.contains("Service Backed");
        cy.contains("Pull Through");
        cy.contains("Last Refresh");
      });
    });

    it("displays lookup table data", () => {
      cy.get(".p-datatable-tbody tr").should("have.length.greaterThan", 0);
    });

    it("displays header buttons", () => {
      cy.get(".btn-toolbar").should("exist");
    });

    it("displays key columns as list items", () => {
      cy.get(".list-item").should("exist");
    });

    it("displays backing service toggle switches", () => {
      cy.get(".p-inputswitch").should("exist");
    });

    it("displays action buttons in each row", () => {
      cy.get(".p-datatable-tbody tr").first().within(() => {
        cy.get("button, .p-button").should("exist");
      });
    });

    it("name column contains links to detail page", () => {
      cy.get(".p-datatable-tbody tr")
        .first()
        .within(() => {
          cy.get("a")
            .should("have.attr", "href")
            .and("include", "/admin/lookup-table/");
        });
    });

    it("page container exists", () => {
      cy.get(".lookup-tables-page").should("be.visible");
    });
  });

  // Sorting tests - clicks change sort state but stay on page
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
  });

  // Navigation test - leaves the page
  describe("Navigation to Detail Page", () => {
    it("clicking table name navigates to detail page", () => {
      cy.visit(baseUrl);
      cy.get(".p-datatable-tbody tr")
        .first()
        .within(() => {
          cy.get("a").first().click();
        });
      cy.url().should("include", "/admin/lookup-table/");
    });
  });

  // Viewport tests
  describe("Responsive Layout", () => {
    it("renders correctly at default viewport", () => {
      cy.visit(baseUrl);
      cy.get(".lookup-tables-page").should("be.visible");
    });

    it("renders correctly at smaller viewport", () => {
      cy.viewport(1024, 768);
      cy.visit(baseUrl);
      cy.get(".lookup-tables-page").should("be.visible");
    });
  });
});
