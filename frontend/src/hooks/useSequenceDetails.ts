import { useEffect, useRef, useState } from "react";
import { api } from "../lib/api.ts";
import type { SequenceTag } from "../types/models.ts";

export interface SequenceDetail {
    programmingLanguage: string;
    taskDescription: string | null;
    concepts: string[];
    tierCount: number;
}

/**
 * Lazily fetches per-sequence enrichment for the worksheet page's teacher-view detail levels.
 *
 * <p>`worksheet.sequenceDTOS` only ever carries `{id, ownerId}` — enough to link a sequence, not
 * enough for a teacher to recognise one without opening it. This fetches the rest: language,
 * task description and concepts/tier count from `GET /sequence/{id}` (needed from "some" level
 * up), plus library tags from a second endpoint (only fetched at the "all" level, since that
 * endpoint requires auth and is the one teachers pay for the least often).
 *
 * <p>Each id is fetched at most once per mount, tracked in a ref rather than state — reordering
 * or removing sequences on the worksheet never re-fetches what is already known; only a genuinely
 * new id (one just added) triggers a request. A failed fetch is evicted from the ref so it can be
 * retried the next time this runs (e.g. detail level toggled off and back on).
 */
export function useSequenceDetails(
    ids: string[],
    enabled: boolean,
    withTags: boolean,
): { details: Record<string, SequenceDetail>; tags: Record<string, SequenceTag[]> } {
    const [details, setDetails] = useState<Record<string, SequenceDetail>>({});
    const [tags, setTags] = useState<Record<string, SequenceTag[]>>({});
    const fetchedDetailIds = useRef<Set<string>>(new Set());
    const fetchedTagIds = useRef<Set<string>>(new Set());
    const idsKey = ids.join(",");

    useEffect(() => {
        if (!enabled) return;
        const missing = ids.filter((id) => !fetchedDetailIds.current.has(id));
        if (missing.length === 0) return;
        missing.forEach((id) => fetchedDetailIds.current.add(id));

        let cancelled = false;
        Promise.all(
            missing.map(async (id) => {
                try {
                    const { data } = await api.get(`/sequence/${id}`);
                    const detail: SequenceDetail = {
                        programmingLanguage: String(data.programmingLanguage ?? "unknown"),
                        taskDescription: data.taskDescription ?? null,
                        concepts: Array.isArray(data.concepts) ? data.concepts.map(String) : [],
                        tierCount: Array.isArray(data.tasks) ? data.tasks.length : 0,
                    };
                    return [id, detail] as const;
                } catch {
                    fetchedDetailIds.current.delete(id);
                    return null;
                }
            }),
        ).then((results) => {
            if (cancelled) return;
            setDetails((prev) => {
                const next = { ...prev };
                for (const r of results) if (r) next[r[0]] = r[1];
                return next;
            });
        });
        return () => { cancelled = true; };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [enabled, idsKey]);

    useEffect(() => {
        if (!enabled || !withTags) return;
        const missing = ids.filter((id) => !fetchedTagIds.current.has(id));
        if (missing.length === 0) return;
        missing.forEach((id) => fetchedTagIds.current.add(id));

        let cancelled = false;
        Promise.all(
            missing.map(async (id) => {
                try {
                    const { data } = await api.get<SequenceTag[]>(`/library/sequences/${id}/tags`);
                    return [id, data] as const;
                } catch {
                    fetchedTagIds.current.delete(id);
                    return null;
                }
            }),
        ).then((results) => {
            if (cancelled) return;
            setTags((prev) => {
                const next = { ...prev };
                for (const r of results) if (r) next[r[0]] = r[1];
                return next;
            });
        });
        return () => { cancelled = true; };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [enabled, withTags, idsKey]);

    return { details, tags };
}
