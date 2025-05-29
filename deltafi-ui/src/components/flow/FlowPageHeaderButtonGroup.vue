<template>
    <Menu
        ref="menu"
        id="FlowPageHeaderMenu"
        :model="items"
        :popup="true"
        @hide="hideMenu"
        :pt="{
            action: {
                class: 'py-1',
            },
        }">
        <template #item="{ item, props }">
            <PermissionedRouterLink v-if="item.route" :to="{ path: 'transform-builder/' }">
                <a v-ripple v-tooltip.top="{ value: item.label, class: 'tooltip-width' }" class="flex align-items-center" v-bind="props.action">
                    <span :class="item.icon" />
                    <span class="ml-2 text-dark">{{ item.label }}</span>
                </a>
            </PermissionedRouterLink>
            <a v-else v-ripple class="flex align-items-center" v-bind="props.action">
                <span :class="item.icon" />
                <span class="ml-2 text-dark">{{ item.label }}</span>
            </a>
        </template>
    </Menu>
    <Button id="FlowPageHeaderOptions" ref="optionsButton" type="button" v-tooltip.left="`Options`" @click="showMenu" severity="secondary" outlined iconPos="right" size="small" :icon="horizontalEllipsis ? 'pi pi-ellipsis-h' : 'pi pi-ellipsis-v'" />
    <FileUpload v-show="false" auto ref="transformsFileUploader" mode="basic" accept=".json,application/json" :file-limit="1" custom-upload @uploader="preUploadValidation" :pt="{
        input: {
            id: 'transformsFileUploaderButton',
        },
    }" />
</template>

<script setup>
import PermissionedRouterLink from "@/components/PermissionedRouterLink.vue";
import useFlowPlanQueryBuilder from "@/composables/useFlowPlanQueryBuilder";
import useNotifications from "@/composables/useNotifications";
import { computed, inject, ref } from "vue";

import _ from "lodash";

import Button from "primevue/button";
import FileUpload from "primevue/fileupload";
import Menu from "primevue/menu";
import Ripple from "primevue/ripple";

const emit = defineEmits(["reloadTransforms"]);
const { saveTransformFlowPlan } = useFlowPlanQueryBuilder();
const notify = useNotifications();
const vRipple = Ripple;
const menu = ref();
const optionsButton = ref(null);
const transformsFileUploader = ref(null);
const horizontalEllipsis = ref(false);
const hasPermission = inject("hasPermission");

const props = defineProps({
    exportTransforms: {
        type: Object,
        required: true,
    },
});

const items = ref([
    {
        label: "Add Transform",
        icon: "pi pi-plus",
        visible: computed(() => hasPermission("FlowPlanCreate")),
        route: "/config/plugins/add",
    },
    {
        label: "Import Transforms",
        icon: "text-muted fas fa-upload fa-fw",
        visible: computed(() => hasPermission("FlowPlanCreate")),
        command: () => {
            document.getElementById("transformsFileUploaderButton").click();
        },
    },
    {
        label: "Export Transforms",
        icon: "text-muted fas fa-download fa-fw",
        visible: true,
        command: () => {
            exportTransforms();
        },
    },
]);

const showMenu = (event) => {
    horizontalEllipsis.value = !horizontalEllipsis.value;
    menu.value.toggle(event);
};

const hideMenu = async () => {
    horizontalEllipsis.value = false;
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
