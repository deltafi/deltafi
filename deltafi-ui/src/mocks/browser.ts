/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>

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

import { setupWorker } from 'msw'
import errorResponseHandlers from './errorResponseHandlers';
import successReponseHandlers from './successResponseHandlers';
import customResponseHandlers from './customResponseHandlers'

const responseHandler = () => {
  let worker = setupWorker();
  if (process.env.VUE_APP_MOCK_RESPONSES === 'successResponse') {
    worker = setupWorker(...successReponseHandlers)
  } else if (process.env.VUE_APP_MOCK_RESPONSES === 'errorResponse') {
    worker = setupWorker(...errorResponseHandlers)
  } else if (process.env.VUE_APP_MOCK_RESPONSES === 'customResponse') {
    worker = setupWorker(...customResponseHandlers)
  }

  return worker;
}

export const worker = responseHandler();
