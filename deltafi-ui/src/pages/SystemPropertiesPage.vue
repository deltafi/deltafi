<template>
  <div class="SystemProperties">
    <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
      <h1 class="h2">
        System Properties
      </h1>
    </div>
    <ProgressBar v-if="loadingPropertySets" mode="indeterminate" style="height: .5em" />
    <PropertySet v-for="propSet in propertySets" :key="propSet.id" :prop-set="propSet" />
  </div>
</template>

<script>
import PropertySet from "@/components/PropertySet";
import ProgressBar from "primevue/progressbar"
import { mapGetters } from "vuex";
import { useStore } from '@/store';
import { PropertySetsActionTypes } from '@/store/modules/propertySets/action-types';

export default {
  name: "SystemPropertiesPage",
  components: {
    PropertySet,
    ProgressBar
  },
  computed: {
    ...mapGetters(["propertySets", "loadingPropertySets"]),
  },
  mounted() {
    const store = useStore();
    store.dispatch(PropertySetsActionTypes.FETCH_PROPERTY_SETS);
  },
};
</script>

<style lang="scss">
  @import "@/styles/pages/system-properties-page.scss";
</style>