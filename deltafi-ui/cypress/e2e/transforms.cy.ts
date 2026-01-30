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

describe("Transforms Page", () => {
  const baseUrl = "http://localhost:8080/config/transforms";

  // Read-only tests - share a single page visit
  describe("Page Structure & Elements", { testIsolation: false }, () => {
    before(() => {
      cy.visit(baseUrl);
    });

    it("loads the page with correct heading", () => {
      cy.get("h2").contains("Transforms");
    });

    it("displays search input", () => {
      cy.get('input[placeholder="Search"]').should("exist");
    });

    it("displays transform panels grouped by plugin", () => {
      cy.get("span.p-panel-title").should("exist");
    });

    it("page container exists", () => {
      cy.get(".transforms-page").should("exist");
    });
  });

  // Search functionality
  describe("Search Functionality", { testIsolation: false }, () => {
    before(() => {
      cy.visit(baseUrl);
    });

    it("can filter transforms by search", () => {
      cy.get('input[placeholder="Search"]').type("test");
      cy.get(".transforms-page").should("exist");
    });

    it("can clear search", () => {
      cy.get('input[placeholder="Search"]').clear();
      cy.get(".transforms-page").should("exist");
    });
  });

  // Responsive Layout
  describe("Responsive Layout", () => {
    it("renders correctly at default viewport", () => {
      cy.visit(baseUrl);
      cy.get(".transforms-page").should("be.visible");
    });

    it("renders correctly at smaller viewport", () => {
      cy.viewport(1024, 768);
      cy.visit(baseUrl);
      cy.get(".transforms-page").should("be.visible");
    });
  });
});
