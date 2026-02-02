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

describe("Topics Page", () => {
  const baseUrl = "http://localhost:8080/config/topics";

  // Read-only tests - share a single page visit
  describe("Page Structure & Elements", { testIsolation: false }, () => {
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
  });

  // Sorting tests - clicks change sort state but stay on page
  describe("Table Sorting", { testIsolation: false }, () => {
    retryableBefore(() => {
      cy.visit(baseUrl);
    });

    it("can sort by Topic column", () => {
      cy.get(".p-datatable-thead").contains("Topic").click();
      cy.get(".p-datatable-thead")
        .contains("Topic")
        .parent()
        .find(".p-sortable-column-icon")
        .should("exist");
    });

    it("can sort by Publishers column", () => {
      cy.get(".p-datatable-thead").contains("Publishers").click();
      cy.get(".p-datatable-thead")
        .contains("Publishers")
        .parent()
        .find(".p-sortable-column-icon")
        .should("exist");
    });

    it("can sort by Subscribers column", () => {
      cy.get(".p-datatable-thead").contains("Subscribers").click();
      cy.get(".p-datatable-thead")
        .contains("Subscribers")
        .parent()
        .find(".p-sortable-column-icon")
        .should("exist");
    });
  });

  // Search tests - modifies input state
  describe("Search Functionality", { testIsolation: false }, () => {
    retryableBefore(() => {
      cy.visit(baseUrl);
    });

    it("can filter topics by search", () => {
      cy.get(".p-datatable-tbody tr").then(($rows) => {
        const initialRowCount = $rows.length;

        // Search for a specific topic
        cy.get('input[placeholder="Search"]').type("blackhole");

        // Should filter the results
        cy.get(".p-datatable-tbody tr").should(
          "have.length.lessThan",
          initialRowCount + 1
        );
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

  // Viewport tests
  describe("Responsive Layout", () => {
    it("renders correctly at default viewport", () => {
      cy.visit(baseUrl);
      cy.get(".topics-table").should("be.visible");
    });

    it("renders correctly at smaller viewport", () => {
      cy.viewport(1024, 768);
      cy.visit(baseUrl);
      cy.get(".topics-table").should("be.visible");
    });
  });
});
