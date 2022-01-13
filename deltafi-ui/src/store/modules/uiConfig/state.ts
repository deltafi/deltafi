// Declaring the State
export type State = {
  uiConfig: {
    title: String,
    domain: String,
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
    dashboard: {
      links: []
    }
  }
};