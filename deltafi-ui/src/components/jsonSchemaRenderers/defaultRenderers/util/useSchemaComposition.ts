/*
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
*/

import { computeLabel, JsonFormsSubStates } from "@jsonforms/core";
import useStyles from "@/components/jsonSchemaRenderers/defaultRenderers/styles/useStyles";
import { computed, ComputedRef, inject, provide, ref, isRef } from "vue";
import _ from "lodash";
import Ajv from "ajv";

const { setStyles } = useStyles();

export default function useSchemaComposition() {
  const useControlAppliedOptions = <I extends { control: any }>(input: I) => {
    if (!isRef(input.control)) {
      return computed(() => _.merge({}, _.cloneDeep(input.control.config), _.cloneDeep(input.control.uischema.options)));
    } else {
      return computed(() => _.merge({}, _.cloneDeep(input.control.value.config), _.cloneDeep(input.control.value.uischema.options)));
    }
  };

  const useComputedLabel = <I extends { control: any }>(input: I, appliedOptions: ComputedRef<any>) => {
    return computed((): string => {
      return computeLabel(input.control.value.label, input.control.value.required, !!appliedOptions.value?.hideRequiredAsterisk);
    });
  };

  /**
   * Adds styles, isFocused, appliedOptions and onChange
   */
  const useControl = <I extends { control: any; handleChange: any }>(input: I, adaptValue: (target: any) => any = (v) => v, debounceWait?: number) => {
    const changeEmitter = typeof debounceWait === "number" ? _.debounce(input.handleChange, debounceWait) : input.handleChange;

    const onChange = (value: any) => {
      changeEmitter(input.control.value.path, adaptValue(value));
    };

    const appliedOptions = useControlAppliedOptions(input);
    const isFocused = ref(false);

    const computedLabel = useComputedLabel(input, appliedOptions);

    const controlWrapper = computed(() => {
      const { id, description, errors, label, visible, required } = input.control.value;
      return { id, description, errors, label, visible, required };
    });

    const styles = setStyles(input.control.value.uischema);

    return {
      ...input,
      styles,
      isFocused,
      appliedOptions,
      controlWrapper,
      onChange,
      computedLabel,
    };
  };

  const useVuetifyProps = (appliedOptions: ComputedRef<Record<string, any>>) => {
    return (path: string, defaultProps?: Record<string, any>) => {
      const props = _.get(appliedOptions.value?.vuetify, path);

      if (props && _.isPlainObject(props)) {
        return defaultProps ? _.merge({}, defaultProps, props) : props;
      } else {
        return defaultProps ?? {};
      }
    };
  };

  const useLabel = <I extends { label: any }>(input: I) => {
    const styles = input.label.value.uischema;
    const appliedOptions = computed(() => _.merge({}, _.cloneDeep(input.label.value.config), _.cloneDeep(input.label.value.uischema.options)));
    const vuetifyProps = useVuetifyProps(appliedOptions);

    return {
      ...input,
      appliedOptions,
      vuetifyProps,
      styles,
    };
  };

  const useTranslator = () => {
    const jsonforms = inject<JsonFormsSubStates>("jsonforms");

    if (!jsonforms) {
      throw new Error("'jsonforms couldn't be injected. Are you within JSON Forms?");
    }

    if (!jsonforms.i18n || !jsonforms.i18n.translate) {
      throw new Error("'jsonforms i18n couldn't be injected. Are you within JSON Forms?");
    }

    const translate = computed(() => {
      // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
      return jsonforms.i18n!.translate!;
    });

    return translate;
  };

  /**
   * Extracts Ajv from JSON Forms
   */
  const useAjv = () => {
    const jsonforms = inject<JsonFormsSubStates>("jsonforms");

    if (!jsonforms) {
      throw new Error("'jsonforms' couldn't be injected. Are you within JSON Forms?");
    }

    // should always exist
    return jsonforms.core?.ajv as Ajv;
  };

  interface NestedInfo {
    level: number;
    parentElement?: "array" | "object";
  }
  const useNested = (element: false | "array" | "object"): NestedInfo => {
    const nestedInfo = inject<NestedInfo>("jsonforms.nestedInfo", { level: 0 });
    if (element) {
      provide("jsonforms.nestedInfo", {
        level: nestedInfo.level + 1,
        parentElement: element,
      });
    }
    return nestedInfo;
  };

  return {
    useAjv,
    useControlAppliedOptions,
    useControl,
    useNested,
    useTranslator,
    useLabel,
  };
}
