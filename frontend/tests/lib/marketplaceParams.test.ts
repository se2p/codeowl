import { describe, expect, it } from "vitest";
import {
    DEFAULT_FILTERS,
    hasAnyFilter,
    type MarketplaceFilters,
    parseMarketplaceParams,
    toRequestParams,
    writeMarketplaceParams,
} from "../../src/lib/marketplaceParams";

const parse = (query: string) => parseMarketplaceParams(new URLSearchParams(query));

describe("parseMarketplaceParams", () => {
    it("reads every filter out of the query string", () => {
        expect(parse("q=rekursion&lang=java&owner=me&sort=popularity&page=2")).toEqual({
            q: "rekursion",
            lang: "java",
            owner: "me",
            sort: "popularity",
            page: 2,
        });
    });

    it("an empty query string is the default view", () => {
        expect(parse("")).toEqual(DEFAULT_FILTERS);
    });

    it("falls back rather than failing on values a URL might arrive with", () => {
        // URLs get edited, truncated and pasted from stale bookmarks. Showing the marketplace
        // is a better answer to any of that than an error page.
        expect(parse("sort=alphabetical").sort).toBe("recent");
        expect(parse("sort=").sort).toBe("recent");
        expect(parse("owner=everyone").owner).toBe("any");
        expect(parse("page=-4").page).toBe(0);
        expect(parse("page=abc").page).toBe(0);
        expect(parse("page=").page).toBe(0);
    });
});

describe("writeMarketplaceParams", () => {
    it("omits everything at its default, so the tidy view has a tidy URL", () => {
        expect(writeMarketplaceParams(DEFAULT_FILTERS).toString()).toBe("");
    });

    it("writes only what differs from the default", () => {
        const written = writeMarketplaceParams({ ...DEFAULT_FILTERS, lang: "java" });
        expect(written.get("lang")).toBe("java");
        expect(written.has("q")).toBe(false);
        expect(written.has("sort")).toBe(false);
        expect(written.has("page")).toBe(false);
    });

    it("round-trips any state back to itself", () => {
        const states: MarketplaceFilters[] = [
            DEFAULT_FILTERS,
            { q: "binäre suche", lang: "python", owner: "me", sort: "relevance", page: 3 },
            { ...DEFAULT_FILTERS, q: "a & b = c?" },
            { ...DEFAULT_FILTERS, sort: "popularity" },
        ];
        for (const state of states) {
            expect(parseMarketplaceParams(writeMarketplaceParams(state))).toEqual(state);
        }
    });

    it("escapes characters that would otherwise break the query string", () => {
        const written = writeMarketplaceParams({ ...DEFAULT_FILTERS, q: "a&b=c" });
        expect(parseMarketplaceParams(new URLSearchParams(written.toString())).q).toBe("a&b=c");
    });
});

describe("hasAnyFilter", () => {
    it("is false for the default view and true for anything else", () => {
        expect(hasAnyFilter(DEFAULT_FILTERS)).toBe(false);
        expect(hasAnyFilter({ ...DEFAULT_FILTERS, q: "x" })).toBe(true);
        expect(hasAnyFilter({ ...DEFAULT_FILTERS, lang: "java" })).toBe(true);
        expect(hasAnyFilter({ ...DEFAULT_FILTERS, owner: "me" })).toBe(true);
        expect(hasAnyFilter({ ...DEFAULT_FILTERS, sort: "popularity" })).toBe(true);
        expect(hasAnyFilter({ ...DEFAULT_FILTERS, page: 1 })).toBe(true);
    });
});

describe("toRequestParams", () => {
    it("always sends paging and sort, so the server never has to guess", () => {
        expect(toRequestParams(DEFAULT_FILTERS, 20)).toEqual({ page: 0, size: 20, sort: "recent" });
    });

    it("omits filters that are not set instead of sending empty strings", () => {
        const sent = toRequestParams(DEFAULT_FILTERS, 20);
        expect(sent).not.toHaveProperty("q");
        expect(sent).not.toHaveProperty("lang");
        expect(sent).not.toHaveProperty("owner");
    });

    it("sends what is set", () => {
        expect(toRequestParams({ q: "arrays", lang: "java", owner: "me", sort: "popularity", page: 2 }, 20))
            .toEqual({ page: 2, size: 20, sort: "popularity", q: "arrays", lang: "java", owner: "me" });
    });

    it("does not ask for relevance without a query", () => {
        // The backend corrects this too, but a request should describe something it actually
        // wants rather than relying on being fixed at the other end.
        expect(toRequestParams({ ...DEFAULT_FILTERS, sort: "relevance" }, 20).sort).toBe("recent");
        expect(toRequestParams({ ...DEFAULT_FILTERS, sort: "relevance", q: "x" }, 20).sort).toBe("relevance");
    });
});
