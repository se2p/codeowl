/**
 * Conversion between an ISO instant and the separate date and time inputs the
 * schedule editor uses.
 *
 * A single `datetime-local` input was tried first and was awkward: its picker
 * highlights today without selecting it, so a teacher who clicks the highlighted
 * day still ends up with an empty field, and it offers no way to mean "this day"
 * without also naming a time. Two inputs avoid both — `type="date"` sets what
 * you click, and the time is optional.
 *
 * Neither input carries a timezone: both show wall-clock time in the browser's
 * zone, while the server stores instants. This module is that boundary, and
 * getting it wrong shifts every deadline by the offset — silently, and only for
 * people not on UTC.
 */

export interface DateTimeParts {
    /** `YYYY-MM-DD`, or empty for no constraint at all. */
    date: string;
    /** `HH:mm`, or empty to mean midnight at the start of that date. */
    time: string;
}

const MIDNIGHT = "00:00";

/** Splits an ISO instant into the two input values, in local time. */
export function toDateTimeParts(iso: string | null | undefined): DateTimeParts {
    if (!iso) return { date: "", time: "" };
    const parsed = new Date(iso);
    if (Number.isNaN(parsed.getTime())) return { date: "", time: "" };

    // Shifting by the offset before formatting as UTC yields local wall-clock,
    // which is what the inputs display. Formatting as UTC directly would show
    // the wrong time to anyone not on UTC.
    const local = new Date(parsed.getTime() - parsed.getTimezoneOffset() * 60_000);
    const [date, clock] = local.toISOString().split("T");
    const time = clock.slice(0, 5);

    // Midnight round-trips as an empty time box, so a date-only schedule stays
    // date-only when the teacher reopens it.
    return { date, time: time === MIDNIGHT ? "" : time };
}

/**
 * Combines the two inputs into an ISO instant.
 *
 * No date means no constraint. A date without a time means the very start of
 * that day, so "deadline: the 14th" is the 14th at 00:00 local — which is what
 * a teacher means by naming a day and nothing else.
 *
 * @returns the instant, or null when there is no date
 */
export function fromDateTimeParts(parts: DateTimeParts): string | null {
    const date = parts.date.trim();
    if (!date) return null;

    const time = parts.time.trim() || MIDNIGHT;

    // Without a zone suffix this is read as local time, which is what the
    // teacher typed.
    const combined = new Date(`${date}T${time}`);
    if (Number.isNaN(combined.getTime())) return null;
    return combined.toISOString();
}
