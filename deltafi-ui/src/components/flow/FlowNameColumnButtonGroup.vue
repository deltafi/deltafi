<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

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
    <Menu
        ref="menu"
        :id="`overlay_menu_` + data.name + `_` + data.type"
        :model="items"
        :popup="true"
        @hide="hideMenu"
        :pt="{
            action: {
                class: 'py-1',
            },
        }">
        <template #item="{ item, props }">
            <PermissionedRouterLink v-if="item.route" :to="{ path: 'transform-builder/' }" @click="setTransformParams(data, item.edit)">
                <a v-ripple v-tooltip.top="{ value: item.label, class: 'tooltip-width' }" class="flex align-items-center" v-bind="props.action">
                    <span :class="item.icon" />
                    <span class="ml-2">{{ item.label }}</span>
                </a>
            </PermissionedRouterLink>
            <a v-else v-ripple class="flex align-items-center" v-bind="props.action">
                <span :class="item.icon" />
                <span class="ml-2">{{ item.label }}</span>
            </a>
        </template>
    </Menu>
    <Button :id="`${data.name}-${data.type}`" ref="optionsButton" type="button" v-tooltip.top="`Options`" @click="showMenu" severity="secondary" outlined iconPos="right" size="small" :icon="horizontalEllipsis ? 'pi pi-ellipsis-h' : 'pi pi-ellipsis-v'" class="mt-n1 mb-n1" />
    <FlowRemoveButton ref="removeTransform" :row-data-prop="data" @reload-Transforms="refresh" @remove-transform-from-table="removeTransformFromTable" />
</template>

<script setup>
import FlowRemoveButton from "@/components/flow/FlowRemoveButton.vue";
import PermissionedRouterLink from "@/components/PermissionedRouterLink.vue";
import { computed, inject, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { useStorage, StorageSerializers } from "@vueuse/core";

const linkedTransform = useStorage("linked-transform-persisted-params", {}, sessionStorage, { serializer: StorageSerializers.object });

import _ from "lodash";

import Button from "primevue/button";
import Menu from "primevue/menu";
import Ripple from "primevue/ripple";

const router = useRouter();
const emit = defineEmits(["reloadTransforms", "removeTransformFromTable"]);

const hasPermission = inject("hasPermission");
const props = defineProps({
    rowDataProp: {
        type: Object,
        required: true,
    },
});

const { rowDataProp: data } = reactive(props);
const vRipple = Ripple;
const removeTransform = ref(null);
const optionsButton = ref(null);
const horizontalEllipsis = ref(false);
const menu = ref();

const items = ref([
    {
        label: "View Pipeline",
        icon: "text-muted pi pi-sitemap",
        visible: computed(() => hasPermission("FlowView")),
        command: () => {
            router.push(`/pipeline/${data.type}/${data.name}`);
        },
    },
    {
        label: "Edit",
        icon: "text-muted pi pi-pencil",
        visible: computed(() => data.sourcePlugin.artifactId === "system-plugin" && hasPermission("FlowPlanCreate")),
        edit: true,
        route: "/config/plugins/edit",
    },
    {
        label: "Clone",
        icon: "text-muted pi pi-clone",
        visible: computed(() => hasPermission("FlowPlanCreate") && !data.flowStatus?.placeholder),
        edit: false,
        route: "/config/plugins/clone",
    },
    {
        label: "Export",
        icon: "text-muted pi pi-download",
        visible: computed(() => hasPermission("FlowPlanCreate")),
        command: () => {
            exportTransform();
        },
    },
    {
        separator: true,
        visible: computed(() => data.sourcePlugin.artifactId === "system-plugin" && hasPermission("FlowPlanDelete")),
    },
    {
        label: "Remove",
        icon: "text-muted fa-solid fa-trash-can",
        command: async () => {
            removeTransform.value.showDialog();
        },
        visible: computed(() => data.sourcePlugin.artifactId === "system-plugin" && hasPermission("FlowPlanDelete")),
    },
]);

const showMenu = (event) => {
    horizontalEllipsis.value = !horizontalEllipsis.value;
    menu.value.toggle(event);
};

const hideMenu = async () => {
    horizontalEllipsis.value = false;
};

const setTransformParams = (data, editExistingTransform) => {
    linkedTransform.value["transformParams"] = { type: data.flowType, selectedTransformName: data.name, selectedTransform: data, editExistingTransform: editExistingTransform };
};

const refresh = async () => {
    emit("reloadTransforms");
};

const removeTransformFromTable = async (event) => {
    emit("removeTransformFromTable", event);
};

const formatFlowData = () => {
    let exportableData = JSON.parse(JSON.stringify(data));

    exportableData = _.pick(exportableData, ["name", "type", "description", "subscribe", "transformActions", "publish.matchingPolicy", "publish.defaultRule", "publish.rules"]);

    return exportableData;
};

const exportTransform = () => {
    const link = document.createElement("a");
    const downloadFileName = `${data.name}_${data.type}_` + new Date(Date.now()).toLocaleDateString();
    link.download = downloadFileName.toLowerCase();
    const blob = new Blob([JSON.stringify(formatFlowData(), null, 2)], {
        type: "application/json",
    });
    link.href = URL.createObjectURL(blob);
    link.click();
    URL.revokeObjectURL(link.href);
    link.remove();
};
</script>
