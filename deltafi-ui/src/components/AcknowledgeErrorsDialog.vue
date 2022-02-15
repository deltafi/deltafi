<template>
  <Dialog :header="acknowledgeButtonLabel" :maximizable="false" :modal="true" :style="{ width: '25vw' }" @update:visible="close">
    <div class="p-fluid">
      <span class="p-float-label mt-3">
        <InputText id="reason" v-model="reason" type="text" :class="{ 'p-invalid': reasonInvalid }" autofocus />
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
import Button from "primevue/button";
import InputText from "primevue/inputtext";
import Dialog from "primevue/dialog";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { computed, ref } from "vue";
import useAcknowledgeErrors from "@/composables/useAcknowledgeErrors";

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
  emits: ["acknowledged", "update:visible"],
  setup(props, context) {
    const { pluralize } = useUtilFunctions();
    const reason = ref("");
    const show = ref(true);
    const reasonInvalid = ref(false);

    const { data: AcknowledgeErrorsData, post: PostAcknowledgeErrors, errors } = useAcknowledgeErrors();

    const acknowledgeButtonLabel = computed(() => {
      if (props.dids.length === 1) return "Acknowledge Error";
      let pluralized = pluralize(props.dids.length, "Error");
      return `Acknowledge ${pluralized}`;
    });

    const acknowledge = async () => {
      if (reason.value) {
        try {
          await PostAcknowledgeErrors(props.dids, reason.value);
          context.emit("acknowledged", props.dids, reason.value);
          context.emit("update:visible", false);
          reason.value = "";
        } catch {
          // Do Nothing
        }
      } else {
        reasonInvalid.value = true;
      }
    };

    const close = () => {
      context.emit("update:visible", false);
    };

    return {
      reason,
      show,
      reasonInvalid,
      close,
      acknowledge,
      acknowledgeButtonLabel,
      AcknowledgeErrorsData,
      PostAcknowledgeErrors,
      errors,
      props,
    };
  },
};
</script>

<style lang="scss">
</style>