<template>
  <Dialog :header="acknowledgeButtonLabel" :maximizable="false" :modal="true" :style="{width: '25vw'}" @update:visible="close">
    <div class="p-fluid">
      <span class="p-float-label mt-3">
        <InputText id="reason" v-model="reason" type="text" :class="{'p-invalid': reasonInvalid}" autofocus />
        <label for="reason">Reason</label>
      </span>
    </div>
    <template #footer>
      <Button label="Cancel" icon="pi pi-times" class="p-button-text" @click="close" />
      <Button :label="acknowledgeButtonLabel" icon="pi pi-check" @click="acknowledge" />
    </template>
  </Dialog>
</template>

<script>
import GraphQLService from "@/service/GraphQLService";
import Button from "primevue/button";
import InputText from "primevue/inputtext";
import Dialog from "primevue/dialog";
import { UtilFunctions } from "@/utils/UtilFunctions";

export default {
  name: "AckErrorsDialog",
  components: {
    Button,
    Dialog,
    InputText,
  },
  props: {
    dids: {
      type: Array,
      required: true,
    },
  },
  emits: [
    'acknowledged',
    'update:visible'
  ],
  data() {
    return {
      reason: '',
      show: true,
      reasonInvalid: false
    };
  },
  computed: {
    acknowledgeButtonLabel() {
      if (this.dids.length === 1) return "Acknowledge Error";
      let pluralized = this.utilFunctions.pluralize(this.dids.length, "Error")
      return `Acknowledge ${pluralized}`;
    }
  },
  created() {
    this.graphQLService = new GraphQLService();
    this.utilFunctions = new UtilFunctions();
  },
  methods: {
    async acknowledge() {
      if (this.reason) {
        await this.graphQLService.acknowledgeErrors(this.dids, this.reason);
        this.$emit('acknowledged', this.dids, this.reason);
        this.$emit('update:visible', false);
        this.reason = '';
      } else {
        this.reasonInvalid = true;
      }
    },
    close() {
      this.$emit('update:visible', false);
    }
  },
  graphQLService: null
};
</script>

<style lang="scss">
</style>