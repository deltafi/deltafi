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

describe("Users Page", () => {
  const baseUrl = "http://localhost:8080/admin/users";

  // Read-only tests - share a single page visit
  describe("Page Structure & Elements", { testIsolation: false }, () => {
    retryableBefore(() => {
      cy.visit(baseUrl);
    });

    it("loads the page with correct heading", () => {
      cy.get("h2").contains("User Management");
    });

    it("displays the Users panel", () => {
      cy.get("span.p-panel-title").contains("Users");
    });

    it("displays Add User button", () => {
      cy.contains("Add User").should("exist");
    });

    it("displays the data table", () => {
      cy.get(".p-datatable").should("exist");
    });

    it("displays users data", () => {
      cy.get(".p-datatable-tbody tr").should("have.length.greaterThan", 0);
    });

    it("page container exists", () => {
      cy.get(".users-page").should("exist");
    });

    it("renders correctly at default viewport", () => {
      cy.get(".users-page").should("be.visible");
    });

    it("renders correctly at smaller viewport", () => {
      cy.viewport(1024, 768);
      cy.get(".users-page").should("be.visible");
    });
  });

  // Dialog tests
  describe("Add User Dialog", () => {
    it("can open Add User dialog", () => {
      cy.visit(baseUrl);
      cy.contains("Add User").click();
      cy.get(".p-dialog").should("be.visible");
    });
  });
});
