<template>
    <div class="d-flex">
        <Button label="Import" icon="fas fa-upload fa-fw" class="p-button-sm p-button-secondary p-button-outlined mx-1" @click="importTransforms" />
        <Button label="Export" icon="fas fa-download fa-fw" class="p-button-sm p-button-secondary p-button-outlined mx-1" @click="exportTransforms" />
        <PermissionedRouterLink :to="{ path: 'transform-builder/' }">
            <Button v-has-permission:FlowPlanCreate label="Add" icon="pi pi-plus" class="p-button-sm p-button-outlined mx-1" />
        </PermissionedRouterLink>

        <FileUpload v-show="false" auto ref="transformsFileUploader" mode="basic" accept=".json,application/json" :file-limit="1" custom-upload @uploader="preUploadValidation" :pt="{
            input: {
                id: 'transformsFileUploaderButton',
            },
        }" />
    </div>
</template>

<script setup>
import PermissionedRouterLink from "@/components/PermissionedRouterLink.vue";
import useFlowPlanQueryBuilder from "@/composables/useFlowPlanQueryBuilder";
import useNotifications from "@/composables/useNotifications";
import { ref } from "vue";

import _ from "lodash";

import Button from "primevue/button";
import FileUpload from "primevue/fileupload";

const emit = defineEmits(["reloadTransforms"]);
const { saveTransformFlowPlan } = useFlowPlanQueryBuilder();
const notify = useNotifications();
const transformsFileUploader = ref(null);

const props = defineProps({
    exportTransforms: {
        type: Object,
        required: true,
    },
});

const importTransforms = () => {
    document.getElementById("transformsFileUploaderButton").click();
};

const preUploadValidation = async (request) => {
    for (const file of request.files) {
        const reader = new FileReader();

        reader.readAsText(file);

        reader.onload = function () {
            if (!JSON.parse(reader.result)) {
                notify.error(`Invalid file format`, 4000);
                deleteUploadFile();
            }

            const validJson = JSON.parse(reader.result);

            if (_.has(validJson, "transforms")) {
                for (const transform of validJson.transforms) {
                    uploadTransform(transform);
                }
            } else if (_.has(validJson, "type")) {
                uploadTransform(validJson, true);
            } else {
                notify.error(`Invalid file format`, 4000);
            }
            emit("reloadTransforms");
        };
    }
};

const uploadTransform = async (data) => {
    let response = await saveTransformFlowPlan(data);
    response = response.data.saveTransformFlowPlan;

    if (_.isEmpty(_.get(response, "name", null))) {
        notify.error(`Transform Import Failed`, `Failed to import Transform ${data.name}.`, 4000);
    } else {
        notify.success(`Imported Successful`, `Successfully imported Transform ${data.name}.`, 4000);
    }
};

const deleteUploadFile = () => {
    transformsFileUploader.value.files = [];
};

const exportTransforms = () => {
    const link = document.createElement("a");
    const downloadFileName = `transforms_export_` + new Date(Date.now()).toLocaleDateString();
    link.download = downloadFileName.toLowerCase();
    const blob = new Blob([JSON.stringify(props.exportTransforms, null, 2)], {
        type: "application/json",
    });
    link.href = URL.createObjectURL(blob);
    link.click();
    URL.revokeObjectURL(link.href);
    link.remove();
};
</script>
