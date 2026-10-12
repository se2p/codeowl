/**
 * Turns whatever js-parsons' getFeedback() returned into HTML for the
 * feedback box.
 *
 * The shape depends on the grader (parsons.js:1722-1727):
 *  - LineBasedGrader, which is the one this app uses, returns `fb.errors` —
 *    a plain array of message strings, empty when the solution is correct.
 *  - The unittest/vartest graders return `{ feedback, success }` where
 *    feedback is an HTML string.
 *
 * It is never a bare string. SolvePage used to render it only when
 * `typeof fb === "string"`, so the grader's messages were silently dropped and
 * students saw nothing but the red/green colouring.
 *
 * The result is sanitised by the caller before it reaches the DOM.
 */
export function formatGraderFeedback(fb: unknown): string {
    if (!fb) return "";

    if (typeof fb === "string") return fb;

    if (Array.isArray(fb)) {
        const messages = fb
            .filter((m): m is string => typeof m === "string")
            .map((m) => m.trim())
            .filter((m) => m !== "");

        if (messages.length === 0) return "";
        if (messages.length === 1) return messages[0];
        return `<ul class="mb-0">${messages.map((m) => `<li>${m}</li>`).join("")}</ul>`;
    }

    if (typeof fb === "object" && fb !== null && "feedback" in fb) {
        const { feedback } = fb as { feedback?: unknown };
        return typeof feedback === "string" ? feedback : "";
    }

    return "";
}
