import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { api } from "../lib/api";
import { renderHighlight } from "../lib/highlight";
import { sequenceOptionTitle } from "../lib/sequencePicker";
import { useAuth } from "../context/AuthContext";
import { useLibraryFilters } from "../hooks/useLibraryFilters";
import TagChip from "../components/TagChip";
import TagChipSkeleton from "../components/TagChipSkeleton";
import TagEditor from "../components/TagEditor";
import ToastMessage from "../components/ToastMessage";
import AddToWorksheetModal from "../components/AddToWorksheetModal";
import { useProgressiveTags } from "../hooks/useProgressiveTags";
import type {
    LibraryItem,
    LibrarySearchResponse,
    LibraryFacetBucket,
    SequenceTag,
} from "../types/models";

/**
 * Response shape of {@code POST /library/sequences/{id}/copy-to-default}. Mirrors the backend
 * {@code CopyToWorksheetResponse} record — duplicated here to avoid a shared-types build step.
 */
interface CopyToWorksheetResponse {
    worksheetId: string;
    worksheetName: string;
    alreadyPresent: boolean;
    wasDefaultInbox: boolean;
}

/** Transient UI state for the "Added to … / Undo" toast. */
interface CopyToastState {
    sequenceId: string;
    worksheetId: string;
    worksheetName: string;
    alreadyPresent: boolean;
    wasDefaultInbox: boolean;
}

/**
 * Response shape of {@code POST /library/sequences/bulk-copy}. Mirrors the backend
 * {@code BulkCopyResponse} record.
 */
interface BulkCopyResponse {
    worksheetId: string;
    worksheetName: string;
    totalRequested: number;
    addedCount: number;
    alreadyPresentCount: number;
    wasDefaultInbox: boolean;
}

/** Transient UI state for the bulk-copy toast. Shape differs from single-copy — no per-item undo. */
interface BulkCopyToastState {
    worksheetId: string;
    worksheetName: string;
    addedCount: number;
    alreadyPresentCount: number;
    wasDefaultInbox: boolean;
    error?: boolean;
}

/**
 * Default tag-facet page size, and the amount "Show more" adds each click — must match the
 * backend's SequenceSearchService.FACET_TOP_TAGS_DEFAULT so the two "50"s can't drift apart.
 */
const TAG_FACET_STEP = 50;

/** Concept-facet mirror of {@link TAG_FACET_STEP} — must match FACET_TOP_CONCEPTS_DEFAULT. */
const CONCEPT_FACET_STEP = 50;

/**
 * AP19 Teil A: The Library (A9 skeleton + A10 filters + URL-state).
 *
 * <p>Architecture:
 * <ul>
 *   <li>Filter state lives entirely in the URL via {@link useLibraryFilters} — bookmarkable + shareable.</li>
 *   <li>Left sidebar holds tag facet chips (quick-filter) and ownership / tierCount toggles.</li>
 *   <li>Main column has the search bar + sort + result grid + paging.</li>
 *   <li>Search input is locally-debounced (300ms) to avoid a URL update on every keystroke.</li>
 * </ul>
 *
 * <p>Still pending (later tasks): inline tag editing (A11), one-click copy + undo toast (A12),
 * progressive LLM-tag skeleton loader (A13).
 */
export default function LibraryPage() {
    const { t } = useTranslation();
    const { isLoggedIn, user, isAdmin, isStudent } = useAuth();
    const navigate = useNavigate();
    const filters = useLibraryFilters();

    // Search-input debounce: keep the URL stable while the user is still typing.
    const [qLocal, setQLocal] = useState(filters.q);
    const firstRender = useRef(true);
    useEffect(() => {
        if (firstRender.current) {
            // Don't overwrite the URL on mount with the initial local value.
            firstRender.current = false;
            return;
        }
        const h = window.setTimeout(() => {
            if (qLocal !== filters.q) filters.setQ(qLocal);
        }, 300);
        return () => window.clearTimeout(h);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [qLocal]);

    // If the URL changes from outside (back button, shared link), sync local state.
    useEffect(() => {
        if (filters.q !== qLocal) setQLocal(filters.q);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [filters.q]);

    // ── response state ──
    const [data, setData] = useState<LibrarySearchResponse | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string>("");

    // A12: copy-to-worksheet toast state.
    const [copyToast, setCopyToast] = useState<CopyToastState | null>(null);

    // "Add to worksheet": links an existing card's sequence into a worksheet the teacher picks,
    // as opposed to Copy (above), which deep-copies it into the default inbox. One modal instance
    // for the whole grid, addressed by sequence id — see AddToWorksheetModal.
    const [addToWorksheetSequenceId, setAddToWorksheetSequenceId] = useState<string | null>(null);

    // A15c: bulk-copy state — `selectMode` flips checkboxes on every card, `selectedIds` tracks
    // picks across page loads (cleared on mode-off), `bulkCopying` disables the action button
    // during the POST, and `bulkToast` drives the aggregated result toast.
    const [selectMode, setSelectMode] = useState(false);
    const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
    const [bulkCopying, setBulkCopying] = useState(false);
    const [bulkToast, setBulkToast] = useState<BulkCopyToastState | null>(null);

    // Sidebar tag/concept facets: how many top entries the backend should return for each.
    // "Show more" bumps the relevant one by its *_FACET_STEP and re-fetches — the backend never
    // sends the whole tag or concept table unasked.
    const [tagsLimit, setTagsLimit] = useState(TAG_FACET_STEP);
    const [conceptsLimit, setConceptsLimit] = useState(CONCEPT_FACET_STEP);

    const fetchPage = useCallback(async () => {
        setLoading(true);
        setError("");
        try {
            const params: Record<string, string | number> = {
                page: filters.page,
                size: 20,
                sort: filters.sort,
                tagsLimit,
                conceptsLimit,
            };
            if (filters.q) params.q = filters.q;
            if (filters.lang) params.lang = filters.lang;
            if (filters.owner === "me") params.owner = "me";
            if (filters.tags.length > 0) {
                params.tags = filters.tags.join(",");
                if (filters.tagMode === "all") params.tagMode = "all";
            }
            if (filters.concepts.length > 0) {
                params.concepts = filters.concepts.join(",");
                if (filters.conceptMode === "all") params.conceptMode = "all";
            }
            if (filters.tierCount != null) params.tierCount = filters.tierCount;

            const res = await api.get<LibrarySearchResponse>("/library/sequences", { params });
            setData(res.data);
        } catch {
            setError(t("library.errorLoading"));
        } finally {
            setLoading(false);
        }
    }, [
        filters.page,
        filters.sort,
        filters.q,
        filters.lang,
        filters.owner,
        filters.tags,
        filters.tagMode,
        filters.concepts,
        filters.conceptMode,
        filters.tierCount,
        tagsLimit,
        conceptsLimit,
        t,
    ]);

    useEffect(() => {
        fetchPage();
    }, [fetchPage]);

    // ── Derived values ──
    const totalPages = useMemo(() => {
        if (!data || data.total === 0) return 0;
        return Math.ceil(data.total / data.size);
    }, [data]);

    // Update a single item's tags after inline edit — avoids a full refetch which would reset
    // scroll position and re-run the (possibly expensive) FTS query.
    const updateItemTags = useCallback(
        (sequenceId: string, nextTags: SequenceTag[]) => {
            setData((prev) =>
                prev
                    ? {
                          ...prev,
                          items: prev.items.map((it) =>
                              it.sequenceId === sequenceId ? { ...it, tags: nextTags } : it,
                          ),
                      }
                    : prev,
            );
        },
        [],
    );

    /** True when the current user may edit tags on this item (owner or admin). */
    const canEditTagsFor = useCallback(
        (item: LibraryItem): boolean => {
            if (!isLoggedIn) return false;
            if (isAdmin) return true;
            return !!user && user.userId === item.ownerId;
        },
        [isLoggedIn, isAdmin, user],
    );

    // ── A12: one-click copy handlers ──
    const handleCopy = useCallback(async (item: LibraryItem) => {
        try {
            const res = await api.post<CopyToWorksheetResponse>(
                `/library/sequences/${item.sequenceId}/copy-to-default`,
            );
            setCopyToast({
                sequenceId: item.sequenceId,
                worksheetId: res.data.worksheetId,
                worksheetName: res.data.worksheetName,
                alreadyPresent: res.data.alreadyPresent,
                wasDefaultInbox: res.data.wasDefaultInbox,
            });
            // Bump the card's usage count optimistically if this was a fresh add.
            if (!res.data.alreadyPresent) {
                setData((prev) =>
                    prev
                        ? {
                              ...prev,
                              items: prev.items.map((it) =>
                                  it.sequenceId === item.sequenceId
                                      ? { ...it, usageCount: it.usageCount + 1 }
                                      : it,
                              ),
                          }
                        : prev,
                );
            }
        } catch (e) {
            console.error("copy-to-default failed", e);
            setCopyToast({
                sequenceId: item.sequenceId,
                worksheetId: "",
                worksheetName: "",
                alreadyPresent: false,
                wasDefaultInbox: false,
            });
        }
    }, []);

    // ── A15c: bulk-copy handlers ──

    /** Toggle select mode. Turning it off clears the current selection — don't surprise the user
     *  by remembering a stale set across re-entries. */
    const toggleSelectMode = useCallback(() => {
        setSelectMode((prev) => {
            const next = !prev;
            if (!next) setSelectedIds(new Set());
            return next;
        });
    }, []);

    /** Toggle one sequence in/out of the current selection. Uses functional update to avoid
     *  stale-closure bugs when multiple checkboxes are flipped in quick succession. */
    const toggleSelected = useCallback((sequenceId: string) => {
        setSelectedIds((prev) => {
            const next = new Set(prev);
            if (next.has(sequenceId)) next.delete(sequenceId);
            else next.add(sequenceId);
            return next;
        });
    }, []);

    /** Fire the bulk-copy request. On success, mark the affected cards with the
     *  alreadyInMyWorksheet flag + bump usage counts optimistically, then show an aggregated toast. */
    const handleBulkCopy = useCallback(async () => {
        if (selectedIds.size === 0 || bulkCopying) return;
        const ids = Array.from(selectedIds);
        setBulkCopying(true);
        try {
            const res = await api.post<BulkCopyResponse>("/library/sequences/bulk-copy", {
                sequenceIds: ids,
            });
            setBulkToast({
                worksheetId: res.data.worksheetId,
                worksheetName: res.data.worksheetName,
                addedCount: res.data.addedCount,
                alreadyPresentCount: res.data.alreadyPresentCount,
                wasDefaultInbox: res.data.wasDefaultInbox,
            });
            // Optimistic UI: mark each copied card as in-my-library, bump usage only for freshly-added.
            // We can't cheaply tell per-ID which were duplicates, so bump all and tolerate the rare
            // over-count — it will self-correct on the next fetch.
            const selectedSet = new Set(ids);
            setData((prev) =>
                prev
                    ? {
                          ...prev,
                          items: prev.items.map((it) =>
                              selectedSet.has(it.sequenceId)
                                  ? {
                                        ...it,
                                        alreadyInMyWorksheet: true,
                                        usageCount: it.alreadyInMyWorksheet
                                            ? it.usageCount
                                            : it.usageCount + 1,
                                    }
                                  : it,
                          ),
                      }
                    : prev,
            );
            // Exit select mode on success — the operation is done, keeping checkboxes open
            // invites accidental re-copy.
            setSelectedIds(new Set());
            setSelectMode(false);
        } catch (e) {
            console.error("bulk-copy failed", e);
            setBulkToast({
                worksheetId: "",
                worksheetName: "",
                addedCount: 0,
                alreadyPresentCount: 0,
                wasDefaultInbox: false,
                error: true,
            });
        } finally {
            setBulkCopying(false);
        }
    }, [selectedIds, bulkCopying]);

    /** Undoes the most recent copy — DELETE the sequence from the worksheet we just added it to. */
    const handleUndoCopy = useCallback(async (toast: CopyToastState) => {
        if (!toast.worksheetId || toast.alreadyPresent) return;
        try {
            await api.delete(
                `/tasks/worksheets/${toast.worksheetId}/sequences/${toast.sequenceId}`,
            );
            // Decrement the optimistic usage-count bump.
            setData((prev) =>
                prev
                    ? {
                          ...prev,
                          items: prev.items.map((it) =>
                              it.sequenceId === toast.sequenceId
                                  ? { ...it, usageCount: Math.max(0, it.usageCount - 1) }
                                  : it,
                          ),
                      }
                    : prev,
            );
        } catch (e) {
            console.error("undo copy failed", e);
        }
    }, []);

    // ─────────────────────────────────────────────────────────────────────
    // Render
    // ─────────────────────────────────────────────────────────────────────

    return (
        <div className="container-fluid py-4">
            {/* Header row */}
            <div className="d-flex align-items-center justify-content-between flex-wrap gap-2 mb-3">
                <h1 className="h3 mb-0">
                    <i className="bi bi-collection me-2"></i>
                    {t("library.title")}
                </h1>
                <div className="d-flex align-items-center gap-3">
                    <span className="text-muted small">
                        {data ? t("library.resultCount", { count: data.total }) : "…"}
                    </span>
                    {filters.hasAnyFilter && (
                        <button className="btn btn-sm btn-outline-secondary" onClick={filters.reset}>
                            <i className="bi bi-x-circle me-1"></i>
                            {t("library.resetFilters")}
                        </button>
                    )}
                </div>
            </div>

            <div className="row g-3">
                {/* ─── Sidebar ──────────────────────────────── */}
                <aside className="col-12 col-lg-3">
                    <FilterSidebar
                        facets={data?.facets}
                        filters={filters}
                        isLoggedIn={isLoggedIn}
                        onShowMoreTags={() => setTagsLimit((prev) => prev + TAG_FACET_STEP)}
                        onShowMoreConcepts={() => setConceptsLimit((prev) => prev + CONCEPT_FACET_STEP)}
                    />
                </aside>

                {/* ─── Main column ──────────────────────────── */}
                <section className="col-12 col-lg-9">
                    {/* Top toolbar: search + sort */}
                    <div className="card mb-3">
                        <div className="card-body">
                            <div className="row g-2 align-items-end">
                                <div className="col-md-7">
                                    <label className="form-label small text-muted mb-1">
                                        {t("library.searchLabel")}
                                    </label>
                                    <div className="input-group">
                                        <span className="input-group-text">
                                            <i className="bi bi-search"></i>
                                        </span>
                                        <input
                                            type="search"
                                            className="form-control"
                                            placeholder={t("library.searchPlaceholder")}
                                            value={qLocal}
                                            onChange={(e) => setQLocal(e.target.value)}
                                            autoFocus
                                        />
                                        {qLocal && (
                                            <button
                                                className="btn btn-outline-secondary"
                                                onClick={() => {
                                                    setQLocal("");
                                                    filters.setQ("");
                                                }}
                                                title={t("library.clearSearch") as string}
                                            >
                                                <i className="bi bi-x"></i>
                                            </button>
                                        )}
                                    </div>
                                </div>

                                <div className="col-md-5">
                                    <label className="form-label small text-muted mb-1">
                                        {t("library.sortLabel")}
                                    </label>
                                    <select
                                        className="form-select"
                                        value={filters.sort}
                                        onChange={(e) =>
                                            filters.setSort(e.target.value as typeof filters.sort)
                                        }
                                    >
                                        <option value="recent">{t("library.sort.recent")}</option>
                                        <option value="relevance" disabled={!filters.q}>
                                            {t("library.sort.relevance")}
                                        </option>
                                        <option value="popularity">{t("library.sort.popularity")}</option>
                                    </select>
                                </div>
                            </div>

                            {/* Active-tag chip row (shown only when the user has picked tags) */}
                            {filters.tags.length > 0 && (
                                <div className="mt-3 d-flex flex-wrap gap-1 align-items-center">
                                    <span className="small text-muted me-1">
                                        {t("library.activeTags")}:
                                    </span>
                                    {filters.tags.map((tag) => (
                                        <button
                                            key={tag}
                                            className="btn btn-sm btn-primary d-inline-flex align-items-center gap-1"
                                            onClick={() => filters.removeTag(tag)}
                                            title={t("library.removeTag", { tag }) as string}
                                        >
                                            {tag}
                                            <i className="bi bi-x-circle-fill"></i>
                                        </button>
                                    ))}

                                    {filters.tags.length > 1 && (
                                        <div className="btn-group btn-group-sm ms-2" role="group">
                                            <button
                                                className={`btn ${filters.tagMode === "any" ? "btn-secondary" : "btn-outline-secondary"}`}
                                                onClick={() => filters.setTagMode("any")}
                                            >
                                                {t("library.tagMode.any")}
                                            </button>
                                            <button
                                                className={`btn ${filters.tagMode === "all" ? "btn-secondary" : "btn-outline-secondary"}`}
                                                onClick={() => filters.setTagMode("all")}
                                            >
                                                {t("library.tagMode.all")}
                                            </button>
                                        </div>
                                    )}
                                </div>
                            )}

                            {/* Active-concept chip row — same shape as the tag row above. */}
                            {filters.concepts.length > 0 && (
                                <div className="mt-3 d-flex flex-wrap gap-1 align-items-center">
                                    <span className="small text-muted me-1">
                                        {t("library.activeConcepts")}:
                                    </span>
                                    {filters.concepts.map((concept) => (
                                        <button
                                            key={concept}
                                            className="btn btn-sm btn-primary d-inline-flex align-items-center gap-1"
                                            onClick={() => filters.removeConcept(concept)}
                                            title={t("library.removeConcept", { concept }) as string}
                                        >
                                            {concept}
                                            <i className="bi bi-x-circle-fill"></i>
                                        </button>
                                    ))}

                                    {filters.concepts.length > 1 && (
                                        <div className="btn-group btn-group-sm ms-2" role="group">
                                            <button
                                                className={`btn ${filters.conceptMode === "any" ? "btn-secondary" : "btn-outline-secondary"}`}
                                                onClick={() => filters.setConceptMode("any")}
                                            >
                                                {t("library.conceptMode.any")}
                                            </button>
                                            <button
                                                className={`btn ${filters.conceptMode === "all" ? "btn-secondary" : "btn-outline-secondary"}`}
                                                onClick={() => filters.setConceptMode("all")}
                                            >
                                                {t("library.conceptMode.all")}
                                            </button>
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>
                    </div>

                    {/* ── Body: loading / error / empty / grid ── */}
                    {loading && (
                        <div className="text-center py-5">
                            <div className="spinner-border text-primary" role="status" aria-hidden="true"></div>
                            <div className="mt-2 text-muted">{t("library.loading")}</div>
                        </div>
                    )}

                    {!loading && error && <div className="alert alert-danger">{error}</div>}

                    {!loading && !error && data && data.items.length === 0 && (
                        <EmptyState
                            query={filters.q}
                            suggestions={data.suggestions}
                            onSuggestion={(s) => {
                                setQLocal(s);
                                filters.setQ(s);
                            }}
                        />
                    )}

                    {!loading && !error && data && data.items.length > 0 && (
                        <>
                            {/* A15c: Grid toolbar — hosts the "Select" toggle for bulk-copy.
                                Kept separate from the filter card so it reads as a per-results action
                                rather than a filter. Students don't see it (same rule as Copy). */}
                            {isLoggedIn && !isStudent && (
                                <div className="d-flex justify-content-end mb-2">
                                    <button
                                        className={`btn btn-sm ${selectMode ? "btn-primary" : "btn-outline-primary"}`}
                                        onClick={toggleSelectMode}
                                        title={t("library.bulkCopy.toggleTooltip") as string}
                                    >
                                        <i className={`bi ${selectMode ? "bi-check2-square" : "bi-ui-checks"} me-1`}></i>
                                        {selectMode
                                            ? t("library.bulkCopy.exitSelectMode")
                                            : t("library.bulkCopy.enterSelectMode")}
                                    </button>
                                </div>
                            )}

                            <div className="row g-3">
                                {data.items.map((item) => (
                                    <div className="col-md-6 col-xl-4" key={item.sequenceId}>
                                        <LibrarySequenceCard
                                            item={item}
                                            activeTags={filters.tags}
                                            activeConcepts={filters.concepts}
                                            onOpen={() => navigate(`/sequence/${item.sequenceId}`)}
                                            onTagClick={filters.toggleTag}
                                            onConceptClick={filters.toggleConcept}
                                            canEdit={canEditTagsFor(item)}
                                            canCopy={isLoggedIn && !isStudent}
                                            canAddToWorksheet={isLoggedIn && !isStudent}
                                            onTagsUpdated={(next) =>
                                                updateItemTags(item.sequenceId, next)
                                            }
                                            onCopy={() => handleCopy(item)}
                                            onAddToWorksheet={() =>
                                                setAddToWorksheetSequenceId(item.sequenceId)
                                            }
                                            selectMode={selectMode}
                                            isSelected={selectedIds.has(item.sequenceId)}
                                            onToggleSelected={() => toggleSelected(item.sequenceId)}
                                        />
                                    </div>
                                ))}
                            </div>

                            {totalPages > 1 && (
                                <nav className="d-flex justify-content-center mt-4">
                                    <ul className="pagination">
                                        <li className={`page-item ${filters.page === 0 ? "disabled" : ""}`}>
                                            <button
                                                className="page-link"
                                                onClick={() =>
                                                    filters.setPage(Math.max(0, filters.page - 1))
                                                }
                                                disabled={filters.page === 0}
                                            >
                                                {t("library.prev")}
                                            </button>
                                        </li>
                                        <li className="page-item disabled">
                                            <span className="page-link">
                                                {t("library.pageOf", {
                                                    page: filters.page + 1,
                                                    total: totalPages,
                                                })}
                                            </span>
                                        </li>
                                        <li
                                            className={`page-item ${filters.page + 1 >= totalPages ? "disabled" : ""}`}
                                        >
                                            <button
                                                className="page-link"
                                                onClick={() => filters.setPage(filters.page + 1)}
                                                disabled={filters.page + 1 >= totalPages}
                                            >
                                                {t("library.next")}
                                            </button>
                                        </li>
                                    </ul>
                                </nav>
                            )}
                        </>
                    )}
                </section>
            </div>

            {/* ── A12: copy-to-worksheet toast ── */}
            {copyToast && (
                <ToastMessage
                    variant={
                        copyToast.worksheetId === ""
                            ? "danger"
                            : copyToast.alreadyPresent
                                ? "info"
                                : "success"
                    }
                    message={
                        copyToast.worksheetId === ""
                            ? t("library.copyToast.errorTitle")
                            : copyToast.alreadyPresent
                                ? t("library.copyToast.alreadyTitle", { name: copyToast.worksheetName })
                                : t("library.copyToast.addedTitle", { name: copyToast.worksheetName })
                    }
                    detail={
                        copyToast.worksheetId === ""
                            ? t("library.copyToast.errorDetail") as string
                            : copyToast.wasDefaultInbox && !copyToast.alreadyPresent
                                ? t("library.copyToast.defaultDetail") as string
                                : undefined
                    }
                    primaryAction={
                        copyToast.worksheetId !== "" && !copyToast.alreadyPresent
                            ? {
                                  label: t("library.copyToast.undo") as string,
                                  icon: "bi-arrow-counterclockwise",
                                  onClick: () => handleUndoCopy(copyToast),
                              }
                            : undefined
                    }
                    secondaryAction={
                        copyToast.worksheetId !== ""
                            ? {
                                  label: t("library.copyToast.open") as string,
                                  icon: "bi-box-arrow-up-right",
                                  onClick: () =>
                                      navigate(`/worksheets/${copyToast.worksheetId}`),
                              }
                            : undefined
                    }
                    onClose={() => setCopyToast(null)}
                />
            )}

            {/* ── A15c: sticky bulk-action footer ──
                Slides in from the bottom when at least one sequence is selected. We keep it
                independent of `selectMode` rendering so that toggling select-mode off via the
                toolbar (or finishing a bulk-copy) always cleans it up via `selectedIds` going empty. */}
            {selectedIds.size > 0 && (
                <div
                    className="position-fixed bottom-0 start-0 end-0 bg-body border-top shadow-lg"
                    style={{ zIndex: 1040 }}
                    role="toolbar"
                    aria-label={t("library.bulkCopy.footerAriaLabel") as string}
                >
                    <div className="container-fluid py-3 d-flex align-items-center justify-content-between flex-wrap gap-2">
                        <span className="fw-semibold">
                            <i className="bi bi-check2-square me-2"></i>
                            {t("library.bulkCopy.countSelected", { count: selectedIds.size })}
                        </span>
                        <div className="d-flex gap-2">
                            <button
                                className="btn btn-outline-secondary"
                                onClick={() => setSelectedIds(new Set())}
                                disabled={bulkCopying}
                            >
                                <i className="bi bi-x-lg me-1"></i>
                                {t("library.bulkCopy.clear")}
                            </button>
                            <button
                                className="btn btn-primary"
                                onClick={handleBulkCopy}
                                disabled={bulkCopying}
                            >
                                {bulkCopying ? (
                                    <span
                                        className="spinner-border spinner-border-sm me-1"
                                        role="status"
                                        aria-hidden="true"
                                    ></span>
                                ) : (
                                    <i className="bi bi-clipboard-plus me-1"></i>
                                )}
                                {t("library.bulkCopy.copyAction", { count: selectedIds.size })}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* ── A15c: bulk-copy result toast ── */}
            {bulkToast && (
                <ToastMessage
                    variant={
                        bulkToast.error
                            ? "danger"
                            : bulkToast.addedCount === 0
                                ? "info"
                                : "success"
                    }
                    message={
                        bulkToast.error
                            ? t("library.bulkCopy.toast.errorTitle")
                            : bulkToast.addedCount === 0
                                ? t("library.bulkCopy.toast.allDuplicatesTitle", {
                                      count: bulkToast.alreadyPresentCount,
                                      name: bulkToast.worksheetName,
                                  })
                                : t("library.bulkCopy.toast.addedTitle", {
                                      count: bulkToast.addedCount,
                                      name: bulkToast.worksheetName,
                                  })
                    }
                    detail={
                        bulkToast.error
                            ? (t("library.bulkCopy.toast.errorDetail") as string)
                            : bulkToast.alreadyPresentCount > 0 && bulkToast.addedCount > 0
                                ? (t("library.bulkCopy.toast.skippedDetail", {
                                      count: bulkToast.alreadyPresentCount,
                                  }) as string)
                                : bulkToast.wasDefaultInbox
                                    ? (t("library.copyToast.defaultDetail") as string)
                                    : undefined
                    }
                    secondaryAction={
                        !bulkToast.error && bulkToast.worksheetId
                            ? {
                                  label: t("library.copyToast.open") as string,
                                  icon: "bi-box-arrow-up-right",
                                  onClick: () =>
                                      navigate(`/worksheets/${bulkToast.worksheetId}`),
                              }
                            : undefined
                    }
                    onClose={() => setBulkToast(null)}
                />
            )}

            {/* ── Add-to-worksheet modal ── */}
            {addToWorksheetSequenceId && (
                <AddToWorksheetModal
                    sequenceId={addToWorksheetSequenceId}
                    onClose={() => setAddToWorksheetSequenceId(null)}
                />
            )}
        </div>
    );
}

// ─────────────────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────────────────

/** Left sidebar: language dropdown, ownership toggle, tier-count chips, top-tag cloud. */
function FilterSidebar({
    facets,
    filters,
    isLoggedIn,
    onShowMoreTags,
    onShowMoreConcepts,
}: {
    facets: import("../types/models").LibraryFacets | undefined;
    filters: ReturnType<typeof useLibraryFilters>;
    isLoggedIn: boolean;
    /** Asks the parent to re-fetch with a higher tagsLimit — "Show more" is a real request. */
    onShowMoreTags: () => void;
    /** Concept-facet mirror of {@link onShowMoreTags}. */
    onShowMoreConcepts: () => void;
}) {
    const { t } = useTranslation();

    // Tier counts that actually occur under the other filters. The active one is kept even if
    // it has no matches (e.g. from a shared URL), so it can still be seen and switched off.
    const tierOptions = Array.from(new Set([
        ...(facets?.tierCounts ?? []).map((b) => Number(b.name)).filter((n) => n > 0),
        ...(filters.tierCount != null ? [filters.tierCount] : []),
    ])).sort((a, b) => a - b);

    // Whether the tag/concept cloud sections are shown at all — pure display state, independent
    // of how many entries the backend sent (that's tagsLimit/conceptsLimit, owned by the parent).
    const [tagsVisible, setTagsVisible] = useState(true);
    const allTags = facets?.topTags ?? [];
    const totalTagCount = facets?.totalTagCount ?? 0;
    const hasMoreTags = allTags.length < totalTagCount;

    const [conceptsVisible, setConceptsVisible] = useState(true);
    const allConcepts = facets?.topConcepts ?? [];
    const totalConceptCount = facets?.totalConceptCount ?? 0;
    const hasMoreConcepts = allConcepts.length < totalConceptCount;

    return (
        <div className="card sticky-top" style={{ top: "1rem" }}>
            <div className="card-body">
                {/* ── Language ── */}
                <h6 className="text-uppercase text-muted small mb-2">
                    <i className="bi bi-globe me-1"></i>
                    {t("library.langLabel")}
                </h6>
                <select
                    className="form-select form-select-sm mb-3"
                    value={filters.lang}
                    onChange={(e) => filters.setLang(e.target.value)}
                >
                    <option value="">{t("library.allLangs")}</option>
                    {facets?.languages.map((b) => (
                        <option key={b.name} value={b.name}>
                            {b.name} ({b.count})
                        </option>
                    ))}
                </select>

                {/* ── Ownership ── */}
                <h6 className="text-uppercase text-muted small mb-2">
                    <i className="bi bi-person-badge me-1"></i>
                    {t("library.ownerLabel")}
                </h6>
                <div className="btn-group btn-group-sm w-100 mb-3" role="group">
                    <button
                        className={`btn ${filters.owner === "any" ? "btn-secondary" : "btn-outline-secondary"}`}
                        onClick={() => filters.setOwner("any")}
                    >
                        {t("library.ownerAny")}
                        {facets && (
                            <span className="ms-1 badge text-bg-light">
                                {facets.ownership.mine + facets.ownership.others}
                            </span>
                        )}
                    </button>
                    <button
                        className={`btn ${filters.owner === "me" ? "btn-secondary" : "btn-outline-secondary"}`}
                        onClick={() => filters.setOwner("me")}
                        disabled={!isLoggedIn}
                        title={!isLoggedIn ? (t("library.ownerMeDisabled") as string) : undefined}
                    >
                        {t("library.ownerMe")}
                        {facets && (
                            <span className="ms-1 badge text-bg-light">{facets.ownership.mine}</span>
                        )}
                    </button>
                </div>

                {/* ── Tier count quick-filter ── */}
                <h6 className="text-uppercase text-muted small mb-2">
                    <i className="bi bi-layers me-1"></i>
                    {t("library.tierLabel")}
                </h6>
                <div className="btn-group btn-group-sm w-100 mb-3" role="group">
                    <TierButton value={null} filters={filters} label={t("library.tierAny")} />
                    {tierOptions.map((n) => (
                        <TierButton key={n} value={n} filters={filters} label={String(n)} />
                    ))}
                </div>

                {/* ── Tag cloud ──
                    tagsLimit (owned by the parent) bounds what the backend sends; totalTagCount
                    is the true count regardless of that limit, so "Show more" knows whether
                    there's actually anything left and re-requests for exactly TAG_FACET_STEP
                    more when clicked. */}
                <h6 className="text-uppercase text-muted small mb-2 d-flex justify-content-between align-items-center">
                    <span>
                        <i className="bi bi-tags me-1"></i>
                        {t("library.tagsLabel")}
                    </span>
                    <span className="d-flex align-items-center gap-2">
                        {totalTagCount > 0 && (
                            <span className="text-muted">{totalTagCount}</span>
                        )}
                        {totalTagCount > 0 && (
                            <button
                                type="button"
                                className="btn btn-sm btn-link text-muted p-0 text-decoration-none"
                                style={{ fontSize: "0.78rem" }}
                                onClick={() => setTagsVisible((v) => !v)}
                                aria-expanded={tagsVisible}
                            >
                                {tagsVisible ? t("library.hideTags") : t("library.showTags")}
                            </button>
                        )}
                    </span>
                </h6>
                {totalTagCount === 0 && (
                    <div className="small text-muted">{t("library.noTagsYet")}</div>
                )}
                {tagsVisible && totalTagCount > 0 && (
                    <>
                        <div className="d-flex flex-wrap gap-1" style={{ maxHeight: "22rem", overflowY: "auto" }}>
                            {allTags.map((b) => (
                                <TagFacetChip key={b.name} bucket={b} filters={filters} />
                            ))}
                        </div>
                        {hasMoreTags && (
                            <button
                                type="button"
                                className="btn btn-sm btn-outline-secondary w-100 mt-2"
                                onClick={onShowMoreTags}
                            >
                                {t("library.showMoreTags")}
                            </button>
                        )}
                    </>
                )}

                {/* ── Concept cloud — same shape as the tag cloud above. ── */}
                <h6 className="text-uppercase text-muted small mb-2 mt-3 d-flex justify-content-between align-items-center">
                    <span>
                        <i className="bi bi-diagram-3 me-1"></i>
                        {t("library.conceptsLabel")}
                    </span>
                    <span className="d-flex align-items-center gap-2">
                        {totalConceptCount > 0 && (
                            <span className="text-muted">{totalConceptCount}</span>
                        )}
                        {totalConceptCount > 0 && (
                            <button
                                type="button"
                                className="btn btn-sm btn-link text-muted p-0 text-decoration-none"
                                style={{ fontSize: "0.78rem" }}
                                onClick={() => setConceptsVisible((v) => !v)}
                                aria-expanded={conceptsVisible}
                            >
                                {conceptsVisible ? t("library.hideConcepts") : t("library.showConcepts")}
                            </button>
                        )}
                    </span>
                </h6>
                {totalConceptCount === 0 && (
                    <div className="small text-muted">{t("library.noConceptsYet")}</div>
                )}
                {conceptsVisible && totalConceptCount > 0 && (
                    <>
                        <div className="d-flex flex-wrap gap-1" style={{ maxHeight: "22rem", overflowY: "auto" }}>
                            {allConcepts.map((b) => (
                                <ConceptFacetChip key={b.name} bucket={b} filters={filters} />
                            ))}
                        </div>
                        {hasMoreConcepts && (
                            <button
                                type="button"
                                className="btn btn-sm btn-outline-secondary w-100 mt-2"
                                onClick={onShowMoreConcepts}
                            >
                                {t("library.showMoreConcepts")}
                            </button>
                        )}
                    </>
                )}
            </div>
        </div>
    );
}

function TierButton({
    value,
    filters,
    label,
}: {
    value: number | null;
    filters: ReturnType<typeof useLibraryFilters>;
    label: string;
}) {
    const active = filters.tierCount === value;
    return (
        <button
            className={`btn ${active ? "btn-secondary" : "btn-outline-secondary"}`}
            onClick={() => filters.setTierCount(value)}
        >
            {label}
        </button>
    );
}

function TagFacetChip({
    bucket,
    filters,
}: {
    bucket: LibraryFacetBucket;
    filters: ReturnType<typeof useLibraryFilters>;
}) {
    const active = filters.tags.includes(bucket.name);
    return (
        <button
            className={`btn btn-sm ${active ? "btn-primary" : "btn-outline-secondary"} d-inline-flex align-items-center gap-1`}
            onClick={() => filters.toggleTag(bucket.name)}
            style={{ fontSize: "0.78rem" }}
        >
            <span>{bucket.name}</span>
            <span className={`badge ${active ? "text-bg-light text-dark" : "text-bg-secondary"}`}>
                {bucket.count}
            </span>
        </button>
    );
}

/** Concept-facet mirror of {@link TagFacetChip}. */
function ConceptFacetChip({
    bucket,
    filters,
}: {
    bucket: LibraryFacetBucket;
    filters: ReturnType<typeof useLibraryFilters>;
}) {
    const active = filters.concepts.includes(bucket.name);
    return (
        <button
            className={`btn btn-sm ${active ? "btn-primary" : "btn-outline-secondary"} d-inline-flex align-items-center gap-1`}
            onClick={() => filters.toggleConcept(bucket.name)}
            style={{ fontSize: "0.78rem" }}
        >
            <span>{bucket.name}</span>
            <span className={`badge ${active ? "text-bg-light text-dark" : "text-bg-secondary"}`}>
                {bucket.count}
            </span>
        </button>
    );
}

/**
 * One card in the Library grid.
 *
 * <p>Tags are clickable → they toggle the filter (A10). If {@code canEdit} is true (owner/admin),
 * a pencil button reveals the {@link TagEditor} panel with autocomplete + remove (A11).
 */
function LibrarySequenceCard({
    item,
    activeTags,
    activeConcepts,
    onOpen,
    onTagClick,
    onConceptClick,
    canEdit,
    canCopy,
    canAddToWorksheet,
    onTagsUpdated,
    onCopy,
    onAddToWorksheet,
    selectMode,
    isSelected,
    onToggleSelected,
}: {
    item: LibraryItem;
    activeTags: string[];
    activeConcepts: string[];
    onOpen: () => void;
    onTagClick: (tag: string) => void;
    onConceptClick: (concept: string) => void;
    canEdit: boolean;
    canCopy: boolean;
    canAddToWorksheet: boolean;
    onTagsUpdated: (next: SequenceTag[]) => void;
    onCopy: () => void;
    onAddToWorksheet: () => void;
    /** When true, the card shows a top-right checkbox and clicking the card body toggles selection. */
    selectMode: boolean;
    isSelected: boolean;
    onToggleSelected: () => void;
}) {
    const { t } = useTranslation();
    const [editingTags, setEditingTags] = useState(false);
    const [copying, setCopying] = useState(false);

    // A13: Progressive LLM-tag flow-in. For freshly-generated sequences (<90s old) that lack an
    // LLM tag, the hook polls /library/sequences/{id}/tags every 2s until the async tagger lands
    // one (or we time out at 60s). `tags` replaces `item.tags` for render so the chip stream is
    // live without requiring a full page refetch.
    const { tags, isWaitingForLlm } = useProgressiveTags(
        item.sequenceId,
        item.tags,
        item.createdAt,
    );

    // Capture which tag names were known at mount so we can animate only the ones that arrive
    // later (LLM or manual adds). Existing chips on first render should appear instantly.
    const initialTagNamesRef = useRef<Set<string>>(
        new Set(item.tags.map((t) => t.tagName)),
    );

    // When the polling loop finishes (LLM arrived or timed out), bubble the fresh list up so the
    // parent's `items` state stays in sync and a re-render (e.g. filter change) doesn't forget
    // the new tags. Only fires on the true→false transition to avoid an update loop.
    const wasWaitingRef = useRef(isWaitingForLlm);
    useEffect(() => {
        if (wasWaitingRef.current && !isWaitingForLlm) {
            onTagsUpdated(tags);
        }
        wasWaitingRef.current = isWaitingForLlm;
    }, [isWaitingForLlm, tags, onTagsUpdated]);

    const title = sequenceOptionTitle(item, t("library.untitled"));
    // A named sequence would otherwise lose its task description, which is the title of an
    // unnamed one; it moves under the name instead.
    const subtitle = item.title?.trim() ? item.taskDescription?.trim() : undefined;

    // A15c: visual affordance for select mode. A selected card gets a primary-border ring so the
    // user can scan their picks at a glance while scrolling a large grid.
    const cardClassName = [
        "card h-100 shadow-sm library-card",
        selectMode ? "library-card-selectable position-relative" : "",
        isSelected ? "border-primary border-2" : "",
    ]
        .filter(Boolean)
        .join(" ");

    return (
        <div className={cardClassName}>
            {/* A15c: top-right checkbox overlay. Rendered only in select mode so it doesn't
                clutter the default card. Uses a stop-propagation wrapper because we also want
                the card body's click-to-toggle (below) to work without double-firing. */}
            {selectMode && (
                <div
                    className="position-absolute top-0 end-0 m-2 bg-body rounded-circle border shadow-sm"
                    style={{ width: "2rem", height: "2rem", zIndex: 2 }}
                >
                    <input
                        type="checkbox"
                        className="form-check-input m-0"
                        style={{
                            position: "absolute",
                            top: "50%",
                            left: "50%",
                            transform: "translate(-50%, -50%)",
                            width: "1.15rem",
                            height: "1.15rem",
                            cursor: "pointer",
                        }}
                        checked={isSelected}
                        onChange={onToggleSelected}
                        aria-label={t("library.bulkCopy.toggleCardAria") as string}
                    />
                </div>
            )}
            <div className="card-body d-flex flex-column">
                <div className="d-flex align-items-center gap-2 mb-2">
                    <span className="badge text-bg-primary text-uppercase">
                        {item.programmingLanguage || "—"}
                    </span>
                    <span className="badge text-bg-light text-muted">
                        {t("library.tierBadge", { count: item.tierCount })}
                    </span>
                    {item.usageCount > 0 && (
                        <span className="badge text-bg-light text-muted" title={t("library.usageTooltip") as string}>
                            <i className="bi bi-bookmark-star me-1"></i>
                            {item.usageCount}
                        </span>
                    )}
                    {/* A15: "Already in your library" indicator. Shows for sequences the current user
                        already has in one of their worksheets — so the Copy button is clearly a
                        no-op add on re-click and the teacher can spot duplicates at a glance. */}
                    {item.alreadyInMyWorksheet && (
                        <span
                            className="badge text-bg-success-subtle text-success-emphasis"
                            title={t("library.alreadyInLibraryTooltip") as string}
                        >
                            <i className="bi bi-check-circle-fill me-1"></i>
                            {t("library.alreadyInLibrary")}
                        </span>
                    )}
                </div>

                <h5 className="card-title mb-1" style={{ fontSize: "1rem" }}>
                    {title}
                </h5>
                {subtitle && !item.highlight && (
                    <p className="card-text small text-muted mb-2">{subtitle}</p>
                )}

                {/* The sequence's semanticSummary is deliberately NOT rendered here. It is an
                    LLM-generated one-liner that the tagger prompt intends as a card tooltip; as
                    visible body text it read like a second task description competing with the
                    title, and it appeared nowhere else in the app (the detail view never showed
                    it). The field itself is untouched: still generated by SemanticTaggingListener,
                    still stored, still weighted into search_vector, and still the card-title
                    fallback above — only this paragraph is gone. */}
                {item.highlight ? (
                    // A14: highlight is a ts_headline snippet with control-char markers.
                    // renderHighlight() escapes the whole payload before swapping markers for
                    // <mark>, so user-supplied HTML in the source text can't break out.
                    <p
                        className="card-text small text-muted mb-2"
                        dangerouslySetInnerHTML={{ __html: renderHighlight(item.highlight) }}
                    />
                ) : null}

                {/* ── Tag region: read-only chips OR full editor ── */}
                {editingTags ? (
                    <div className="mb-3 p-2 bg-body-tertiary rounded border">
                        <div className="d-flex justify-content-between align-items-center mb-2">
                            <span className="small text-muted fw-semibold">
                                <i className="bi bi-tag-fill me-1"></i>
                                {t("library.editTags")}
                            </span>
                            <button
                                className="btn btn-sm btn-outline-secondary"
                                onClick={() => setEditingTags(false)}
                            >
                                <i className="bi bi-check-lg me-1"></i>
                                {t("library.doneEditing")}
                            </button>
                        </div>
                        <TagEditor
                            sequenceId={item.sequenceId}
                            tags={tags}
                            onTagsChanged={onTagsUpdated}
                        />
                    </div>
                ) : (
                    (tags.length > 0 || isWaitingForLlm) && (
                        <div className="d-flex flex-wrap gap-1 mb-3 align-items-center">
                            {tags.slice(0, 6).map((tag) => (
                                <TagChip
                                    key={`${tag.tagName}-${tag.source}`}
                                    tag={tag}
                                    active={activeTags.includes(tag.tagName)}
                                    onClick={() => onTagClick(tag.tagName)}
                                    fadeIn={!initialTagNamesRef.current.has(tag.tagName)}
                                />
                            ))}
                            {tags.length > 6 && (
                                <span className="badge text-bg-light text-muted">
                                    +{tags.length - 6}
                                </span>
                            )}
                            {/* A13: skeleton while the LLM tagger is still running on this sequence. */}
                            {isWaitingForLlm && (
                                <TagChipSkeleton ariaLabel={t("library.tagLoading") as string} />
                            )}
                            {canEdit && (
                                <button
                                    className="btn btn-sm btn-link text-muted p-0 ms-1"
                                    title={t("library.editTags") as string}
                                    onClick={() => setEditingTags(true)}
                                    style={{ fontSize: "0.85rem" }}
                                >
                                    <i className="bi bi-pencil-square"></i>
                                </button>
                            )}
                        </div>
                    )
                )}

                {/* No tags at all + canEdit → offer a single "Add tags" entry point. */}
                {!editingTags && tags.length === 0 && !isWaitingForLlm && canEdit && (
                    <div className="mb-3">
                        <button
                            className="btn btn-sm btn-outline-secondary"
                            onClick={() => setEditingTags(true)}
                        >
                            <i className="bi bi-plus-lg me-1"></i>
                            {t("library.addTags")}
                        </button>
                    </div>
                )}

                {/* Concepts the sequence covers — read-only here (editing stays on the
                    sequence detail page). Clicking one toggles it as a library filter,
                    the same way a tag chip does. Distinguished from the tag row above by
                    the diagram icon, mirroring the sidebar's Tags/Concepts split. */}
                {item.concepts.length > 0 && (
                    <div className="d-flex flex-wrap gap-1 mb-3 align-items-center">
                        <i
                            className="bi bi-diagram-3 text-muted"
                            aria-hidden="true"
                            style={{ fontSize: "0.8rem" }}
                        ></i>
                        {item.concepts.slice(0, 6).map((concept) => {
                            const active = activeConcepts.includes(concept);
                            return (
                                <button
                                    key={concept}
                                    type="button"
                                    className={`badge border-0 ${active ? "text-bg-primary" : "bg-secondary-subtle text-secondary-emphasis"}`}
                                    style={{ cursor: "pointer", fontWeight: 500, fontSize: "0.78rem" }}
                                    onClick={() => onConceptClick(concept)}
                                >
                                    {concept}
                                </button>
                            );
                        })}
                        {item.concepts.length > 6 && (
                            <span className="badge text-bg-light text-muted">
                                +{item.concepts.length - 6}
                            </span>
                        )}
                    </div>
                )}

                <div className="mt-auto d-flex gap-2">
                    <Link
                        to={`/sequence/${item.sequenceId}`}
                        className="btn btn-sm btn-outline-primary flex-shrink-0"
                        title={t("library.open") as string}
                        aria-label={t("library.open") as string}
                        onClick={(e) => {
                            e.preventDefault();
                            onOpen();
                        }}
                    >
                        <i className="bi bi-eye"></i>
                    </Link>
                    <button
                        className="btn btn-sm btn-outline-success flex-fill"
                        disabled={!canCopy || copying}
                        title={
                            !canCopy
                                ? (t("library.copyLoginRequired") as string)
                                : (t("library.copyTooltip") as string)
                        }
                        onClick={async () => {
                            setCopying(true);
                            try {
                                await onCopy();
                            } finally {
                                setCopying(false);
                            }
                        }}
                    >
                        {copying ? (
                            <span
                                className="spinner-border spinner-border-sm me-1"
                                role="status"
                                aria-hidden="true"
                            ></span>
                        ) : (
                            <i className="bi bi-clipboard-plus me-1"></i>
                        )}
                        {t("library.copy")}
                    </button>
                    <button
                        className="btn btn-sm btn-outline-secondary flex-fill"
                        disabled={!canAddToWorksheet}
                        title={
                            !canAddToWorksheet
                                ? (t("library.addToWorksheetLoginRequired") as string)
                                : (t("sequence.addToWorksheetTitle") as string)
                        }
                        onClick={onAddToWorksheet}
                    >
                        <i className="bi bi-journal-plus me-1"></i>
                        {t("sequence.addToWorksheet")}
                    </button>
                </div>
            </div>
        </div>
    );
}

function EmptyState({
    query,
    suggestions,
    onSuggestion,
}: {
    query: string;
    suggestions: string[];
    onSuggestion: (s: string) => void;
}) {
    const { t } = useTranslation();
    return (
        <div className="text-center py-5">
            <i className="bi bi-search display-5 text-muted"></i>
            <h5 className="mt-3">{t("library.empty.title")}</h5>
            {query && (
                <p className="text-muted">
                    {t("library.empty.forQuery", { query })}
                </p>
            )}
            {suggestions.length > 0 && (
                <div className="mt-3">
                    <div className="small text-muted mb-2">{t("library.empty.didYouMean")}</div>
                    <div className="d-flex flex-wrap justify-content-center gap-1">
                        {suggestions.map((s) => (
                            <button
                                key={s}
                                className="btn btn-sm btn-outline-secondary"
                                onClick={() => onSuggestion(s)}
                            >
                                {s}
                            </button>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
}
