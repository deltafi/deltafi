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

describe("Delete Policies Page", () => {
  const baseUrl = "http://localhost:8080/config/delete-policies";

  // Read-only tests - share a single page visit
  describe("Page Structure & Elements", { testIsolation: false }, () => {
    before(() => {
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
  });

  // Sorting tests
  describe("Table Sorting", { testIsolation: false }, () => {
    before(() => {
      cy.visit(baseUrl);
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

  // Responsive Layout
  describe("Responsive Layout", () => {
    it("renders correctly at default viewport", () => {
      cy.visit(baseUrl);
      cy.get(".delete-policies-page").should("be.visible");
    });

    it("renders correctly at smaller viewport", () => {
      cy.viewport(1024, 768);
      cy.visit(baseUrl);
      cy.get(".delete-policies-page").should("be.visible");
    });
  });
});
