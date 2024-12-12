module.exports = {
  extends: [
    'eslint:recommended',
    'plugin:vue/vue3-recommended',
    '@vue/typescript',
    'prettier',
    "plugin:vuejs-accessibility/recommended"
  ],
  rules: {
    "vue/max-attributes-per-line": "off",
    "vue/no-v-html": "off",
    "vue/no-dupe-keys": "off",
    "vue/no-lone-template": "off",
    "vue/multi-word-component-names": ["error", {
      "ignores": ["Timestamp", "Clock", "Version"]
    }]
  },
  ignorePatterns: [
    "dist"
  ],
  env: {
    node: true,
    browser: true
  }
}