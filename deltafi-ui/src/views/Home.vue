<template>
  <div class="home text-center">
    <img alt="Vue logo" src="../assets/deltafi.png">
    <DFDataTable msg="Welcome to the DeltaFi Dataflow Management Portal"/>
    <df-data-table></df-data-table>
    </div>
</template>


<script lang="ts">
import DFDataTable from '@/components/DFDataTable.vue';

 export default {
   components: { DFDataTable },
   name: 'Home',
   setup() {

   },
 };

</script>
