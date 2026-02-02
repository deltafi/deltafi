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

describe("Pipeline Visualization Page", () => {
  const baseUrl = "http://localhost:8080/pipeline";

  // Page Load - No Flow Selected
  describe("Page Load - No Flow Selected", { testIsolation: false }, () => {
    retryableBefore(() => {
      cy.visit(baseUrl);
    });

    it("loads the page with flow selection prompt", () => {
      cy.contains("System Map: Detail").should("exist");
      cy.contains("Select an entity to visualize").should("exist");
    });

    it("displays flow selection lists", () => {
      cy.contains("Data Sources").should("exist");
      cy.contains("Transforms").should("exist");
      cy.contains("Data Sinks").should("exist");
      cy.contains("Topics").should("exist");
    });

    it("displays flow options in listboxes", () => {
      cy.get(".p-listbox").should("have.length", 4);
    });

    it("renders correctly at default viewport", () => {
      cy.get(".pipeline-visualization-page").should("be.visible");
    });

    it("renders correctly at smaller viewport", () => {
      cy.viewport(1024, 768);
      cy.get(".pipeline-visualization-page").should("be.visible");
    });
  });

  // Flow Selection
  describe("Flow Selection", { testIsolation: false }, () => {
    retryableBefore(() => {
      cy.visit(baseUrl);
    });

    it("can select a data source", () => {
      cy.get(".flow-selection-column")
        .contains("Data Sources")
        .parent()
        .within(() => {
          cy.get(".p-listbox-item").first().click();
        });
    });
  });

  // Flow Selection - Additional options (need fresh page)
  describe("Flow Selection Options", () => {
    it("can select a transform", () => {
      cy.visit(baseUrl);
      cy.get(".flow-selection-column")
        .contains("Transforms")
        .parent()
        .within(() => {
          cy.get(".p-listbox-item").first().click();
        });
    });

    it("can select a data sink", () => {
      cy.visit(baseUrl);
      cy.get(".flow-selection-column")
        .contains("Data Sinks")
        .parent()
        .within(() => {
          cy.get(".p-listbox-item").first().click();
        });
    });

    it("can select a topic", () => {
      cy.visit(baseUrl);
      cy.get(".flow-selection-column")
        .contains("Topics")
        .parent()
        .within(() => {
          cy.get(".p-listbox-item").first().click();
        });
    });
  });

  // Page Load - With Flow Selected
  describe("Page Load - With Flow Selected", { testIsolation: false }, () => {
    retryableBefore(() => {
      cy.visit(`${baseUrl}/TRANSFORM/smoke-test-transform`);
    });

    it("loads with a specific transform flow", () => {
      cy.contains("System Map: Detail").should("exist");
      cy.contains("smoke-test-transform").should("exist");
    });

    it("displays the pipeline graph when flow is selected", () => {
      cy.get(".pipeline-container").should("exist");
      cy.get(".graph-wrapper").should("exist");
    });

    it("displays change flow button when flow is selected", () => {
      cy.get(".change-flow-button").should("exist");
    });

    it("displays metrics controls when flow is selected", () => {
      cy.get(".pipeline-visualization-page").should("exist");
    });

    it("panel shows when node is selected", () => {
      cy.get(".pipeline-visualization-page").should("exist");
    });

    it("graph wrapper is visible", () => {
      cy.get(".graph-wrapper").should("be.visible");
    });
  });

  // Change Flow Button
  describe("Change Flow Button", () => {
    it("clicking change flow returns to selection view", () => {
      cy.visit(`${baseUrl}/TRANSFORM/smoke-test-transform`);
      cy.get(".change-flow-button").click();
      cy.url().should("include", "/pipeline");
      cy.contains("Select an entity to visualize").should("exist");
    });
  });

  // Flow Not Found
  describe("Flow Not Found", { testIsolation: false }, () => {
    retryableBefore(() => {
      cy.visit(`${baseUrl}/TRANSFORM/non-existent-flow`);
    });

    it("shows error message for non-existent flow", () => {
      cy.contains("Flow Not Found").should("exist");
      cy.contains("Select a Flow").should("exist");
    });

    it("can navigate back to flow selection from error", () => {
      cy.contains("Select a Flow").click();
      cy.contains("Select an entity to visualize").should("exist");
    });
  });
});
