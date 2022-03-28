const generateDeltaFiles = (count) => {
  return Array.from(Array(count)).map(() => {
    const uuid = crypto.randomUUID();
    const date = new Date();
    return {
      "did": uuid,
      "stage": "ERROR",
      "created": date,
      "modified": date,
      "actions": [
        {
          "name": "IngressAction",
          "state": "ERROR",
          "created": date,
          "modified": date,
          "errorCause": "Failed Ingress",
          "errorContext": "Details..."
        }
      ],
      "sourceInfo": {
        "filename": `smoke-${uuid}`,
        "flow": "smoke"
      },
      "errorAcknowledged": null,
      "errorAcknowledgedReason": null
    }
  })
};

export default {
  "deltaFiles": {
    "offset": 0,
    "count": 1000,
    "totalCount": 2000,
    "deltaFiles": generateDeltaFiles(1000)
  }
}