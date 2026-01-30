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

// ABOUTME: Composable for managing the Message of the Day (MOTD) dialog display.
// ABOUTME: Tracks dismissal state in localStorage and shows the dialog once per calendar day or when the message changes.

import { ref, watch, type Ref } from 'vue';

const STORAGE_KEY = 'deltafi-motd-dismissed';

interface MotdState {
  date: string;
  hash: string;
}

async function hashMessage(message: string): Promise<string> {
  // crypto.subtle is only available in secure contexts (HTTPS)
  if (crypto.subtle) {
    const encoder = new TextEncoder();
    const data = encoder.encode(message);
    const hashBuffer = await crypto.subtle.digest('SHA-256', data);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
  }
  // Fallback for HTTP: simple hash for change detection (not security-critical)
  let hash = 0;
  for (let i = 0; i < message.length; i++) {
    const char = message.charCodeAt(i);
    hash = ((hash << 5) - hash) + char;
    hash = hash & hash;
  }
  return hash.toString(16);
}

function getLocalDateString(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export default function useMotd(message: Ref<string | null | undefined>) {
  const showMotd = ref(false);
  const currentHash = ref('');

  watch(message, async (newMessage) => {
    if (!newMessage) {
      showMotd.value = false;
      return;
    }

    currentHash.value = await hashMessage(newMessage);
    const stored = localStorage.getItem(STORAGE_KEY);

    if (!stored) {
      showMotd.value = true;
      return;
    }

    try {
      const state: MotdState = JSON.parse(stored);
      const today = getLocalDateString();
      showMotd.value = state.date !== today || state.hash !== currentHash.value;
    } catch {
      showMotd.value = true;
    }
  }, { immediate: true });

  function dismiss() {
    const state: MotdState = {
      date: getLocalDateString(),
      hash: currentHash.value
    };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    showMotd.value = false;
  }

  return { showMotd, dismiss };
}
