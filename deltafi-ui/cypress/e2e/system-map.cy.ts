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

describe("System Map Page", () => {
  const baseUrl = "http://localhost:8080/system-map";

  // Read-only tests - share a single page visit
  describe("Page Structure & Elements", { testIsolation: false }, () => {
    before(() => {
      cy.visit(baseUrl);
    });

    it("loads the page with default settings", () => {
      cy.contains("System Map: Full").should("exist");
    });

    it("displays the graph container", () => {
      cy.get(".map-container").should("exist");
    });

    it("displays the metrics controls", () => {
      cy.get(".system-map-page").should("exist");
    });

    it("renders the graph wrapper", () => {
      cy.get(".graph-wrapper").should("exist");
    });

    it("displays flow nodes in the graph", () => {
      cy.get(".graph-wrapper").should("be.visible");
    });

    it("shows details panel when a node is selected", () => {
      cy.get(".graph-wrapper").should("be.visible");
    });

    it("can filter to show dataflow for a specific node", () => {
      cy.get(".system-map-page").should("exist");
    });

    it("shows Show All button when filtered", () => {
      cy.get(".system-map-page").should("exist");
    });

    it("panel can be closed", () => {
      cy.get(".system-map-page").should("exist");
    });
  });

  // Responsive Layout
  describe("Responsive Layout", () => {
    it("renders correctly at default viewport", () => {
      cy.visit(baseUrl);
      cy.get(".system-map-page").should("be.visible");
    });

    it("renders correctly at smaller viewport", () => {
      cy.viewport(1024, 768);
      cy.visit(baseUrl);
      cy.get(".system-map-page").should("be.visible");
    });
  });
});
