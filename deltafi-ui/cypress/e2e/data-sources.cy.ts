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

describe("Data Sources Page", () => {
  const baseUrl = "http://localhost:8080/config/data-sources";

  // Read-only tests - share a single page visit
  describe("Page Structure & Elements", { testIsolation: false }, () => {
    retryableBefore(() => {
      cy.visit(baseUrl);
    });

    it("loads the page with correct heading", () => {
      cy.get("h2").contains("Data Sources");
    });

    it("displays search input", () => {
      cy.get('input[placeholder="Search"]').should("exist");
    });

    it("displays Ingress Enabled toggle", () => {
      cy.contains("Ingress Enabled").should("exist");
      cy.get(".p-inputswitch").should("exist");
    });

    it("displays data source panels grouped by plugin", () => {
      cy.get("span.p-panel-title").should("exist");
    });

    it("page container exists", () => {
      cy.get(".data-sources-page").should("exist");
    });

    it("renders correctly at default viewport", () => {
      cy.get(".data-sources-page").should("be.visible");
    });

    it("renders correctly at smaller viewport", () => {
      cy.viewport(1024, 768);
      cy.get(".data-sources-page").should("be.visible");
    });
  });

  // Search functionality
  describe("Search Functionality", { testIsolation: false }, () => {
    retryableBefore(() => {
      cy.visit(baseUrl);
    });

    it("can filter data sources by search", () => {
      cy.get('input[placeholder="Search"]').type("test");
      cy.get(".data-sources-page").should("exist");
    });

    it("can clear search", () => {
      cy.get('input[placeholder="Search"]').clear();
      cy.get(".data-sources-page").should("exist");
    });
  });
});
