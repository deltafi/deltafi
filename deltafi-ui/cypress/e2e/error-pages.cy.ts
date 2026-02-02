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

describe("Page Not Found", () => {
  describe("Default Not Found", { testIsolation: false }, () => {
    retryableBefore(() => {
      cy.visit("http://localhost:8080/this-page-does-not-exist", {
        failOnStatusCode: false,
      });
    });

    it("displays Page Not Found heading", () => {
      cy.get("h2").contains("Page Not Found");
    });

    it("displays appropriate message", () => {
      cy.contains("The page you are looking for cannot be found").should(
        "exist"
      );
    });

    it("renders correctly at default viewport", () => {
      cy.get("h2").should("be.visible");
    });

    it("renders correctly at smaller viewport", () => {
      cy.viewport(1024, 768);
      cy.get("h2").should("be.visible");
    });
  });

  describe("Service Unavailable", { testIsolation: false }, () => {
    retryableBefore(() => {
      cy.visit("http://localhost:8080/not-found?service=Grafana", {
        failOnStatusCode: false,
      });
    });

    it("displays service unavailable heading with service name", () => {
      cy.get("h2").contains("Grafana Unavailable");
    });

    it("displays service unavailable message", () => {
      cy.contains("service is currently offline or being updated").should(
        "exist"
      );
    });
  });
});

describe("Unauthorized Page", () => {
  const baseUrl = "http://localhost:8080/unauthorized";

  describe("Page Load", { testIsolation: false }, () => {
    retryableBefore(() => {
      cy.visit(baseUrl);
    });

    it("displays Access Denied heading", () => {
      cy.get("h2").contains("Access Denied");
    });

    it("displays unauthorized message", () => {
      cy.contains("You are not authorized to view the requested page").should(
        "exist"
      );
    });

    it("renders correctly at default viewport", () => {
      cy.get("h2").should("be.visible");
    });

    it("renders correctly at smaller viewport", () => {
      cy.viewport(1024, 768);
      cy.get("h2").should("be.visible");
    });
  });
});
