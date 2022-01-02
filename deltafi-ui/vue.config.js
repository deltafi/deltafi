module.exports = {
  devServer: {
    host: 'localhost',
    proxy: {
      '^/api': {
        target: process.env.DELTAFI_API_URL || 'https://dev.deltafi.org'
      },
      '^/graphql': {
        target: process.env.DELTAFI_GATEWAY_URL || 'https://gateway.dev.deltafi.org'
      },
      '^/deltafile/ingress': {
        target: process.env.DELTAFI_INGRESS_URL || 'https://ingress.dev.deltafi.org'
      },
    }
  },
  pwa: {
    manifestOptions: {
      icons: []
    }
  }
}
