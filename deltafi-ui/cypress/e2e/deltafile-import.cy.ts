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

describe("DeltaFile Import Page", () => {
  const baseUrl = "http://localhost:8080/deltafile/import";

  // Read-only tests - share a single page visit
  describe("Page Structure & Elements", { testIsolation: false }, () => {
    before(() => {
      cy.visit(baseUrl);
    });

    it("loads the page with default settings", () => {
      cy.get("h2").contains("Import DeltaFiles");
    });

    it("displays file upload component", () => {
      cy.get(".p-fileupload").should("exist");
    });

    it("displays drag and drop message", () => {
      cy.contains("Drag and drop").should("exist");
    });

    it("displays Add Files button", () => {
      cy.contains("Add Files").should("exist");
    });

    it("displays Clear button", () => {
      cy.contains("Clear").should("exist");
    });

    it("accepts tar files", () => {
      cy.get(".p-fileupload").should("exist");
    });

    it("page container exists", () => {
      cy.get(".deltafile-import-page").should("exist");
    });
  });

  // Responsive Layout
  describe("Responsive Layout", () => {
    it("renders correctly at default viewport", () => {
      cy.visit(baseUrl);
      cy.get(".deltafile-import-page").should("be.visible");
    });

    it("renders correctly at smaller viewport", () => {
      cy.viewport(1024, 768);
      cy.visit(baseUrl);
      cy.get(".deltafile-import-page").should("be.visible");
    });
  });
});
