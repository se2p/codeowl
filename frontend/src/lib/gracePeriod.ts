/**
 * The grace period is entered as hours and minutes but stored as a single
 * number of minutes — one canonical unit on the server, two fields for the
 * teacher, since "1 h 30" is easier to mean than "90".
 *
 * Null throughout means no grace at all, which makes the deadline sharp. That
 * is distinct from an explicit zero only in intent, not in effect, so both are
 * normalised to null.
 */

export interface GraceParts {
    hours: string;
    minutes: string;
}

/** Splits stored minutes into the two input fields. */
export function toGraceParts(totalMinutes: number | null | undefined): GraceParts {
    if (totalMinutes == null || !Number.isFinite(totalMinutes) || totalMinutes <= 0) {
        return { hours: "", minutes: "" };
    }
    const whole = Math.floor(totalMinutes);
    return {
        hours: String(Math.floor(whole / 60)),
        minutes: String(whole % 60),
    };
}

/**
 * Combines the two fields back into minutes.
 *
 * Both empty means no grace. Either one filled treats the other as zero, so a
 * teacher can type "2" in hours and leave minutes blank.
 *
 * @returns total minutes, null for no grace, or undefined when the input is not
 *          a usable pair of whole non-negative numbers
 */
export function fromGraceParts(parts: GraceParts): number | null | undefined {
    const hoursRaw = parts.hours.trim();
    const minutesRaw = parts.minutes.trim();

    if (hoursRaw === "" && minutesRaw === "") return null;

    const hours = hoursRaw === "" ? 0 : Number(hoursRaw);
    const minutes = minutesRaw === "" ? 0 : Number(minutesRaw);

    if (!Number.isInteger(hours) || !Number.isInteger(minutes)) return undefined;
    if (hours < 0 || minutes < 0) return undefined;

    const total = hours * 60 + minutes;
    // An explicit "0 h 0 min" is a sharp deadline, which is what null means.
    return total === 0 ? null : total;
}

/** Human-readable summary of a stored grace period, e.g. "1 h 30 min". */
export function formatGrace(totalMinutes: number | null | undefined): string {
    const { hours, minutes } = toGraceParts(totalMinutes);
    if (hours === "" && minutes === "") return "";
    const h = Number(hours);
    const m = Number(minutes);
    if (h === 0) return `${m} min`;
    if (m === 0) return `${h} h`;
    return `${h} h ${m} min`;
}
