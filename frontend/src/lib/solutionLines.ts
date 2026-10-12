/**
 * Turns a stored solution into the lines a student would have seen as blocks.
 *
 * A solution is kept with the annotations js-parsons reads and then hides: `#swap:NAME` and
 * `#group:NAME` (AP16) mark interchangeable lines, `#distractor` marks a wrong one. The widget
 * strips all three before it puts a line on screen (`public/jsparsons/parsons.js`, parseCode),
 * so the block text a student's arrangement is saved as never contains them.
 *
 * That is why this exists rather than a bare `split("\n")`. Showing a teacher the raw solution
 * next to the student's blocks would put `base = 8 #swap:init` beside `base = 8` and make two
 * identical lines look different — the seed data is full of exactly that.
 *
 * Gap markers (`$$blank:…$$`, AP30) are deliberately left alone; `renderGapLine` in
 * `gapMarkers.tsx` turns those into chips at render time.
 */
export function toDisplayLines(solution: string | null | undefined): string[] {
    if (!solution) return [];

    return solution
        .split("\n")
        .map((line) =>
            line
                // Order matters only in that #distractor may follow a #swap/#group marker;
                // parsons.js strips the group markers first for the same reason.
                .replace(/#group:\S+\s*$/, "")
                .replace(/#swap:\S+\s*$/, "")
                .replace(/#distractor\s*$/, "")
                .trimEnd(),
        )
        // A solution's blank lines are layout, not blocks: the widget never deals one, so a
        // student's arrangement can never contain one either.
        .filter((line) => line.trim() !== "");
}
