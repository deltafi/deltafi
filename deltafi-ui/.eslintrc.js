module.exports = {
  extends: [
    'eslint:recommended',
    'plugin:vue/vue3-recommended',
    '@vue/typescript',
    'prettier'
  ],
  rules: {
    "vue/max-attributes-per-line": "off",
    "vue/no-v-html": "off",
    "vue/multi-word-component-names": ["error", {
      "ignores": ["Timestamp", "Clock", "Version"]
    }]
  },
  ignorePatterns: [
    "dist",
    "graphiql"
  ],
  env: {
    node: true,
    browser: true
  }
}