/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>

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

describe("DeltaFile Viewer Page", () => {
  const baseUrl = "http://localhost:8080/deltafile/viewer";
  const testDid = "2ace8f8a-60d6-4211-894a-0cd7455bf599";

  // Form view tests (no DID)
  describe("Form View (No DID)", { testIsolation: false }, () => {
    before(() => {
      cy.visit(baseUrl);
    });

    it("loads the page with correct heading", () => {
      cy.get("h2").contains("DeltaFile Viewer");
    });

    it("displays DID input field", () => {
      cy.get('input[placeholder="DID (UUID)"]').should("exist");
    });

    it("displays View button", () => {
      cy.contains("View").should("exist");
    });

    it("page container exists", () => {
      cy.get(".deltafile-viewer-page").should("exist");
    });
  });

  // DID validation tests
  describe("DID Input Validation", { testIsolation: false }, () => {
    before(() => {
      cy.visit(baseUrl);
    });

    it("shows error for invalid UUID", () => {
      cy.get('input[placeholder="DID (UUID)"]').type("invalid-uuid");
      cy.contains("Invalid UUID").should("exist");
    });

    it("accepts valid UUID after clearing", () => {
      cy.get('input[placeholder="DID (UUID)"]').clear().type(testDid);
      cy.contains("Invalid UUID").should("not.exist");
    });
  });

  // Viewer with DID tests
  describe("Viewer with DID", { testIsolation: false }, () => {
    before(() => {
      cy.visit(`${baseUrl}/${testDid}`);
    });

    it("loads the DeltaFile data", () => {
      cy.get(".deltafile-viewer-page").should("exist");
    });

    it("displays Refresh button", () => {
      cy.contains("Refresh").should("exist");
    });

    it("displays Menu button", () => {
      cy.contains("Menu").should("exist");
    });

    it("displays Parent DeltaFiles panel", () => {
      cy.get("span.p-panel-title").contains("Parent DeltaFiles");
    });

    it("displays Child DeltaFiles panel", () => {
      cy.get("span.p-panel-title").contains("Child DeltaFiles");
    });

    it("displays Annotations panel", () => {
      cy.get("span.p-panel-title").contains("Annotations");
    });

    it("displays Flow Graph tab", () => {
      cy.contains("span.p-tabview-title", "Flow Graph").should("exist");
    });

    it("displays Flow Table tab", () => {
      cy.contains("span.p-tabview-title", "Flow Table").should("exist");
    });

    it("displays Flow Trace tab", () => {
      cy.contains("span.p-tabview-title", "Flow Trace").should("exist");
    });
  });

  // Navigation test
  describe("Navigation", () => {
    it("can navigate to DeltaFile from form", () => {
      cy.visit(baseUrl);
      cy.get('input[placeholder="DID (UUID)"]').type(testDid);
      cy.get(".p-inputgroup").contains("View").click();
      cy.url().should("include", testDid);
    });
  });

  // Responsive Layout
  describe("Responsive Layout", () => {
    it("renders correctly at default viewport", () => {
      cy.visit(`${baseUrl}/${testDid}`);
      cy.get(".deltafile-viewer-page").should("be.visible");
    });

    it("renders correctly at smaller viewport", () => {
      cy.viewport(1024, 768);
      cy.visit(`${baseUrl}/${testDid}`);
      cy.get(".deltafile-viewer-page").should("be.visible");
    });
  });
});
