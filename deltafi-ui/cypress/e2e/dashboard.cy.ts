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

describe("Dashboard Page", () => {
  const baseUrl = "http://localhost:8080";

  // Read-only tests - share a single page visit
  describe("Page Structure & Elements", { testIsolation: false }, () => {
    retryableBefore(() => {
      cy.visit(baseUrl);
    });

    it("loads the page with correct heading", () => {
      cy.get("h2").contains("Dashboard");
    });

    it("displays Refresh button", () => {
      cy.contains("Refresh").should("exist");
    });

    it("displays DeltaFile Stats panel", () => {
      cy.get("span.p-panel-title").contains("DeltaFile Stats");
    });

    it("displays Metrics panel", () => {
      cy.get("span.p-panel-title").contains("Metrics");
    });

    it("displays Installed Plugins panel", () => {
      cy.get("span.p-panel-title").contains("Installed Plugins");
    });

    it("displays External Links panel", () => {
      cy.get("span.p-panel-title").contains("External Links");
    });

    it("page container exists", () => {
      cy.get(".dashboard-page").should("exist");
    });
  });

  // Refresh functionality
  describe("Refresh Functionality", () => {
    it("can click refresh button", () => {
      cy.visit(baseUrl);
      cy.contains("Refresh").click();
      cy.get(".dashboard-page").should("exist");
    });
  });

  // Responsive Layout
  describe("Responsive Layout", () => {
    it("renders correctly at default viewport", () => {
      cy.visit(baseUrl);
      cy.get(".dashboard-page").should("be.visible");
    });

    it("renders correctly at smaller viewport", () => {
      cy.viewport(1024, 768);
      cy.visit(baseUrl);
      cy.get(".dashboard-page").should("be.visible");
    });
  });
});
