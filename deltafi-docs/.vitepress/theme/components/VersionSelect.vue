<template>
  <div v-show="showVersionSelect" class="vp-version-select">
    <select :value="currentLabel" @change="onChange" aria-label="Select docs version">
      <option v-for="v in versions" :key="v.link" :value="v.label">
        {{ v.label }}
      </option>
    </select>
  </div>
</template>

<script setup>
import { computed, onMounted } from "vue";
import { useRoute, useRouter } from "vitepress";

// const appVersion = __APP_VERSION__|| "no version";

const router = useRouter();
const route = useRoute();

onMounted(() => {
  console.log("Vite ENV: ", import.meta.env);
  // console.log("App Version: ", appVersion);

});

//   if (process.env.VUE_APP_SHOW_VERSIONS === "true") {
//     config.versions = {};
//     config.versions[`v${process.env.VUE_APP_VERSION} (Latest)`] = { link: "/" };
//     config.versions["v1.2.20"] = { link: "https://v1.docs.deltafi.org" };
//   }

// Edit to your versions + base paths
const versions = [
  { label: `v(Latest)`, link: "/" },
  { label: "v1.2.20", link: "https://v1.docs.deltafi.org" },
];

const showVersionSelect = computed(() => {
  if (import.meta.env.VITE_APP_SHOW_VERSIONS === "true") {
    return true;
  }
});

const currentLabel = computed(() => versions.find((v) => route.path.startsWith(v.link))?.label ?? versions[0].label);

const onChange = (e) => {
  const label = e.target.value;
  const v = versions.find((v) => v.label === label);
  if (v) router.go(v.link);
};
</script>

<style scoped>
.vp-version-select {
  padding: 12px 0px;
  border-bottom: 1px solid var(--vp-c-divider);
}
.vp-version-select select {
  width: 100%;
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid var(--vp-c-divider-strong);
  background: var(--vp-c-bg-soft);
  color: var(--vp-c-text-1);
}
</style>
