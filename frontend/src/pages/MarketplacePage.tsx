import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { api, apiErrorMessage, isNotFound } from "../lib/api.ts";
import type { MarketplaceItem, MarketplaceSearchResponse, Worksheet } from "../types/models.ts";
import { usePageTitle } from "../hooks/usePageTitle.ts";
import { useAuth } from "../context/AuthContext.tsx";
import { useMarketplaceFilters } from "../hooks/useMarketplaceFilters.ts";
import { hasAnyFilter, toRequestParams, type MarketplaceSort } from "../lib/marketplaceParams.ts";
import WorksheetCard from "../components/WorksheetCard.tsx";

const PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

/**
 * The worksheet marketplace: what other teachers have published.
 *
 * <p>Kept deliberately thin. Filter encoding is in `marketplaceParams`, the card is its own
 * component, and what is left here is fetching, paging and the copy action. The sequence
 * Library grew to 1100 lines by doing all of that in one file, which is why it has no tests.
 */
export default function MarketplacePage() {
    const { t } = useTranslation();
    usePageTitle(t("marketplace.pageTitle"));
    const navigate = useNavigate();
    const { user, isLoggedIn, isTeacher, isAdmin } = useAuth();

    const filters = useMarketplaceFilters();

    // Typing into a live search box would otherwise push a history entry per keystroke and make
    // the back button useless. The URL follows once the teacher pauses.
    const [queryDraft, setQueryDraft] = useState(filters.q);
    const firstRender = useRef(true);
    useEffect(() => {
        if (firstRender.current) {
            firstRender.current = false;
            return;
        }
        const handle = window.setTimeout(() => {
            if (queryDraft !== filters.q) filters.setQ(queryDraft);
        }, SEARCH_DEBOUNCE_MS);
        return () => window.clearTimeout(handle);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [queryDraft]);

    // The URL can also change from outside — back button, a shared link, the reset control.
    useEffect(() => {
        if (filters.q !== queryDraft) setQueryDraft(filters.q);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [filters.q]);

    const [data, setData] = useState<MarketplaceSearchResponse | null>(null);
    const [loading, setLoading] = useState(true);
    // A flag rather than a translated string: keeping the message in state would put `t` in
    // the fetch callback's dependencies -- which re-creates the callback on every render and
    // re-runs the effect -- and would leave a stale German sentence on screen after a switch
    // to English.
    const [failed, setFailed] = useState(false);
    const [copyingId, setCopyingId] = useState<string | null>(null);

    const fetchPage = useCallback(async () => {
        setLoading(true);
        setFailed(false);
        try {
            const response = await api.get<MarketplaceSearchResponse>("/library/worksheets", {
                params: toRequestParams(filters, PAGE_SIZE),
            });
            setData(response.data);
        } catch {
            setFailed(true);
        } finally {
            setLoading(false);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [filters.q, filters.lang, filters.owner, filters.sort, filters.page]);

    useEffect(() => {
        void fetchPage();
    }, [fetchPage]);

    async function handleCopy(item: MarketplaceItem) {
        setCopyingId(item.worksheetId);
        try {
            const { data: copy } = await api.post<Worksheet>(
                `/worksheets/${item.worksheetId}/copy`,
            );
            // Straight to the copy: it is the teacher's now, and adapting it is the whole point
            // of having copied it.
            navigate(`/worksheets/${copy.id}`,
                { state: { justCopied: true, from: "marketplace", search: window.location.search } });
        } catch (err: unknown) {
            // A 404 means it was unpublished or deleted between this page loading and the
            // click. Saying so is more use than a status code, and the fallback below cannot
            // do it: a 404 from these endpoints has no body, so apiErrorMessage reaches for
            // axios's own English message before ever considering the fallback.
            alert(isNotFound(err)
                    ? t("marketplace.errorGone")
                    : apiErrorMessage(err, t("marketplace.errorCopying")));
            setCopyingId(null);
        }
    }

    const canCopy = isLoggedIn && (isTeacher || isAdmin);

    // Your own worksheet is offered without a copy button. Copying it would hand you a second
    // worksheet identical to one you already own and can edit in place, and every sequence in
    // it would be duplicated too — clutter in your own library, from a button whose whole
    // purpose is acquiring material you do not have yet. The marketplace lists your worksheets
    // deliberately (the "mine" facet counts them), so this is about the action, not the row.
    //
    // ownerId is null for seed material nobody owns, which is not yours and stays copyable.
    const isOwn = (item: MarketplaceItem) => !!user && item.ownerId === user.userId;
    const totalPages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1;

    return (
        <div className="container py-4">
            <h1 className="mb-1">{t("marketplace.title")}</h1>
            <p className="text-muted">{t("marketplace.subtitle")}</p>

            <div className="row g-3 align-items-end mb-4">
                <div className="col-12 col-md-5">
                    <label className="form-label" htmlFor="marketplace-search">
                        {t("marketplace.searchLabel")}
                    </label>
                    <input
                        id="marketplace-search"
                        type="search"
                        className="form-control"
                        value={queryDraft}
                        onChange={(e) => setQueryDraft(e.target.value)}
                        placeholder={t("marketplace.searchPlaceholder") as string}
                    />
                </div>

                <div className="col-6 col-md-3">
                    <label className="form-label" htmlFor="marketplace-language">
                        {t("marketplace.langLabel")}
                    </label>
                    <select
                        id="marketplace-language"
                        className="form-select"
                        value={filters.lang}
                        onChange={(e) => filters.setLang(e.target.value)}
                    >
                        <option value="">{t("marketplace.allLangs")}</option>
                        {data?.facets.languages.map((bucket) => (
                            <option key={bucket.name} value={bucket.name}>
                                {bucket.name} ({bucket.count})
                            </option>
                        ))}
                    </select>
                </div>

                <div className="col-6 col-md-2">
                    <label className="form-label" htmlFor="marketplace-sort">
                        {t("marketplace.sortLabel")}
                    </label>
                    <select
                        id="marketplace-sort"
                        className="form-select"
                        value={filters.sort}
                        onChange={(e) => filters.setSort(e.target.value as MarketplaceSort)}
                    >
                        <option value="recent">{t("marketplace.sort.recent")}</option>
                        <option value="relevance" disabled={!filters.q}>
                            {t("marketplace.sort.relevance")}
                        </option>
                        <option value="popularity">{t("marketplace.sort.popularity")}</option>
                    </select>
                </div>

                <div className="col-12 col-md-2 d-flex gap-2">
                    {isLoggedIn && (
                        <div className="form-check">
                            <input
                                className="form-check-input"
                                type="checkbox"
                                id="marketplace-only-mine"
                                checked={filters.owner === "me"}
                                onChange={(e) => filters.setOwner(e.target.checked ? "me" : "any")}
                            />
                            <label className="form-check-label" htmlFor="marketplace-only-mine">
                                {t("marketplace.onlyMine")}
                                {data ? ` (${data.facets.mine})` : ""}
                            </label>
                        </div>
                    )}
                </div>
            </div>

            {hasAnyFilter(filters) && (
                <button className="btn btn-link btn-sm px-0 mb-3" onClick={filters.reset}>
                    {t("marketplace.clearFilters")}
                </button>
            )}

            {loading && (
                <div className="text-muted" role="status">
                    <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />
                    {t("marketplace.loading")}
                </div>
            )}

            {failed && (
                <div className="alert alert-danger" role="alert">
                    {t("marketplace.errorLoading")}
                </div>
            )}

            {!loading && !failed && data && data.items.length === 0 && (
                <div className="text-center text-muted py-5">
                    <p className="mb-1">{t("marketplace.emptyTitle")}</p>
                    <p className="small mb-0">
                        {filters.q || filters.lang || filters.owner === "me"
                            ? t("marketplace.emptyFiltered")
                            : t("marketplace.emptyNothingPublished")}
                    </p>
                </div>
            )}

            {!loading && !failed && data && data.items.length > 0 && (
                <>
                    <p className="text-muted small">
                        {t("marketplace.resultCount", { count: data.total })}
                    </p>
                    <div className="row row-cols-1 row-cols-md-2 row-cols-xl-3 g-3">
                        {data.items.map((item) => (
                            <div className="col" key={item.worksheetId}>
                                <WorksheetCard
                                    item={item}
                                    canCopy={!!canCopy && !isOwn(item)}
                                    onCopy={handleCopy}
                                    copying={copyingId === item.worksheetId}
                                />
                            </div>
                        ))}
                    </div>

                    {totalPages > 1 && (
                        <nav className="d-flex justify-content-center align-items-center gap-3 mt-4">
                            <button
                                className="btn btn-outline-secondary btn-sm"
                                disabled={filters.page === 0}
                                onClick={() => filters.setPage(filters.page - 1)}
                            >
                                {t("marketplace.previous")}
                            </button>
                            <span className="small text-muted">
                                {t("marketplace.pageOf", {
                                    page: filters.page + 1,
                                    total: totalPages,
                                })}
                            </span>
                            <button
                                className="btn btn-outline-secondary btn-sm"
                                disabled={filters.page + 1 >= totalPages}
                                onClick={() => filters.setPage(filters.page + 1)}
                            >
                                {t("marketplace.next")}
                            </button>
                        </nav>
                    )}
                </>
            )}
        </div>
    );
}
