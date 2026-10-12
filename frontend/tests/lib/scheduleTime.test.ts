import { describe, it, expect } from "vitest";
import { toDateTimeParts, fromDateTimeParts } from "../../src/lib/scheduleTime.ts";

/**
 * The date and time inputs carry no timezone: they show wall-clock time in the
 * browser's zone, while the server stores instants. Getting this boundary wrong
 * shifts every deadline by the offset — silently, and only for people not on
 * UTC — so it is worth pinning rather than trusting.
 *
 * The time is optional, and leaving it empty means the very start of the chosen
 * day, which is what a teacher means by naming a date and nothing else.
 */
describe("schedule date and time", () => {
    it("round-trips an instant through the inputs and back", () => {
        const iso = "2026-09-02T14:30:00.000Z";
        expect(fromDateTimeParts(toDateTimeParts(iso))).toBe(iso);
    });

    it("round-trips across the year, including daylight-saving switches", () => {
        for (const iso of [
            "2026-01-15T08:00:00.000Z",
            "2026-03-29T01:30:00.000Z",
            "2026-06-21T23:59:00.000Z",
            "2026-10-25T02:15:00.000Z",
            "2026-12-31T23:00:00.000Z",
        ]) {
            expect(fromDateTimeParts(toDateTimeParts(iso))).toBe(iso);
        }
    });

    it("treats a date with no time as the start of that day", () => {
        const iso = fromDateTimeParts({ date: "2026-09-14", time: "" });
        expect(iso).not.toBeNull();

        const local = new Date(iso!);
        expect(local.getFullYear()).toBe(2026);
        expect(local.getMonth()).toBe(8); // September, zero-based
        expect(local.getDate()).toBe(14);
        expect(local.getHours()).toBe(0);
        expect(local.getMinutes()).toBe(0);
    });

    it("gives midnight back as an empty time, so a date-only schedule stays so", () => {
        // Otherwise reopening the editor would show 00:00 and the teacher could
        // not tell a deliberate midnight from a date they never gave a time to.
        const midnight = fromDateTimeParts({ date: "2026-09-14", time: "" });
        expect(toDateTimeParts(midnight)).toEqual({ date: "2026-09-14", time: "" });
    });

    it("keeps a time that is not midnight", () => {
        const iso = fromDateTimeParts({ date: "2026-09-14", time: "23:59" });
        expect(toDateTimeParts(iso)).toEqual({ date: "2026-09-14", time: "23:59" });
    });

    it("shows local wall-clock time, not UTC", () => {
        const iso = "2026-09-02T14:30:00.000Z";
        const { date, time } = toDateTimeParts(iso);
        const expected = new Date(iso);
        expect(date).toBe(
            `${expected.getFullYear()}-${String(expected.getMonth() + 1).padStart(2, "0")}-${String(
                expected.getDate(),
            ).padStart(2, "0")}`,
        );
        // Only compare the clock when it is not midnight, which reads back empty.
        if (time !== "") {
            expect(time.slice(0, 2)).toBe(String(expected.getHours()).padStart(2, "0"));
        }
    });

    it("treats an empty date as no constraint, whatever the time says", () => {
        // Empty means "available at once" or "open indefinitely", so it must
        // become null rather than some default date.
        expect(fromDateTimeParts({ date: "", time: "" })).toBeNull();
        expect(fromDateTimeParts({ date: "", time: "12:00" })).toBeNull();
        expect(toDateTimeParts(null)).toEqual({ date: "", time: "" });
        expect(toDateTimeParts(undefined)).toEqual({ date: "", time: "" });
        expect(toDateTimeParts("")).toEqual({ date: "", time: "" });
    });

    it("does not turn unparseable input into a date", () => {
        expect(toDateTimeParts("not a date")).toEqual({ date: "", time: "" });
        expect(fromDateTimeParts({ date: "not a date", time: "" })).toBeNull();
        expect(fromDateTimeParts({ date: "2026-09-14", time: "99:99" })).toBeNull();
    });

    it("produces the shapes the inputs expect", () => {
        const { date, time } = toDateTimeParts("2026-09-02T14:30:00.000Z");
        expect(date).toMatch(/^\d{4}-\d{2}-\d{2}$/);
        if (time !== "") expect(time).toMatch(/^\d{2}:\d{2}$/);
    });
});
