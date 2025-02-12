import pluginVue from "eslint-plugin-vue";
import { defineConfigWithVueTs, vueTsConfigs, configureVueProject } from "@vue/eslint-config-typescript";
import pluginVueA11y from "eslint-plugin-vuejs-accessibility";

configureVueProject({
  tsSyntaxInTemplates: true,
  scriptLangs: ["ts", "js"],
  rootDir: import.meta.dirname,
});

export default defineConfigWithVueTs(
  {
    ignores: ["dist/"],
  },
  pluginVue.configs["flat/essential"],
  vueTsConfigs.recommended,
  ...pluginVueA11y.configs["flat/recommended"],
  {
    rules: {
      "vue/max-attributes-per-line": "off",
      "vue/no-v-html": "off",
      "vue/no-dupe-keys": "off",
      "vue/no-lone-template": "off",
      "vue/multi-word-component-names": [
        "error",
        {
          ignores: ["Timestamp", "Clock", "Version"],
        },
      ],
      "@typescript-eslint/no-array-constructor": "off",
      "@typescript-eslint/no-explicit-any": "off",
      "@typescript-eslint/no-non-null-assertion')": "off",
      "@typescript-eslint/no-require-imports": "off",
      "@typescript-eslint/no-this-alias": "off",
      "@typescript-eslint/no-unused-expressions": "off",
      "@typescript-eslint/no-unused-vars": "off",
      "@typescript-eslint/no-wrapper-object-types": "off",
    },
  }
);
