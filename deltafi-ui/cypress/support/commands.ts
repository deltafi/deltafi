/// <reference types="cypress" />

// Wait for loading overlays to disappear
Cypress.Commands.add("waitForLoading", () => {
  cy.get(".p-datatable-loading-overlay", { timeout: 1000 }).should("not.exist");
});

// Wait for page to be fully loaded (no loading overlays)
Cypress.Commands.add("waitForPageReady", () => {
  cy.get("body").should("be.visible");
  cy.get(".p-datatable-loading-overlay").should("not.exist");
});

// Extend Cypress Chainable interface
declare module "cypress" {
  interface Chainable {
    waitForLoading(): Chainable<void>;
    waitForPageReady(): Chainable<void>;
  }
}