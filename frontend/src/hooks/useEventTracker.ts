import { useCallback } from "react";
import { api } from "../lib/api";

export function useEventTracker() {
  const logEvent = useCallback(async (
    taskId: string,
    eventType: string,
    eventData: Record<string, any> = {},
    sequenceId?: string,
    tierLevel?: number
  ) => {
    try {
      await api.post("/events", { taskId, sequenceId, tierLevel, eventType, eventData });
    } catch {
      // Silent failure — analytics should never break UX
    }
  }, []);

  return { logEvent };
}
