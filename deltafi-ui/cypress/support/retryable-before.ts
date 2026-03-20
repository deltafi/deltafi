// ABOUTME: Provides a retryable alternative to Mocha's before() hook.
// ABOUTME: Cypress does not retry before() hooks on failure; this uses beforeEach() with a flag to enable retries.

export function retryableBefore(fn: () => void) {
  let shouldRun = true;

  beforeEach(function () {
    if (shouldRun) {
      fn.call(this);
      // Wait for Vue app to mount so loading indicators are in the DOM
      // before we assert their absence
      cy.get("#app").children().should("have.length.greaterThan", 0);
      cy.get(".p-datatable-mask", { timeout: 30000 }).should("not.exist");
      cy.get(".p-progressbar", { timeout: 30000 }).should("not.exist");
      shouldRun = false;
    }
  });

  afterEach(function () {
    if (this.currentTest?.state === "failed") {
      const test = this.currentTest as any;
      const maxRetries = test._retries ?? 0;
      const currentRetry = test._currentRetry ?? 0;
      if (currentRetry >= maxRetries && Cypress.env("BAIL_ON_FAILURE")) {
        cy.task("bail");
      }
      shouldRun = true;
    }
  });
}

// Make available globally (loaded via e2e.ts support file)
(globalThis as any).retryableBefore = retryableBefore;

// Global type declaration
declare global {
  function retryableBefore(fn: () => void): void;
}
