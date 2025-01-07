<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->

<template>
  <footer v-if="bannerEnabled" :style="{ backgroundColor: backgroundColor, color: textColor }" class="sticky-footer">
    <div class="container my-auto">
      <div class="text-center my-auto">
        <span>{{ bannerMessage }}</span>
      </div>
    </div>
  </footer>
</template>

<script setup>
import { computed, inject } from "vue";

const uiConfig = inject('uiConfig');

const textColor = computed(() => {
  return uiConfig.securityBanner.textColor;
});

const backgroundColor = computed(() => {
  return uiConfig.securityBanner.backgroundColor;
});

const bannerMessage = computed(() => {
  return uiConfig.securityBanner.text;
});

const bannerEnabled = computed(() => {
  return uiConfig.securityBanner.enabled;
});
</script>

<style lang="scss">
@import "@/styles/components/footer/footer-banner.scss";
</style>
