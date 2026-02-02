import { defineConfig } from "cypress";

export default defineConfig({
  e2e: {
    viewportHeight: 900,
    viewportWidth: 1400,
    defaultCommandTimeout: 10000,

    // Performance optimizations
    video: false,
    screenshotOnRunFailure: true,
    numTestsKeptInMemory: 0,
    experimentalMemoryManagement: true,
    waitForAnimations: false,
    pageLoadTimeout: 20000,
    responseTimeout: 10000,
    trashAssetsBeforeRuns: false,

    // Retry failed tests in CI
    retries: {
      runMode: 3,
      openMode: 0,
    },

    setupNodeEvents(on) {
      on("before:browser:launch", (browser, launchOptions) => {
        if (browser.name === "chrome" || browser.name === "electron") {
          launchOptions.args.push("--disable-gpu");
          launchOptions.args.push("--disable-dev-shm-usage");
          launchOptions.args.push("--disable-extensions");
        }
        return launchOptions;
      });

      if (process.env.CYPRESS_BAIL_ON_FAILURE) {
        on("task", {
          bail() {
            setTimeout(() => process.exit(1), 100);
            return null;
          },
        });

        on("after:spec", (spec, results) => {
          if (results && results.stats.failures > 0) {
            process.exit(1);
          }
        });
      }
    },
  },
});
