module.exports = {
  devServer: {
    host: 'localhost',
    proxy: {
      '^/api': {
        target: process.env.DELTAFI_API_URL || 'https://dev.deltafi.org'
      }
    }
  }
}
