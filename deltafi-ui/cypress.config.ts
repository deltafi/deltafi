import { defineConfig } from "cypress";

export default defineConfig({
  component: {
    devServer: {
      framework: "vue-cli",
      bundler: "webpack",
    },
  },

  e2e: {
    viewportHeight: 900,
    viewportWidth: 1400,
    setupNodeEvents() {
      // implement node event listeners here
    },
  },
});
