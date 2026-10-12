import { describe, expect, it, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { useSequenceAccess, NO_SEQUENCE_ACCESS } from "../../src/hooks/useSequenceAccess.ts";
import { api } from "../../src/lib/api.ts";

vi.mock("../../src/lib/api.ts", () => ({
    api: { get: vi.fn() },
}));

const get = api.get as unknown as ReturnType<typeof vi.fn>;

/**
 * The permission the curator feature hangs on.
 *
 * <p>Two things matter and neither is the happy path: that a failed or unanswered request opens
 * nothing, and that an answer arriving late cannot grant rights on a sequence the teacher has
 * already navigated away from.
 */
describe("useSequenceAccess", () => {
    beforeEach(() => get.mockReset());

    it("carries the server's answer through unchanged", async () => {
        get.mockResolvedValue({ data: { canInspect: true, canEditInPlace: false, mustForkToEdit: true } });

        const { result } = renderHook(() => useSequenceAccess("seq-1", "ws-1"));

        await waitFor(() => expect(result.current.canInspect).toBe(true));
        expect(result.current).toEqual({ canInspect: true, canEditInPlace: false, mustForkToEdit: true });
        expect(get).toHaveBeenCalledWith("/worksheets/ws-1/sequences/seq-1/access");
    });

    it("asks nothing when the sequence was opened without a worksheet", () => {
        // A bare link carries no claim to curate anything, so there is nothing to ask about.
        const { result } = renderHook(() => useSequenceAccess("seq-1", null));

        expect(get).not.toHaveBeenCalled();
        expect(result.current).toEqual(NO_SEQUENCE_ACCESS);
    });

    it("closes access again when a later request fails", async () => {
        // Failing closed, observed as a transition rather than as a state that was already
        // true: the hook starts closed, so asserting "still closed" would pass even if the
        // catch had been left out entirely.
        get.mockResolvedValueOnce({ data: { canInspect: true, canEditInPlace: true, mustForkToEdit: false } });
        get.mockImplementationOnce(() => Promise.reject(new Error("network")));

        const { rerender, result } = renderHook(
            ({ seq }) => useSequenceAccess(seq, "ws-1"), { initialProps: { seq: "seq-1" } });
        await waitFor(() => expect(result.current.canInspect).toBe(true));

        rerender({ seq: "seq-2" });

        await waitFor(() => expect(result.current).toEqual(NO_SEQUENCE_ACCESS));
    });

    it("drops an answer that arrives after the teacher moved on", async () => {
        // Clicking through a worksheet's sequences is fast. Without the cancellation flag a
        // slow "yes" for the previous sequence would land on the next one and unlock it.
        let resolveFirst: (v: unknown) => void = () => {};
        get.mockReturnValueOnce(new Promise((res) => { resolveFirst = res; }));
        get.mockResolvedValueOnce({ data: NO_SEQUENCE_ACCESS });

        const { rerender, result } = renderHook(
            ({ seq }) => useSequenceAccess(seq, "ws-1"), { initialProps: { seq: "seq-1" } });

        rerender({ seq: "seq-2" });
        resolveFirst({ data: { canInspect: true, canEditInPlace: true, mustForkToEdit: false } });

        await waitFor(() => expect(get).toHaveBeenCalledTimes(2));
        expect(result.current.canInspect).toBe(false);
    });

    it("re-asks when the worksheet context changes", async () => {
        get.mockResolvedValue({ data: NO_SEQUENCE_ACCESS });

        const { rerender } = renderHook(
            ({ ws }) => useSequenceAccess("seq-1", ws), { initialProps: { ws: "ws-1" } });
        await waitFor(() => expect(get).toHaveBeenCalledTimes(1));

        rerender({ ws: "ws-2" });

        await waitFor(() => expect(get).toHaveBeenCalledWith("/worksheets/ws-2/sequences/seq-1/access"));
    });
});
