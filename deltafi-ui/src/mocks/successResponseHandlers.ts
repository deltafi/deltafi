import { rest, graphql } from 'msw'

export default [
  rest.get("/api/v1/events", (req, res, ctx) => {
    const status = require(`./api/v1/status.json`);
    const errorCount = 2;

    return res(
      ctx.set('Connection', 'keep-alive'),
      ctx.set('Content-Type', 'text/event-stream'),
      ctx.status(200, 'Mocked status'),
      ctx.body(`event: errorCount\ndata: ${errorCount}\n\nevent: status\ndata: ${JSON.stringify(status)}\n\n`),
    );
  }),

  rest.get("/api/v1/*", (req, res, ctx) => {
    try {
      const responseJson = require(`.${req.url.pathname}`);
      return res(
        ctx.delay(500),
        ctx.status(200, 'Mocked status'),
        ctx.body(JSON.stringify(responseJson, null, 2))
      );
    } catch (e) {
      return
    }
  }),

  graphql.query(/.*/, (req, res, ctx) => {
    try {
      if (req.body && 'operationName' in req.body) {
        const responseJson = require(`./graphql/${req.body.operationName}.json`);
        return res(
          ctx.delay(100),
          ctx.data(responseJson)
        );
      }
    } catch (e) {
      return
    }
  })
]