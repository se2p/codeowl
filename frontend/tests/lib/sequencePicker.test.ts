import { describe, it, expect } from "vitest";
import { prepareSequenceOptions, sequenceOptionTitle } from "../../src/lib/sequencePicker.ts";
import type { LibraryItem } from "../../src/types/models.ts";

/** Fills in every LibraryItem field a test doesn't care about with a harmless default. */
function item(overrides: Partial<LibraryItem> & { sequenceId: string }): LibraryItem {
    return {
        programmingLanguage: "java",
        taskDescription: null,
        semanticSummary: null,
        createdAt: null,
        ownerId: null,
        tierCount: 3,
        tags: [],
        concepts: [],
        usageCount: 0,
        highlight: null,
        alreadyInMyWorksheet: false,
        ...overrides,
    };
}

describe("prepareSequenceOptions", () => {
    const me = "user-1";

    it("filters out sequences already on the worksheet", () => {
        const items = [item({ sequenceId: "a" }), item({ sequenceId: "b" }), item({ sequenceId: "c" })];
        const result = prepareSequenceOptions(items, ["b"], me);
        expect(result.map((i) => i.sequenceId)).toEqual(["a", "c"]);
    });

    it("sorts the current user's own sequences before everyone else's", () => {
        const items = [
            item({ sequenceId: "other-1", ownerId: "user-2" }),
            item({ sequenceId: "mine-1", ownerId: me }),
            item({ sequenceId: "other-2", ownerId: "user-3" }),
            item({ sequenceId: "mine-2", ownerId: me }),
        ];
        const result = prepareSequenceOptions(items, [], me);
        expect(result.map((i) => i.sequenceId)).toEqual(["mine-1", "mine-2", "other-1", "other-2"]);
    });

    it("keeps each group's relative order (stable sort), not alphabetical or re-ranked", () => {
        // The search results arrive already ordered (recency or relevance) — reordering by
        // ownership must not scramble that order within either group.
        const items = [
            item({ sequenceId: "other-b", ownerId: "user-2" }),
            item({ sequenceId: "other-a", ownerId: "user-3" }),
            item({ sequenceId: "mine-z", ownerId: me }),
            item({ sequenceId: "mine-y", ownerId: me }),
        ];
        const result = prepareSequenceOptions(items, [], me);
        expect(result.map((i) => i.sequenceId)).toEqual(["mine-z", "mine-y", "other-b", "other-a"]);
    });

    it("treats everything as 'others' for an anonymous caller (no currentUserId)", () => {
        const items = [item({ sequenceId: "a", ownerId: null }), item({ sequenceId: "b", ownerId: "user-2" })];
        const result = prepareSequenceOptions(items, [], null);
        expect(result.map((i) => i.sequenceId)).toEqual(["a", "b"]);
    });

    it("does not treat an ownerless (legacy/anonymous) sequence as the current user's own", () => {
        // ownerId is nullable for legacy sequences (see PostgreClassDao); `currentUserId === null`
        // must not accidentally match `item.ownerId === null` and mark it "mine".
        const items = [item({ sequenceId: "legacy", ownerId: null })];
        const result = prepareSequenceOptions(items, [], me);
        expect(result).toEqual(items);
    });

    it("returns an empty list unchanged", () => {
        expect(prepareSequenceOptions([], [], me)).toEqual([]);
    });

    it("can filter out every candidate, leaving an empty list", () => {
        const items = [item({ sequenceId: "a" }), item({ sequenceId: "b" })];
        expect(prepareSequenceOptions(items, ["a", "b"], me)).toEqual([]);
    });
});

describe("sequenceOptionTitle", () => {
    it("prefers the name the owner gave the sequence", () => {
        const title = sequenceOptionTitle(
            { title: "Sorting basics", taskDescription: "Write a sorting function.", semanticSummary: "Sortiert ein Array." },
            "fallback"
        );
        expect(title).toBe("Sorting basics");
    });

    it("ignores a blank name", () => {
        const title = sequenceOptionTitle(
            { title: "  ", taskDescription: "Write a sorting function.", semanticSummary: null },
            "fallback"
        );
        expect(title).toBe("Write a sorting function.");
    });

    it("prefers the teacher-written task description", () => {
        const title = sequenceOptionTitle(
            { taskDescription: "Write a sorting function.", semanticSummary: "Sortiert ein Array." },
            "fallback"
        );
        expect(title).toBe("Write a sorting function.");
    });

    it("falls back to the LLM summary when there is no task description", () => {
        const title = sequenceOptionTitle(
            { taskDescription: null, semanticSummary: "Sortiert ein Array." },
            "fallback"
        );
        expect(title).toBe("Sortiert ein Array.");
    });

    it("treats a blank task description the same as a missing one", () => {
        const title = sequenceOptionTitle(
            { taskDescription: "   ", semanticSummary: "Sortiert ein Array." },
            "fallback"
        );
        expect(title).toBe("Sortiert ein Array.");
    });

    it("falls back to the caller's fallback text when both fields are empty", () => {
        const title = sequenceOptionTitle({ taskDescription: null, semanticSummary: null }, "fallback");
        expect(title).toBe("fallback");
    });

    it("falls back when both fields are present but blank", () => {
        const title = sequenceOptionTitle({ taskDescription: "  ", semanticSummary: "  " }, "fallback");
        expect(title).toBe("fallback");
    });
});
