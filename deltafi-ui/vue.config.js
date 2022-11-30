const DELTAFI_DOMAIN = process.env.DELTAFI_DOMAIN || "dev.deltafi.org";

module.exports = {
  devServer: {
    host: 'localhost',
    proxy: {
      '^/api': {
        target: `https://${DELTAFI_DOMAIN}`
      },
      '^/graphql': {
        target: `https://${DELTAFI_DOMAIN}`
      },
      '^/deltafile/ingress': {
        target: `https://ingress.${DELTAFI_DOMAIN}`
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
