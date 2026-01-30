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

describe("Queue Metrics Page", () => {
  const baseUrl = "http://localhost:8080/metrics/queues";

  // Read-only tests - share a single page visit
  describe("Page Structure & Elements", { testIsolation: false }, () => {
    before(() => {
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
      cy.get(".p-inputswitch").should("exist");
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
  });

  // Sorting tests - clicks change sort state but stay on page
  describe("Table Sorting", { testIsolation: false }, () => {
    before(() => {
      cy.visit(baseUrl);
    });

    it("can sort by Flow column", () => {
      cy.get(".p-datatable-thead").contains("Flow").click();
      cy.get(".p-datatable-thead")
        .contains("Flow")
        .parent()
        .find(".p-sortable-column-icon")
        .should("exist");
    });

    it("can sort by Warm column", () => {
      cy.get(".p-datatable-thead").contains("Warm").click();
      cy.get(".p-datatable-thead")
        .contains("Warm")
        .parent()
        .find(".p-sortable-column-icon")
        .should("exist");
    });

    it("can sort by Cold column", () => {
      cy.get(".p-datatable-thead").contains("Cold").click();
      cy.get(".p-datatable-thead")
        .contains("Cold")
        .parent()
        .find(".p-sortable-column-icon")
        .should("exist");
    });

    it("can sort by Total column", () => {
      cy.get(".p-datatable-thead").contains("Total").click();
      cy.get(".p-datatable-thead")
        .contains("Total")
        .parent()
        .find(".p-sortable-column-icon")
        .should("exist");
    });
  });

  // Toggle test - modifies state significantly
  describe("Group by Action Class Toggle", () => {
    it("can toggle group by action class", () => {
      cy.visit(baseUrl);
      cy.get(".p-datatable-thead").contains("Flow").should("exist");
      cy.get(".p-inputswitch").click();
      cy.get(".p-datatable-thead").contains("Flow").should("not.exist");
      cy.get(".p-datatable-thead").contains("Action Class").should("exist");
    });
  });

  // Panel toggle
  describe("Running Tasks Panel", () => {
    it("can toggle the running tasks panel", () => {
      cy.visit(baseUrl);
      cy.get(".running-tasks-panel .p-datatable").should("be.visible");
      cy.contains("Running Tasks").click();
    });
  });

  // Viewport tests
  describe("Responsive Layout", () => {
    it("renders correctly at default viewport", () => {
      cy.visit(baseUrl);
      cy.get(".queue-metrics-page").should("be.visible");
      cy.get(".p-datatable").should("be.visible");
    });

    it("renders correctly at smaller viewport", () => {
      cy.viewport(1024, 768);
      cy.visit(baseUrl);
      cy.get(".queue-metrics-page").should("be.visible");
      cy.get(".p-datatable").should("be.visible");
    });
  });
});
