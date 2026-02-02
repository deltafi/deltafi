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

describe("Errors Page", () => {
  const baseUrl = "http://localhost:8080/errors";

  // Read-only tests - share a single page visit
  describe("Page Structure & Elements", { testIsolation: false }, () => {
    retryableBefore(() => {
      cy.visit(baseUrl);
    });

    it("loads the page with correct heading", () => {
      cy.get("h2").contains("Errors");
    });

    it("displays timezone button", () => {
      cy.get("button").contains(/[A-Z]{2,4}/).should("exist");
    });

    it("displays date picker", () => {
      cy.get(".time-range").should("exist");
    });

    it("displays Refresh button", () => {
      cy.contains("Refresh").should("exist");
    });

    it("displays clear filters button", () => {
      cy.get(".p-column-filter-menu-button").should("exist");
    });

    it("displays Flow dropdown", () => {
      cy.get(".flow-dropdown").should("exist");
    });

    it("displays Error Message dropdown", () => {
      cy.get(".p-dropdown").should("have.length.greaterThan", 0);
    });

    it("displays Ack status dropdown", () => {
      cy.get(".ack-dropdown").should("exist");
    });

    it("displays tabs", () => {
      cy.get(".p-tabview").should("exist");
    });

    it("displays All tab", () => {
      cy.contains("span.p-tabview-title", "All").should("exist");
    });

    it("displays By Flow tab", () => {
      cy.contains("span.p-tabview-title", "By Flow").should("exist");
    });

    it("displays By Message tab", () => {
      cy.contains("span.p-tabview-title", "By Message").should("exist");
    });

    it("page container exists", () => {
      cy.get(".errors-page").should("exist");
    });

    it("renders correctly at default viewport", () => {
      cy.get(".errors-page").should("be.visible");
    });

    it("renders correctly at smaller viewport", () => {
      cy.viewport(1024, 768);
      cy.get(".errors-page").should("be.visible");
    });
  });

  // Tab navigation tests
  describe("Tab Navigation", { testIsolation: false }, () => {
    retryableBefore(() => {
      cy.visit(baseUrl);
    });

    it("All tab displays DeltaFiles panel", () => {
      cy.get("span.p-panel-title").contains("DeltaFiles");
    });

    it("can switch to By Flow tab", () => {
      cy.contains("span.p-tabview-title", "By Flow").click();
      cy.get("span.p-panel-title").contains("DeltaFiles by Flow");
    });

    it("can switch to By Message tab", () => {
      cy.contains("span.p-tabview-title", "By Message").click();
      cy.get("span.p-panel-title").contains("DeltaFiles by Message");
    });
  });

  // All Panel Structure
  describe("All Panel Structure", () => {
    it("displays action menu button in panel header", () => {
      cy.visit(baseUrl);
      cy.get(".p-panel-header-icon").should("exist");
    });

    it("displays errors table", () => {
      cy.visit(baseUrl);
      cy.get("#errorsTable").should("exist");
    });
  });

  // By Flow Panel
  describe("By Flow Panel", () => {
    it("displays By Flow panel when tab is clicked", () => {
      cy.visit(baseUrl);
      cy.contains("span.p-tabview-title", "By Flow").click();
      cy.get("span.p-panel-title").contains("DeltaFiles by Flow");
    });

    it("displays action menu button in panel header", () => {
      cy.visit(baseUrl);
      cy.contains("span.p-tabview-title", "By Flow").click();
      cy.get(".p-panel-header-icon").should("exist");
    });
  });

  // By Message Panel
  describe("By Message Panel", () => {
    it("displays By Message panel when tab is clicked", () => {
      cy.visit(baseUrl);
      cy.contains("span.p-tabview-title", "By Message").click();
      cy.get("span.p-panel-title").contains("DeltaFiles by Message");
    });

    it("displays action menu button in panel header", () => {
      cy.visit(baseUrl);
      cy.contains("span.p-tabview-title", "By Message").click();
      cy.get(".p-panel-header-icon").should("exist");
    });
  });
});
