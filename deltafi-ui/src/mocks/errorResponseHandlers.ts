import { rest } from 'msw'


export default [

  rest.get("/api/v1/config", (req, res, ctx) => {
    return res(
      ctx.delay(1500),
      ctx.status(500, 'Mocked status'),
      ctx.json({
        error: 'Error fetching UI configs.',
      })
    )
  }),
  rest.get("/api/v1/metrics/system/nodes", (req, res, ctx) => {
    return res(
      ctx.delay(1500),
      ctx.status(500, 'Mocked status'),
      ctx.json({
        error: 'Error fetching System Metrics.',
      })
    )
  }),

  rest.get("/api/v1/metrics/action", (req, res, ctx) => {
    return res(
      ctx.delay(1500),
      ctx.status(500, 'Mocked status'),
      ctx.json({
        error: 'Error fetching Action Metrics.',
      })
    )
  }),

  rest.get("/api/v1/metrics/queues", (req, res, ctx) => {
    return res(
      ctx.delay(1500),
      ctx.status(500, 'Mocked status'),
      ctx.json({
        error: 'Error fetching Queue Metrics.',
      })
    )
  }),

  rest.get("/api/v1/versions", (req, res, ctx) => {
    return res(
      ctx.delay(1500),
      ctx.status(500, 'Mocked status'),
      ctx.json({
        error: 'Error fetching Versions.',
      })
    )
  }),

  rest.post("/graphql", (req, res, ctx) => {
    return res(
      ctx.delay(1500),
      ctx.status(500, 'Mocked status'),
      ctx.json({
        message: 'Error fetching data from GraphQL',
      })
    )
  }),
]