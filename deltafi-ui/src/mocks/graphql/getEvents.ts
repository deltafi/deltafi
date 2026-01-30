/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

const generateEvents = () => {
  const events = [];
  const severities = ["INFO", "WARN", "ERROR", "SUCCESS"];
  const sources = ["deltafi-core", "deltafi-auth", "deltafi-ingress", "deltafi-worker"];
  const summaries = [
    "System started successfully",
    "Plugin deployed: deltafi-testjig",
    "Action execution failed",
    "Memory usage threshold exceeded",
    "New user created",
    "Flow configuration updated",
    "DeltaFile processing completed",
    "Connection pool exhausted",
    "Scheduled task executed",
    "Cache cleared",
  ];

  for (let i = 0; i < 50; i++) {
    events.push({
      id: `event-${i}`,
      severity: severities[Math.floor(Math.random() * severities.length)],
      summary: summaries[Math.floor(Math.random() * summaries.length)],
      source: sources[Math.floor(Math.random() * sources.length)],
      timestamp: new Date(Date.now() - i * 600000).toISOString(),
      notification: Math.random() > 0.7,
      acknowledged: Math.random() > 0.5,
      content: `Detailed event content for event ${i}`,
    });
  }
  return events;
};

export default {
  getEvents: {
    events: generateEvents(),
    totalCount: 50,
  },
};
