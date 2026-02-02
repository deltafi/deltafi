// ***********************************************************
// This example support/e2e.ts is processed and
// loaded automatically before your test files.
//
// This is a great place to put global configuration and
// behavior that modifies Cypress.
//
// You can change the location of this file or turn off
// automatically serving support files with the
// 'supportFile' configuration option.
//
// You can read more here:
// https://on.cypress.io/configuration
// ***********************************************************

// Import commands.js using ES2015 syntax:
import './commands'
import './retryable-before'

// When CYPRESS_BAIL_ON_FAILURE is set, skip remaining tests in a spec once a
// test has exhausted all retries. Support files are re-evaluated per spec, so
// specFailed resets automatically.
if (Cypress.env('BAIL_ON_FAILURE')) {
  let specFailed = false;

  afterEach(function () {
    const test = this.currentTest as any;
    if (test?.state === 'failed') {
      const maxRetries = test._retries ?? 0;
      const currentRetry = test._currentRetry ?? 0;
      if (currentRetry >= maxRetries) {
        specFailed = true;
      }
    }
  });

  beforeEach(function () {
    if (specFailed) {
      this.skip();
    }
  });
}
