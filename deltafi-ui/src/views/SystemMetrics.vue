<template>
  <div>
    <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
      <h1 class="h2">System Metrics</h1>
    </div>

    <SystemMetrics />
  </div>
</template>


<script lang="ts">

import SystemMetrics from '@/components/SystemMetrics.vue';

 export default {
   components: { SystemMetrics },
   name: 'Metrics'
 };

</script>
