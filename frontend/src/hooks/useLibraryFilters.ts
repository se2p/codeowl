import { useCallback, useMemo } from "react";
import { useSearchParams } from "react-router-dom";

/**
 * AP19 Teil A (A10): URL-backed filter state for the Library.
 *
 * <p>Every filter lives in the URL query string so that:
 * <ul>
 *   <li>Back/forward buttons naturally revisit previous filter states.</li>
 *   <li>Teachers can bookmark or share exact views (e.g. "all my Java rekursion sequences").</li>
 *   <li>Deep links from other parts of the app (Curriculum, Worksheet) can pre-seed filters.</li>
 * </ul>
 *
 * <p>Encoding choices:
 * <ul>
 *   <li>{@code tags} is a comma-separated list — more compact than repeated {@code tags=…} params.</li>
 *   <li>Boolean flags use {@code owner=me} / absent (no {@code owner=any} clutter).</li>
 *   <li>Defaults (e.g. {@code sort=recent}, {@code page=0}) are not serialized, keeping URLs short.</li>
 * </ul>
 */
export type LibrarySort = "recent" | "relevance" | "popularity";
export type LibraryOwner = "me" | "any";
export type LibraryTagMode = "any" | "all";

export interface LibraryFiltersState {
    q: string;
    lang: string;
    owner: LibraryOwner;
    tags: string[];
    tagMode: LibraryTagMode;
    tierCount: number | null;
    sort: LibrarySort;
    page: number;
}

export interface LibraryFiltersApi extends LibraryFiltersState {
    setQ(v: string): void;
    setLang(v: string): void;
    setOwner(v: LibraryOwner): void;
    setTagMode(v: LibraryTagMode): void;
    setTierCount(v: number | null): void;
    setSort(v: LibrarySort): void;
    setPage(v: number): void;

    /** Toggle a tag on/off, preserving all other filters; resets page to 0. */
    toggleTag(tag: string): void;

    /** Add a tag unconditionally (dedup-safe); resets page to 0. */
    addTag(tag: string): void;

    /** Remove a tag (no-op if not present); resets page to 0. */
    removeTag(tag: string): void;

    /** Clear every filter in a single router transition. */
    reset(): void;

    /** True when at least one non-default filter is active. */
    hasAnyFilter: boolean;
}

const DEFAULT_SORT: LibrarySort = "recent";
const DEFAULT_OWNER: LibraryOwner = "any";
const DEFAULT_TAG_MODE: LibraryTagMode = "any";

export function useLibraryFilters(): LibraryFiltersApi {
    const [params, setParams] = useSearchParams();

    // ── Parse ──
    const state: LibraryFiltersState = useMemo(() => {
        const q = params.get("q") ?? "";
        const lang = params.get("lang") ?? "";
        const owner = (params.get("owner") === "me" ? "me" : "any") as LibraryOwner;
        const tagMode = (params.get("tagMode") === "all" ? "all" : "any") as LibraryTagMode;
        const sortRaw = params.get("sort") ?? DEFAULT_SORT;
        const sort: LibrarySort =
            sortRaw === "relevance" || sortRaw === "popularity" ? sortRaw : DEFAULT_SORT;

        const pageRaw = parseInt(params.get("page") ?? "0", 10);
        const page = Number.isFinite(pageRaw) && pageRaw >= 0 ? pageRaw : 0;

        const tierRaw = params.get("tierCount");
        const tierCount = tierRaw ? parseInt(tierRaw, 10) : null;

        const tagsCsv = params.get("tags") ?? "";
        const tags = tagsCsv
            .split(",")
            .map((s) => s.trim())
            .filter(Boolean);

        return { q, lang, owner, tags, tagMode, tierCount, sort, page };
    }, [params]);

    // Derived: "is this URL still in its default shape?"
    const hasAnyFilter =
        state.q !== "" ||
        state.lang !== "" ||
        state.owner !== DEFAULT_OWNER ||
        state.tagMode !== DEFAULT_TAG_MODE ||
        state.tags.length > 0 ||
        state.tierCount !== null ||
        state.sort !== DEFAULT_SORT ||
        state.page !== 0;

    // ── Serialize ──
    // All mutations funnel through this helper so defaults are stripped in one place.
    const mutate = useCallback(
        (next: Partial<LibraryFiltersState>, resetPage = true) => {
            setParams(
                (prev) => {
                    const out = new URLSearchParams(prev);
                    const merged: LibraryFiltersState = {
                        ...state,
                        ...next,
                        page: resetPage ? 0 : (next.page ?? state.page),
                    };

                    write(out, "q", merged.q, "");
                    write(out, "lang", merged.lang, "");
                    write(out, "owner", merged.owner, DEFAULT_OWNER);
                    write(out, "tagMode", merged.tagMode, DEFAULT_TAG_MODE);
                    write(out, "sort", merged.sort, DEFAULT_SORT);
                    write(out, "tags", merged.tags.join(","), "");
                    write(
                        out,
                        "tierCount",
                        merged.tierCount != null ? String(merged.tierCount) : "",
                        "",
                    );
                    write(out, "page", merged.page > 0 ? String(merged.page) : "", "");

                    return out;
                },
                { replace: false },
            );
        },
        [setParams, state],
    );

    return {
        ...state,
        setQ: (v) => mutate({ q: v }),
        setLang: (v) => mutate({ lang: v }),
        setOwner: (v) => mutate({ owner: v }),
        setTagMode: (v) => mutate({ tagMode: v }),
        setTierCount: (v) => mutate({ tierCount: v }),
        setSort: (v) => mutate({ sort: v }),
        setPage: (v) => mutate({ page: v }, /* resetPage */ false),

        toggleTag: (tag) => {
            const t = tag.trim().toLowerCase();
            if (!t) return;
            const next = state.tags.includes(t)
                ? state.tags.filter((x) => x !== t)
                : [...state.tags, t];
            mutate({ tags: next });
        },

        addTag: (tag) => {
            const t = tag.trim().toLowerCase();
            if (!t || state.tags.includes(t)) return;
            mutate({ tags: [...state.tags, t] });
        },

        removeTag: (tag) => {
            const t = tag.trim().toLowerCase();
            if (!state.tags.includes(t)) return;
            mutate({ tags: state.tags.filter((x) => x !== t) });
        },

        reset: () => {
            setParams(new URLSearchParams(), { replace: false });
        },

        hasAnyFilter,
    };
}

/** Strip default values from the query string so `/library` stays tidy when nothing is set. */
function write(out: URLSearchParams, key: string, value: string, defaultValue: string) {
    if (value === defaultValue || value === "" || value == null) {
        out.delete(key);
    } else {
        out.set(key, value);
    }
}
