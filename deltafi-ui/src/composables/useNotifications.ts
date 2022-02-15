import { useToast } from "primevue/usetoast";

export default function useNotifications() {
  const toast = useToast();

  const defaultTTL = {
    success: 5000,
    info: 5000,
    warn: 10000,
    error: 10000,
  };

  const success = (summary: string, detail: string = "", ttl: number = defaultTTL.success) => {
    toast.add({ severity: "success", summary, detail, life: ttl });
  };

  const info = (summary: string, detail: string = "", ttl: number = defaultTTL.info) => {
    toast.add({ severity: "info", summary, detail, life: ttl });
  };

  const warn = (summary: string, detail: string = "", ttl: number = defaultTTL.warn) => {
    toast.add({ severity: "warn", summary, detail, life: ttl });
  };

  const error = (summary: string, detail: string = "", ttl: number = defaultTTL.error) => {
    toast.add({ severity: "error", summary, detail, life: ttl });
  };

  const clear = () => {
    toast.removeAllGroups();
  };

  return { success, info, warn, error, clear };
}