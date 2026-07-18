import { useEffect, useRef, useState } from "react";
import { api } from "../lib/api";
import type { SequenceTag } from "../types/models";

/**
 * AP19 Teil A (A13): Progressive enhancement hook for LLM semantic tags.
 *
 * <p>Problem: when a teacher generates a new sequence, the auto (structural) tags are written
 * synchronously, but the LLM semantic tagger runs on a background thread and may take 3–10s to
 * complete. The user shouldn't have to refresh to see the semantic tags land. Equally, we don't
 * want to poll forever for old sequences — that would generate constant load for zero benefit
 * (the LLM tagger ran, or failed, long ago).
 *
 * <p>Strategy:
 * <ul>
 *   <li>Seed state from {@code initialTags} if provided (zero-latency render of already-known tags).</li>
 *   <li>If {@code initialTags} is {@code undefined}, do a single fetch so consumers without
 *       server-provided tag data still get them.</li>
 *   <li>Only start the polling loop when the tags lack {@code source='llm'} AND the sequence was
 *       created within the last {@link #RECENT_THRESHOLD_MS}.</li>
 *   <li>Poll every {@link #POLL_INTERVAL_MS} for up to {@link #MAX_POLL_DURATION_MS}; stop as
 *       soon as an LLM tag appears.</li>
 * </ul>
 *
 * <p>The hook exposes {@code isWaitingForLlm} so the UI can render a skeleton/pulsing chip while
 * we wait — giving the teacher a visible "something's cooking" signal.
 *
 * @param sequenceId  the sequence to poll; polling stops if this becomes undefined
 * @param initialTags server-provided tag list (from the search / sequence detail endpoint). If
 *                    {@code undefined}, the hook fetches once on mount.
 * @param createdAt   ISO timestamp of the sequence's creation — gates the "is recent" check
 */

const POLL_INTERVAL_MS = 2000;
const MAX_POLL_DURATION_MS = 60_000;
const RECENT_THRESHOLD_MS = 90_000;

export interface ProgressiveTagsState {
    /** The current best-known tag list (may be freshly re-fetched). */
    tags: SequenceTag[];
    /** True while the hook is actively polling for an LLM tag to arrive. */
    isWaitingForLlm: boolean;
}

export function useProgressiveTags(
    sequenceId: string | undefined,
    initialTags: SequenceTag[] | undefined,
    createdAt: string | null | undefined,
): ProgressiveTagsState {
    const [tags, setTags] = useState<SequenceTag[]>(() => initialTags ?? []);
    const [isWaitingForLlm, setIsWaitingForLlm] = useState(false);

    // Sync in parent-provided tags when they change (e.g. user adds a manual tag).
    const lastInitialRef = useRef<SequenceTag[] | undefined>(initialTags);
    useEffect(() => {
        if (initialTags !== lastInitialRef.current) {
            setTags(initialTags ?? []);
            lastInitialRef.current = initialTags;
        }
    }, [initialTags]);

    useEffect(() => {
        if (!sequenceId) {
            setIsWaitingForLlm(false);
            return;
        }

        let cancelled = false;
        let timer: number | null = null;
        const startedAt = Date.now();
        const ageMs = createdAt
            ? Date.now() - new Date(createdAt).getTime()
            : Number.POSITIVE_INFINITY;
        const isRecent = Number.isFinite(ageMs) && ageMs < RECENT_THRESHOLD_MS;

        const fetchTagsOnce = async (): Promise<SequenceTag[] | null> => {
            try {
                const resp = await api.get<SequenceTag[]>(
                    `/library/sequences/${sequenceId}/tags`,
                );
                return resp.data ?? [];
            } catch {
                return null; // silent — LLM tagging is optional
            }
        };

        const poll = async () => {
            if (cancelled) return;
            const fresh = await fetchTagsOnce();
            if (cancelled) return;
            if (fresh !== null) {
                setTags(fresh);
                if (fresh.some((t) => t.source === "llm")) {
                    setIsWaitingForLlm(false);
                    return; // success — stop the chain
                }
            }
            if (Date.now() - startedAt >= MAX_POLL_DURATION_MS) {
                setIsWaitingForLlm(false);
                return; // timed out
            }
            timer = window.setTimeout(poll, POLL_INTERVAL_MS);
        };

        // Decide the initial path based on whether the parent already seeded us with tags.
        const seedTags = initialTags ?? [];
        const seedHasLlm = seedTags.some((t) => t.source === "llm");

        if (initialTags === undefined) {
            // No seed → do a first fetch, then decide whether to keep polling.
            (async () => {
                const fresh = await fetchTagsOnce();
                if (cancelled) return;
                if (fresh !== null) setTags(fresh);
                const list = fresh ?? [];
                if (list.some((t) => t.source === "llm") || !isRecent) {
                    setIsWaitingForLlm(false);
                    return;
                }
                setIsWaitingForLlm(true);
                timer = window.setTimeout(poll, POLL_INTERVAL_MS);
            })();
        } else if (seedHasLlm || !isRecent) {
            // Already complete — nothing to do.
            setIsWaitingForLlm(false);
        } else {
            // Seeded but no LLM yet and recent — poll.
            setIsWaitingForLlm(true);
            timer = window.setTimeout(poll, POLL_INTERVAL_MS);
        }

        return () => {
            cancelled = true;
            if (timer !== null) window.clearTimeout(timer);
        };
        // We intentionally only re-run when the *identity* of the sequence changes — not when
        // the tag state updates mid-poll (that would restart the timer on every fetch).
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [sequenceId, createdAt]);

    return { tags, isWaitingForLlm };
}
