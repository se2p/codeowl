/**
 * The marketplace's filter state, and how it maps to and from the URL.
 *
 * <p>Kept as plain functions over `URLSearchParams` rather than living inside the hook, so the
 * encoding can be tested on its own. `useLibraryFilters` does the same job inline and is
 * consequently untestable without a router — worth not repeating on a second search page.
 *
 * <p>The URL is the single source of truth for what is being shown. That is what makes the back
 * button work, lets a teacher bookmark "all published Java worksheets", and lets a colleague be
 * sent a link to exactly the view being talked about.
 */

export type MarketplaceSort = "recent" | "relevance" | "popularity";
export type MarketplaceOwner = "me" | "any";

export interface MarketplaceFilters {
    q: string;
    lang: string;
    owner: MarketplaceOwner;
    sort: MarketplaceSort;
    page: number;
}

export const DEFAULT_FILTERS: MarketplaceFilters = {
    q: "",
    lang: "",
    owner: "any",
    sort: "recent",
    page: 0,
};

const SORTS: readonly MarketplaceSort[] = ["recent", "relevance", "popularity"] as const;

/**
 * Reads filter state out of a query string.
 *
 * <p>Anything unrecognised falls back to its default rather than being rejected: a URL is
 * something people edit, share and truncate, and a stale bookmark should show the marketplace
 * rather than an error page.
 */
export function parseMarketplaceParams(params: URLSearchParams): MarketplaceFilters {
    const sortRaw = params.get("sort") ?? "";
    const sort = (SORTS as readonly string[]).includes(sortRaw)
        ? (sortRaw as MarketplaceSort)
        : DEFAULT_FILTERS.sort;

    const pageRaw = Number.parseInt(params.get("page") ?? "", 10);
    const page = Number.isFinite(pageRaw) && pageRaw > 0 ? pageRaw : 0;

    return {
        q: params.get("q") ?? "",
        lang: params.get("lang") ?? "",
        owner: params.get("owner") === "me" ? "me" : "any",
        sort,
        page,
    };
}

/**
 * Writes filter state back into a query string, omitting anything at its default.
 *
 * <p>So the marketplace with nothing set is `/library/worksheets`, not
 * `/library/worksheets?q=&lang=&owner=any&sort=recent&page=0`. Short URLs are the ones people
 * are willing to paste into a chat.
 */
export function writeMarketplaceParams(filters: MarketplaceFilters): URLSearchParams {
    const out = new URLSearchParams();
    if (filters.q) out.set("q", filters.q);
    if (filters.lang) out.set("lang", filters.lang);
    if (filters.owner !== DEFAULT_FILTERS.owner) out.set("owner", filters.owner);
    if (filters.sort !== DEFAULT_FILTERS.sort) out.set("sort", filters.sort);
    if (filters.page > 0) out.set("page", String(filters.page));
    return out;
}

/** Whether anything is filtered, so the page can offer a "clear" control only when it would do something. */
export function hasAnyFilter(filters: MarketplaceFilters): boolean {
    return (
        filters.q !== DEFAULT_FILTERS.q ||
        filters.lang !== DEFAULT_FILTERS.lang ||
        filters.owner !== DEFAULT_FILTERS.owner ||
        filters.sort !== DEFAULT_FILTERS.sort ||
        filters.page !== DEFAULT_FILTERS.page
    );
}

/**
 * Turns filter state into the parameters the API expects.
 *
 * <p>Separate from {@link writeMarketplaceParams} on purpose: the URL is for humans and omits
 * defaults, while the request is for the server. They happen to look alike today, and coupling
 * them would mean a change to one silently changing the other.
 *
 * <p>Sorting by relevance means nothing without a query — the backend corrects that too, but
 * asking for it in the first place would make the request describe something it does not want.
 */
export function toRequestParams(filters: MarketplaceFilters, pageSize: number): Record<string, string | number> {
    const out: Record<string, string | number> = {
        page: filters.page,
        size: pageSize,
        sort: filters.sort === "relevance" && !filters.q ? "recent" : filters.sort,
    };
    if (filters.q) out.q = filters.q;
    if (filters.lang) out.lang = filters.lang;
    if (filters.owner === "me") out.owner = "me";
    return out;
}
