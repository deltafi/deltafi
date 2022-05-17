module.exports = {
  devServer: {
    host: 'localhost',
    proxy: {
      '^/api': {
        target: process.env.DELTAFI_API_URL || 'https://dev.deltafi.org'
      },
      '^/graphql': {
        target: process.env.DELTAFI_GATEWAY_URL || 'https://dev.deltafi.org'
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
  },
  configureWebpack: (config) => {
    config.module.rules = [
      {
        test: /\.worker\.(j|t)s$/i,
        use: [
          {
            loader: 'comlink-loader',
            options: {
              singleton: true
            }
          }
        ]
      },
      ...config.module.rules
    ]
  },
  chainWebpack: (config) => {
    config.plugin("copy").tap(([options]) => {
      options[0].ignore.push("mockServiceWorker.js");
      return [options];
    });
  }
}