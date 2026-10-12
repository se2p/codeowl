import { describe, it, expect } from "vitest";
import { apiErrorMessage, isNotFound, signalsSessionLoss } from "../../src/lib/api.ts";

/**
 * The backend's GlobalExceptionHandler always replies with an ApiErrorResponse
 * object ({ error, message }). Callers used to put that object straight into
 * component state and render it, which throws "Objects are not valid as a React
 * child" and — with no error boundary at the time — blanked the whole app.
 *
 * The contract these tests pin down is narrow but load-bearing: whatever comes
 * back, the result must be a non-empty string that is safe to render.
 */
describe("apiErrorMessage", () => {
    it("combines error and message from the real ApiErrorResponse shape", () => {
        const err = {
            response: {
                data: {
                    error: "Ungültige Eingabe",
                    message: "Worksheet name must not be empty.",
                },
            },
        };
        expect(apiErrorMessage(err)).toBe("Ungültige Eingabe: Worksheet name must not be empty.");
    });

    it("falls back to message alone when there is no error title", () => {
        expect(apiErrorMessage({ response: { data: { message: "boom" } } })).toBe("boom");
    });

    it("falls back to the error title alone when there is no message", () => {
        expect(apiErrorMessage({ response: { data: { error: "Zugriff verweigert" } } }))
            .toBe("Zugriff verweigert");
    });

    it("passes a plain string body through unchanged", () => {
        expect(apiErrorMessage({ response: { data: "already a string" } }))
            .toBe("already a string");
    });

    it("uses the fallback for a blank string body", () => {
        // A 401 from ResponseEntity.build() has an empty body; `??` would keep
        // the empty string and the user would see nothing at all.
        expect(apiErrorMessage({ response: { data: "   " } }, "fallback")).toBe("fallback");
    });

    it("uses the axios message when the request never reached the server", () => {
        expect(apiErrorMessage({ message: "Network Error" })).toBe("Network Error");
    });

    it("uses the fallback when there is nothing to go on", () => {
        expect(apiErrorMessage({}, "fallback")).toBe("fallback");
        expect(apiErrorMessage(undefined, "fallback")).toBe("fallback");
    });

    it("never returns a non-string, whatever the body looks like", () => {
        const bodies = [
            null,
            undefined,
            0,
            [],
            { unexpected: "shape" },
            { error: null, message: null },
        ];
        for (const data of bodies) {
            const result = apiErrorMessage({ response: { data } }, "fallback");
            expect(typeof result).toBe("string");
            expect(result.length).toBeGreaterThan(0);
        }
    });
});

describe("isNotFound", () => {
    it("recognises a 404", () => {
        expect(isNotFound({ response: { status: 404 } })).toBe(true);
    });

    it("is false for every other status, and for no response at all", () => {
        expect(isNotFound({ response: { status: 400 } })).toBe(false);
        expect(isNotFound({ response: { status: 403 } })).toBe(false);
        expect(isNotFound({ response: { status: 500 } })).toBe(false);
        expect(isNotFound({ message: "Network Error" })).toBe(false);
        expect(isNotFound(undefined)).toBe(false);
        expect(isNotFound(null)).toBe(false);
    });

    it("is why a 404 is asked about separately", () => {
        // The worksheet endpoints answer 404 with no body, on purpose: "no such worksheet" and
        // "not yours to see" must look identical. With nothing in the body apiErrorMessage
        // reaches past the caller's fallback for axios's own English message, so a page that
        // wants to say something sensible has to check the status itself.
        const emptyNotFound = { response: { status: 404, data: "" }, message: "Request failed with status code 404" };
        expect(apiErrorMessage(emptyNotFound, "eine deutsche Meldung"))
            .toBe("Request failed with status code 404");
        expect(isNotFound(emptyNotFound)).toBe(true);
    });
});

describe("signalsSessionLoss", () => {
    it("is true when a stored token is rejected", () => {
        // The case the auto-logout exists for: the token expired or was revoked while the
        // teacher was working, so the session really is gone.
        expect(signalsSessionLoss(401, true)).toBe(true);
        expect(signalsSessionLoss(403, true)).toBe(true);
    });

    it("is false for a visitor with no token, whatever the status", () => {
        // The bug this closes. An anonymous student opening a worksheet triggers requests they
        // are not entitled to make -- the concept catalogue among them -- and the refusal meant
        // "you are logged out", which sent someone who had never logged in to /login. Both codes
        // are covered because the backend answers 403, not 401, when there is no principal.
        expect(signalsSessionLoss(401, false)).toBe(false);
        expect(signalsSessionLoss(403, false)).toBe(false);
    });

    it("ignores failures that say nothing about the session", () => {
        // A 404 or a 500 is the caller's problem. Redirecting on those would hide real errors
        // behind a login page.
        expect(signalsSessionLoss(404, true)).toBe(false);
        expect(signalsSessionLoss(500, true)).toBe(false);
        expect(signalsSessionLoss(undefined, true)).toBe(false);
    });
});
