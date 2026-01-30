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

describe("Fleet Search Page", () => {
  const baseUrl = "http://localhost:8080/fleet/search";

  // Read-only tests - share a single page visit
  describe("Page Structure & Elements", { testIsolation: false }, () => {
    before(() => {
      cy.visit(baseUrl);
    });

    it("loads the page with default settings", () => {
      cy.get("h2").contains("Fleet Search");
    });

    it("displays the search date toolbar", () => {
      cy.get(".p-dropdown").should("exist");
    });

    it("displays the search options panel", () => {
      cy.contains("Search Options").should("exist");
    });

    it("displays the results panel", () => {
      cy.get("span.p-panel-title").contains("Results");
    });

    it("displays expected table columns", () => {
      cy.get(".p-datatable-thead").within(() => {
        cy.contains("System");
        cy.contains("Count");
        cy.contains("Tags");
        cy.contains("Status");
      });
    });

    it("displays results data", () => {
      cy.get(".p-datatable").should("exist");
    });

    it("search options panel exists", () => {
      cy.contains("Search Options").should("exist");
    });
  });

  // Search Options interaction
  describe("Search Options", { testIsolation: false }, () => {
    before(() => {
      cy.visit(baseUrl);
    });

    it("can interact with search options", () => {
      cy.contains("Search Options").click();
    });
  });

  // Responsive Layout
  describe("Responsive Layout", () => {
    it("renders correctly at default viewport", () => {
      cy.visit(baseUrl);
      cy.get(".fleet-search-page").should("be.visible");
    });

    it("renders correctly at smaller viewport", () => {
      cy.viewport(1024, 768);
      cy.visit(baseUrl);
      cy.get(".fleet-search-page").should("be.visible");
    });
  });
});

describe("Fleet Dashboard Page", () => {
  const baseUrl = "http://localhost:8080/fleet/dashboard";

  // Read-only tests - share a single page visit
  describe("Page Structure & Elements", { testIsolation: false }, () => {
    before(() => {
      cy.visit(baseUrl);
    });

    it("loads the page with default settings", () => {
      cy.get("h2").contains("Fleet Dashboard");
    });

    it("displays the refresh button", () => {
      cy.contains("Refresh").should("exist");
    });

    it("displays view mode selector", () => {
      cy.get(".view-mode-selector").should("exist");
    });

    it("displays display mode selector", () => {
      cy.get(".display-mode-selector").should("exist");
    });

    it("has DataFlow view option", () => {
      cy.contains("DataFlow").should("exist");
    });

    it("has System view option", () => {
      cy.contains("System").should("exist");
    });

    it("has Ingress view option", () => {
      cy.contains("Ingress").should("exist");
    });

    it("has Egress view option", () => {
      cy.contains("Egress").should("exist");
    });

    it("has Storage view option", () => {
      cy.contains("Storage").should("exist");
    });

    it("has Deleted view option", () => {
      cy.contains("Deleted").should("exist");
    });

    it("displays display mode icons", () => {
      cy.get(".display-mode-selector .p-button").should("have.length", 3);
    });

    it("displays member filters panel", () => {
      cy.contains("Member Filters").should("exist");
    });

    it("page container exists", () => {
      cy.get(".leader-dashboard-page").should("exist");
    });
  });

  // View Mode Selection
  describe("View Mode Selection", { testIsolation: false }, () => {
    before(() => {
      cy.visit(baseUrl);
    });

    it("can switch view modes", () => {
      cy.contains("System").click();
    });
  });

  // Responsive Layout
  describe("Responsive Layout", () => {
    it("renders correctly at default viewport", () => {
      cy.visit(baseUrl);
      cy.get(".leader-dashboard-page").should("be.visible");
    });

    it("renders correctly at smaller viewport", () => {
      cy.viewport(1024, 768);
      cy.visit(baseUrl);
      cy.get(".leader-dashboard-page").should("be.visible");
    });
  });
});

describe("Fleet Config Page", () => {
  const baseUrl = "http://localhost:8080/fleet/config";

  // Read-only tests - share a single page visit
  describe("Page Structure & Elements", { testIsolation: false }, () => {
    before(() => {
      cy.visit(baseUrl);
    });

    it("loads the page with default settings", () => {
      cy.get("h2").contains("Fleet Config");
    });

    it("displays the refresh button", () => {
      cy.contains("Refresh").should("exist");
    });

    it("displays view mode selector", () => {
      cy.get(".view-mode-selector").should("exist");
    });

    it("has Members view option", () => {
      cy.contains("Members").should("exist");
    });

    it("has Plugins view option", () => {
      cy.contains("Plugins").should("exist");
    });

    it("has Snapshot view option", () => {
      cy.contains("Snapshot").should("exist");
    });

    it("page container exists", () => {
      cy.get(".leader-config-page").should("exist");
    });

    it("view mode buttons exist", () => {
      cy.get(".view-mode-selector").should("exist");
    });
  });

  // View Mode Selection
  describe("View Mode Selection", { testIsolation: false }, () => {
    before(() => {
      cy.visit(baseUrl);
    });

    it("can switch to Plugins view", () => {
      cy.contains("Plugins").click();
    });

    it("can switch to Snapshot view", () => {
      cy.contains("Snapshot").click();
    });
  });

  // Responsive Layout
  describe("Responsive Layout", () => {
    it("renders correctly at default viewport", () => {
      cy.visit(baseUrl);
      cy.get(".leader-config-page").should("be.visible");
    });

    it("renders correctly at smaller viewport", () => {
      cy.viewport(1024, 768);
      cy.visit(baseUrl);
      cy.get(".leader-config-page").should("be.visible");
    });
  });
});
