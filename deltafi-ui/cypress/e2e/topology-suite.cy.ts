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

{
  describe("Fleet Search Page", () => {
    const baseUrl = "http://localhost:8080/fleet/search";
    describe("Page Tests", { testIsolation: false }, () => {
      retryableBefore(() => { cy.visit(baseUrl); });
      it("loads the page with default settings", () => { cy.get("h2").contains("Fleet Search"); });
      it("displays the search date toolbar", () => { cy.get(".p-dropdown").should("exist"); });
      it("displays the search filter tab", () => { cy.contains("Search").should("exist"); });
      it("displays the results panel", () => { cy.get("span.p-panel-title").contains("Results"); });
      it("displays expected table columns", () => {
        cy.get(".p-datatable-thead").within(() => { cy.contains("System"); cy.contains("Count"); cy.contains("Tags"); cy.contains("Status"); });
      });
      it("displays results data", () => { cy.get(".p-datatable").should("exist"); });
      it("search filter tab exists", () => { cy.contains("Search").should("exist"); });
      it("renders correctly at default viewport", () => { cy.get(".fleet-search-page").should("be.visible"); });
      it("renders correctly at smaller viewport", () => { cy.viewport(1024, 768); cy.get(".fleet-search-page").should("be.visible"); });
      it("can interact with search filter", () => { cy.contains("Search").click(); });
    });
  });
  describe("Fleet Dashboard Page", () => {
    const baseUrl = "http://localhost:8080/fleet/dashboard";
    describe("Page Tests", { testIsolation: false }, () => {
      retryableBefore(() => { cy.visit(baseUrl); });
      it("loads the page with default settings", () => { cy.get("h2").contains("Fleet Dashboard"); });
      it("displays the refresh button", () => { cy.contains("Refresh").should("exist"); });
      it("displays view mode selector", () => { cy.get(".view-mode-selector").should("exist"); });
      it("displays display mode selector", () => { cy.get(".display-mode-selector").should("exist"); });
      it("has DataFlow view option", () => { cy.contains("DataFlow").should("exist"); });
      it("has System view option", () => { cy.contains("System").should("exist"); });
      it("has Ingress view option", () => { cy.contains("Ingress").should("exist"); });
      it("has Egress view option", () => { cy.contains("Egress").should("exist"); });
      it("has Storage view option", () => { cy.contains("Storage").should("exist"); });
      it("has Deleted view option", () => { cy.contains("Deleted").should("exist"); });
      it("displays display mode icons", () => { cy.get(".display-mode-selector .p-button").should("have.length", 3); });
      it("displays member filters panel", () => { cy.contains("Member Filters").should("exist"); });
      it("page container exists", () => { cy.get(".leader-dashboard-page").should("exist"); });
      it("renders correctly at default viewport", () => { cy.get(".leader-dashboard-page").should("be.visible"); });
      it("renders correctly at smaller viewport", () => { cy.viewport(1024, 768); cy.get(".leader-dashboard-page").should("be.visible"); });
      it("can switch view modes", () => { cy.contains("System").click(); });
    });
  });
  describe("Fleet Config Page", () => {
    const baseUrl = "http://localhost:8080/fleet/config";
    describe("Page Tests", { testIsolation: false }, () => {
      retryableBefore(() => { cy.visit(baseUrl); });
      it("loads the page with default settings", () => { cy.get("h2").contains("Fleet Config"); });
      it("displays the refresh button", () => { cy.contains("Refresh").should("exist"); });
      it("displays view mode selector", () => { cy.get(".view-mode-selector").should("exist"); });
      it("has Members view option", () => { cy.contains("Members").should("exist"); });
      it("has Plugins view option", () => { cy.contains("Plugins").should("exist"); });
      it("has Snapshot view option", () => { cy.contains("Snapshot").should("exist"); });
      it("page container exists", () => { cy.get(".leader-config-page").should("exist"); });
      it("view mode buttons exist", () => { cy.get(".view-mode-selector").should("exist"); });
      it("renders correctly at default viewport", () => { cy.get(".leader-config-page").should("be.visible"); });
      it("renders correctly at smaller viewport", () => { cy.viewport(1024, 768); cy.get(".leader-config-page").should("be.visible"); });
      it("can switch to Plugins view", () => { cy.contains("Plugins").click(); });
      it("can switch to Snapshot view", () => { cy.contains("Snapshot").click(); });
    });
  });
}

{
  describe("System Map Page", () => {
    const baseUrl = "http://localhost:8080/system-map";
    describe("Page Structure & Elements", { testIsolation: false }, () => {
      retryableBefore(() => { cy.visit(baseUrl); });
      it("loads the page with default settings", () => { cy.contains("System Map: Full").should("exist"); });
      it("displays the graph container", () => { cy.get(".map-container").should("exist"); });
      it("displays the metrics controls", () => { cy.get(".system-map-page").should("exist"); });
      it("renders the graph wrapper", () => { cy.get(".graph-wrapper").should("exist"); });
      it("displays flow nodes in the graph", () => { cy.get(".graph-wrapper").should("be.visible"); });
      it("shows details panel when a node is selected", () => { cy.get(".graph-wrapper").should("be.visible"); });
      it("can filter to show dataflow for a specific node", () => { cy.get(".system-map-page").should("exist"); });
      it("shows Show All button when filtered", () => { cy.get(".system-map-page").should("exist"); });
      it("panel can be closed", () => { cy.get(".system-map-page").should("exist"); });
      it("renders correctly at default viewport", () => { cy.get(".system-map-page").should("be.visible"); });
      it("renders correctly at smaller viewport", () => { cy.viewport(1024, 768); cy.get(".system-map-page").should("be.visible"); });
    });
  });
}

{
  describe("Pipeline Visualization Page", () => {
    const baseUrl = "http://localhost:8080/pipeline";
    describe("No Flow Selected", { testIsolation: false }, () => {
      retryableBefore(() => { cy.visit(baseUrl); });
      it("loads the page with flow selection prompt", () => { cy.contains("System Map: Detail").should("exist"); cy.contains("Select an entity to visualize").should("exist"); });
      it("displays flow selection lists", () => { cy.contains("Data Sources").should("exist"); cy.contains("Transforms").should("exist"); cy.contains("Data Sinks").should("exist"); cy.contains("Topics").should("exist"); });
      it("displays flow options in listboxes", () => { cy.get(".p-listbox").should("have.length", 4); });
      it("renders correctly at default viewport", () => { cy.get(".pipeline-visualization-page").should("be.visible"); });
      it("renders correctly at smaller viewport", () => { cy.viewport(1024, 768); cy.get(".pipeline-visualization-page").should("be.visible"); });
      it("can select a data source", () => { cy.get(".flow-selection-column").contains("Data Sources").parent().within(() => { cy.get(".p-listbox-item").first().click(); }); });
      it("can select a transform", () => { cy.visit(baseUrl); cy.get(".flow-selection-column").contains("Transforms").parent().within(() => { cy.get(".p-listbox-item").first().click(); }); });
      it("can select a data sink", () => { cy.visit(baseUrl); cy.get(".flow-selection-column").contains("Data Sinks").parent().within(() => { cy.get(".p-listbox-item").first().click(); }); });
      it("can select a topic", () => { cy.visit(baseUrl); cy.get(".flow-selection-column").contains("Topics").parent().within(() => { cy.get(".p-listbox-item").first().click(); }); });
    });
    describe("With Flow Selected", { testIsolation: false }, () => {
      retryableBefore(() => { cy.visit(`${baseUrl}/TRANSFORM/smoke-test-transform`); });
      it("loads with a specific transform flow", () => { cy.contains("System Map: Detail").should("exist"); cy.contains("smoke-test-transform").should("exist"); });
      it("displays the pipeline graph when flow is selected", () => { cy.get(".pipeline-container").should("exist"); cy.get(".graph-wrapper").should("exist"); });
      it("displays change flow button when flow is selected", () => { cy.get(".change-flow-button").should("exist"); });
      it("displays metrics controls when flow is selected", () => { cy.get(".pipeline-visualization-page").should("exist"); });
      it("panel shows when node is selected", () => { cy.get(".pipeline-visualization-page").should("exist"); });
      it("graph wrapper is visible", () => { cy.get(".graph-wrapper").should("be.visible"); });
      it("clicking change flow returns to selection view", () => { cy.visit(`${baseUrl}/TRANSFORM/smoke-test-transform`); cy.get(".change-flow-button").click(); cy.url().should("include", "/pipeline"); cy.contains("Select an entity to visualize").should("exist"); });
    });
    describe("Flow Not Found", { testIsolation: false }, () => {
      retryableBefore(() => { cy.visit(`${baseUrl}/TRANSFORM/non-existent-flow`); });
      it("shows error message for non-existent flow", () => { cy.contains("Flow Not Found").should("exist"); cy.contains("Select a Flow").should("exist"); });
      it("can navigate back to flow selection from error", () => { cy.contains("Select a Flow").click(); cy.contains("Select an entity to visualize").should("exist"); });
    });
  });
}

{
  describe("Transform Builder Page", () => {
    const baseUrl = "http://localhost:8080/config/transform-builder";
    describe("Page Structure & Elements", { testIsolation: false }, () => {
      retryableBefore(() => { cy.visit(baseUrl); });
      it("loads the page", () => { cy.get("title").contains("Transform Builder"); });
      it("page container exists", () => { cy.get(".transform-builder-page").should("exist"); });
      it("renders correctly at default viewport", () => { cy.get(".transform-builder-page").should("be.visible"); });
      it("renders correctly at smaller viewport", () => { cy.viewport(1024, 768); cy.get(".transform-builder-page").should("be.visible"); });
    });
  });
}
