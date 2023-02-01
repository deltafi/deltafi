/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>

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

import { ref, Ref } from 'vue'
import useEvents from './useEvents';

export default function useEventsNotifications() {
  const { data, loaded, loading, fetch, acknowledgeEvent, errors } = useEvents();
  const notifications: Ref<Array<any>> = ref([]);
  const daysAgo = 7;

  const fetchNotifications = async () => {
    try {
      const startDate = new Date();
      startDate.setDate(startDate.getDate() - daysAgo)
      await fetch({ notification: true, acknowledged: false, start: startDate.toISOString() });
      notifications.value = data.value;
    } catch (response: any) {
      return Promise.reject(response);
    }
  }

  const ackNotification = async (id: string) => {
    notifications.value = notifications.value.filter((notification) => {
      return notification._id !== id;
    });
    await acknowledgeEvent(id);
  }

  const ackAllNotifications = async () => {
    const ids = notifications.value.map((n) => n._id);
    await acknowledgeEvent(ids);
    notifications.value = [];
  }

  return { notifications, fetchNotifications, loaded, loading, ackNotification, ackAllNotifications, errors };
}