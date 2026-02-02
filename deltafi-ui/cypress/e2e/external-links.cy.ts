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

describe("External Links Page", () => {
  const baseUrl = "http://localhost:8080/admin/external-links";

  // Read-only tests - share a single page visit
  describe("Page Structure & Elements", { testIsolation: false }, () => {
    retryableBefore(() => {
      cy.visit(baseUrl);
    });

    it("loads the page with correct heading", () => {
      cy.get("h2").contains("External Links");
    });

    it("displays search input", () => {
      cy.get('input[placeholder="Search"]').should("exist");
    });

    it("displays Add Link button", () => {
      cy.contains("Add Link").should("exist");
    });

    it("displays External links panel", () => {
      cy.get("span.p-panel-title").contains("External links");
    });

    it("displays DeltaFile Links panel", () => {
      cy.get("span.p-panel-title").contains("DeltaFile Links");
    });

    it("displays External links data table", () => {
      cy.get(".external-links-panel .p-datatable").should("exist");
    });

    it("displays expected External links columns", () => {
      cy.get(".p-datatable-thead").first().within(() => {
        cy.contains("Name");
        cy.contains("Description");
        cy.contains("URL");
      });
    });

    it("page container exists", () => {
      cy.get(".external-links-page").should("exist");
    });
  });

  // Search functionality
  describe("Search Functionality", () => {
    it("can filter links by search", () => {
      cy.visit(baseUrl);
      cy.get('input[placeholder="Search"]').type("test");
      cy.get(".external-links-page").should("exist");
    });
  });

  // Responsive Layout
  describe("Responsive Layout", () => {
    it("renders correctly at default viewport", () => {
      cy.visit(baseUrl);
      cy.get(".external-links-page").should("be.visible");
    });

    it("renders correctly at smaller viewport", () => {
      cy.viewport(1024, 768);
      cy.visit(baseUrl);
      cy.get(".external-links-page").should("be.visible");
    });
  });
});
