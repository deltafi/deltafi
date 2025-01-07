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

import { UISchemaElement } from '@jsonforms/core';
import { inject } from 'vue';
import _ from 'lodash';

export interface Styles {
  control: {
    root?: string;
    input?: string;
  };
  verticalLayout: {
    root?: string;
    item?: string;
  };
  horizontalLayout: {
    root?: string;
    item?: string;
  };
  group: {
    root?: string;
    label?: string;
    item?: string;
    bare?: string;
    alignLeft?: string;
  };
  arrayList: {
    root?: string;
    toolbar?: string;
    validationIcon?: string;
    container?: string;
    addButton?: string;
    label?: string;
    noData?: string;
    item?: string;
    itemContainer?: string;
    itemHeader?: string;
    itemLabel?: string;
    itemContent?: string;
    itemMoveUp?: string;
    itemMoveDown?: string;
    itemDelete?: string;
  };
  listWithDetail: {
    root?: string;
    toolbar?: string;
    addButton?: string;
    label?: string;
    noData?: string;
    item?: string;
    itemLabel?: string;
    itemContent?: string;
    itemMoveUp?: string;
    itemMoveDown?: string;
    itemDelete?: string;
  };
  label: {
    root?: string;
  };
  categorization: {
    root?: string;
  };
}

export default function useStyles() {
  const defaultStyles: Styles = {
    control: {
      root: 'control',
      input: 'input',
    },
    verticalLayout: {
      root: 'vertical-layout',
      item: 'vertical-layout-item',
    },
    horizontalLayout: {
      root: 'horizontal-layout',
      item: 'horizontal-layout-item',
    },
    group: {
      root: 'group',
      label: 'group-label',
      item: 'group-item',
      bare: 'group-bare',
      alignLeft: 'group-align-left',
    },
    arrayList: {
      root: 'array-list',
      toolbar: 'array-list-toolbar',
      validationIcon: 'array-list-validation',
      addButton: 'array-list-add',
      label: 'array-list-label',
      noData: 'array-list-no-data',
      item: 'array-list-item',
      itemHeader: 'array-list-item-header',
      itemLabel: 'array-list-item-label',
      itemContent: 'array-list-item-content',
      itemMoveUp: 'array-list-item-move-up',
      itemMoveDown: 'array-list-item-move-down',
      itemDelete: 'array-list-item-delete',
    },
    listWithDetail: {
      root: 'list-with-detail',
      toolbar: 'list-with-detail-toolbar',
      addButton: 'list-with-detail-add',
      label: 'list-with-detail-label',
      noData: 'list-with-detail-no-data',
      item: 'list-with-detail-item',
      itemLabel: 'list-with-detail-item-label',
      itemContent: 'list-with-detail-item-content',
      itemMoveUp: 'list-with-detail-item-move-up',
      itemMoveDown: 'list-with-detail-item-move-down',
      itemDelete: 'list-with-detail-item-delete',
    },
    label: {
      root: 'label-element',
    },
    categorization: {
      root: 'categorization',
    },
  };

  const createEmptyStyles = (): Styles => ({
    control: {},
    verticalLayout: {},
    horizontalLayout: {},
    group: {},
    arrayList: {},
    listWithDetail: {},
    label: {},
    categorization: {},
  });

  const setStyles = (element?: UISchemaElement): Styles => {
    const userStyles = inject('styles', defaultStyles);
    if (!element?.options?.styles) {
      return userStyles;
    }
    const styles = createEmptyStyles();
    if (userStyles) {
      _.merge(styles, userStyles);
    } else {
      _.merge(styles, defaultStyles);
    }
    if (element?.options?.styles) {
      _.merge(styles, element.options.styles);
    }
    return styles;
  };

  return {
    setStyles,
  }
}
