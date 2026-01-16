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

describe("DeltaFile Upload Page", () => {
  const baseUrl = "http://localhost:8080/deltafile/upload";

  // Read-only tests - share a single page visit
  describe("Page Structure & Elements", { testIsolation: false }, () => {
    before(() => {
      cy.visit(baseUrl);
    });

    it("loads the page with correct heading", () => {
      cy.get("h2").contains("Upload Files");
    });

    it("displays the Metadata panel", () => {
      cy.get(".metadata-panel").should("exist");
    });

    it("displays Add Metadata Field button", () => {
      cy.contains("Add Metadata Field").should("exist");
    });

    it("displays Clear button", () => {
      cy.get(".metadata-panel").within(() => {
        cy.contains("Clear").should("exist");
      });
    });

    it("displays Download Metadata button", () => {
      cy.contains("Download Metadata").should("exist");
    });

    it("displays Upload Metadata button", () => {
      cy.contains("Upload Metadata").should("exist");
    });

    it("displays Import Metadata button", () => {
      cy.contains("Import Metadata").should("exist");
    });

    it("displays Data Source dropdown", () => {
      cy.get(".p-dropdown").should("exist");
    });

    it("displays file upload component", () => {
      cy.get(".p-fileupload").should("exist");
    });

    it("displays Add Files button", () => {
      cy.contains("Add Files").should("exist");
    });

    it("displays drag and drop message", () => {
      cy.contains("Drag and drop files").should("exist");
    });

    it("page container exists", () => {
      cy.get(".deltafile-upload-page").should("exist");
    });
  });

  // Metadata interaction tests
  describe("Metadata Functionality", { testIsolation: false }, () => {
    before(() => {
      cy.visit(baseUrl);
    });

    it("can add metadata field", () => {
      cy.contains("Add Metadata Field").click();
      cy.get('input[placeholder="Key"]').should("exist");
      cy.get('input[placeholder="Value"]').should("exist");
    });

    it("can add multiple metadata fields", () => {
      cy.contains("Add Metadata Field").click();
      cy.contains("Add Metadata Field").click();
      cy.get('input[placeholder="Key"]').should("have.length", 3);
    });

    it("metadata field has remove button", () => {
      cy.get(".metadata-panel button").should("exist");
    });
  });

  // Responsive Layout
  describe("Responsive Layout", () => {
    it("renders correctly at default viewport", () => {
      cy.visit(baseUrl);
      cy.get(".deltafile-upload-page").should("be.visible");
    });

    it("renders correctly at smaller viewport", () => {
      cy.viewport(1024, 768);
      cy.visit(baseUrl);
      cy.get(".deltafile-upload-page").should("be.visible");
    });
  });
});
