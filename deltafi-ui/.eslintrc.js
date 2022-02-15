module.exports = {
  extends: [
    'eslint:recommended',
    'plugin:vue/vue3-recommended',
    '@vue/typescript',
    'prettier'
  ],
  rules: {
    "vue/max-attributes-per-line": "off",
    "vue/no-v-html": "off"
  },
  ignorePatterns: [
    "dist"
  ],
  env: {
    node: true,
    browser: true
  }
}