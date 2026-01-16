import { defineConfig } from "cypress";

export default defineConfig({
  e2e: {
    viewportHeight: 900,
    viewportWidth: 1400,
    defaultCommandTimeout: 15000,

    // Performance optimizations
    video: false,
    screenshotOnRunFailure: true,
    numTestsKeptInMemory: 0,
    experimentalMemoryManagement: true,
    waitForAnimations: false,
    pageLoadTimeout: 60000,
    responseTimeout: 20000,
    trashAssetsBeforeRuns: false,

    // Retry failed tests in CI
    retries: {
      runMode: 2,
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
    },
  },
});
