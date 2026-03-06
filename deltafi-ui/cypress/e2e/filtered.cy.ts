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

describe("Filtered Page", () => {
  const baseUrl = "http://localhost:8080/deltafile/filtered";

  // Read-only tests - share a single page visit
  describe("Page Structure & Elements", { testIsolation: false }, () => {
    retryableBefore(() => {
      cy.visit(baseUrl);
    });

    it("loads the page with correct heading", () => {
      cy.get("h2").contains("Filtered");
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

    it("displays filter sidebar", () => {
      cy.get(".filter-sidebar").should("exist");
    });

    it("displays filter sidebar tab", () => {
      cy.get(".filter-sidebar-tab").contains("Filters").should("exist");
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

    it("displays By Cause tab", () => {
      cy.contains("span.p-tabview-title", "By Cause").should("exist");
    });

    it("page container exists", () => {
      cy.get(".filtered-page").should("exist");
    });

    it("renders correctly at default viewport", () => {
      cy.get(".filtered-page").should("be.visible");
    });

    it("renders correctly at smaller viewport", () => {
      cy.viewport(1024, 768);
      cy.get(".filtered-page").should("be.visible");
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

    it("can switch to By Cause tab", () => {
      cy.contains("span.p-tabview-title", "By Cause").click();
      cy.get("span.p-panel-title").contains("DeltaFiles by Cause");
    });
  });
});
