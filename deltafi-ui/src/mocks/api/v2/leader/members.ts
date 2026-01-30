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

const generateData = () => {
  return {
    members: [
      {
        memberName: "deltafi-member-1",
        url: "https://member1.deltafi.org",
        tags: ["production", "primary"],
        isLeader: true,
        status: {
          code: 0,
          color: "green",
          state: "Healthy",
          checks: [
            {
              description: "System Health Check",
              code: 0,
              message: "",
              timestamp: new Date().toISOString(),
            },
          ],
          timestamp: new Date().toISOString(),
        },
        errorCount: 5,
        inFlightCount: 12,
        warmQueuedCount: 100,
        coldQueuedCount: 50,
        pausedCount: 0,
        oldestInFlightCreated: new Date(Date.now() - 60000).toISOString(),
        lastUpdated: new Date().toISOString(),
        connectionState: "CONNECTED",
        connectionError: null,
        version: "2.47.1",
        cpuUsage: 25.5,
        memoryUsage: 60.2,
        diskUsage: 45.0,
      },
      {
        memberName: "deltafi-member-2",
        url: "https://member2.deltafi.org",
        tags: ["production", "secondary"],
        isLeader: false,
        status: {
          code: 0,
          color: "green",
          state: "Healthy",
          checks: [
            {
              description: "System Health Check",
              code: 0,
              message: "",
              timestamp: new Date().toISOString(),
            },
          ],
          timestamp: new Date().toISOString(),
        },
        errorCount: 2,
        inFlightCount: 8,
        warmQueuedCount: 75,
        coldQueuedCount: 25,
        pausedCount: 0,
        oldestInFlightCreated: new Date(Date.now() - 30000).toISOString(),
        lastUpdated: new Date().toISOString(),
        connectionState: "CONNECTED",
        connectionError: null,
        version: "2.47.1",
        cpuUsage: 18.3,
        memoryUsage: 55.8,
        diskUsage: 42.0,
      },
      {
        memberName: "deltafi-member-3",
        url: "https://member3.deltafi.org",
        tags: ["staging"],
        isLeader: false,
        status: {
          code: 1,
          color: "yellow",
          state: "Degraded",
          checks: [
            {
              description: "System Health Check",
              code: 1,
              message: "High memory usage",
              timestamp: new Date().toISOString(),
            },
          ],
          timestamp: new Date().toISOString(),
        },
        errorCount: 10,
        inFlightCount: 5,
        warmQueuedCount: 200,
        coldQueuedCount: 100,
        pausedCount: 3,
        oldestInFlightCreated: new Date(Date.now() - 120000).toISOString(),
        lastUpdated: new Date().toISOString(),
        connectionState: "CONNECTED",
        connectionError: null,
        version: "2.47.0",
        cpuUsage: 45.0,
        memoryUsage: 85.5,
        diskUsage: 70.0,
      },
    ],
    lastRefresh: new Date().toISOString(),
  };
};

export default generateData();
