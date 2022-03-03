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