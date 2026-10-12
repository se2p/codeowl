import type { ClassWorksheetInfo } from "../types/models.ts";

/** A worksheet a student still has to hand in, and when it is due. */
export interface UpcomingDeadline {
    worksheetId: string;
    name: string;
    deadlineAt: string;
    /** Milliseconds until it closes. */
    msRemaining: number;
    /** Due within a day, so it is worth calling out rather than stating quietly. */
    soon: boolean;
}

/** Anything closing within this window counts as urgent. */
export const SOON_MS = 24 * 60 * 60 * 1000;

/**
 * The nearest deadline a student still has ahead of them in one class.
 *
 * Worksheets with no deadline stay out of it — they are open indefinitely and
 * have nothing to show. So do closed ones: a passed deadline is shown on the
 * worksheet itself, but it is not what a student needs to plan around.
 *
 * The server has already resolved `availability` and filtered out anything not
 * yet released, so this only has to pick the earliest of what is left.
 */
export function nextDeadline(
    worksheets: ClassWorksheetInfo[] | null | undefined,
    now: number = Date.now(),
): UpcomingDeadline | null {
    if (!worksheets || worksheets.length === 0) return null;

    const upcoming = worksheets
        .filter((w) => w.deadlineAt && w.availability !== "CLOSED" && w.availability !== "SCHEDULED")
        .map((w) => ({
            worksheetId: w.worksheetId,
            name: w.name,
            deadlineAt: w.deadlineAt as string,
            time: Date.parse(w.deadlineAt as string),
        }))
        // A deadline that will not parse is worse than none: it would sort
        // first as NaN and show as "Invalid Date".
        .filter((w) => Number.isFinite(w.time))
        .sort((a, b) => a.time - b.time || a.name.localeCompare(b.name));

    if (upcoming.length === 0) return null;

    const first = upcoming[0];
    const msRemaining = first.time - now;
    return {
        worksheetId: first.worksheetId,
        name: first.name,
        deadlineAt: first.deadlineAt,
        msRemaining,
        soon: msRemaining <= SOON_MS,
    };
}

/**
 * A deadline as a student should read it: date and clock time, in their locale.
 *
 * The time is always shown. A deadline set as a bare date means midnight, and
 * hiding that would let "due the 3rd" be read as "any time on the 3rd".
 */
export function formatDeadline(iso: string, locale: string): string {
    const date = new Date(iso);
    if (Number.isNaN(date.getTime())) return "";
    return date.toLocaleString(locale, { dateStyle: "medium", timeStyle: "short" });
}
