// Declaring the State
export type State = {
  uiConfig: {
    title: String,
    domain: String,
    securityBanner: {
      enabled: Boolean,
      backgroundColor: String,
      textColor: String
      text: String
    }
    dashboard: {
      links: Array<{
        name: String,
        url: String,
        description: String
      }>
    }
  }
}

// Setting the State
export const state: State = {
  uiConfig: {
    title: 'DeltaFi',
    domain: 'example.deltafi.org',
    securityBanner: {
      enabled: false,
      backgroundColor: "#FFFFFFF",
      textColor: "#000000",
      text: 'DeltaFi',
    },
    dashboard: {
      links: []
    }
  }
};