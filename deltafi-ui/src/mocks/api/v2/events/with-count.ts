// ABOUTME: Mock data for the events/with-count API endpoint.
// ABOUTME: Returns events wrapped with a totalCount for paginated responses.

import events from "../events";

export default {
  events,
  totalCount: events.length,
};
