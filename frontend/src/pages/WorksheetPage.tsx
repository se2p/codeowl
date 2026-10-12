import { useEffect, useState } from "react";
import { useParams, useNavigate, useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { api, apiErrorMessage, isNotFound } from "../lib/api.ts";
import type { Worksheet, ClassInfo, LibraryItem, LibrarySearchResponse, WorksheetVisibility } from "../types/models.ts";
import { usePageTitle } from "../hooks/usePageTitle.ts";
import { useHomePath } from "../hooks/useHomePath.ts";
import { marketplaceBackLink } from "../lib/worksheetBackLink.ts";
import { ListGroup } from "react-bootstrap";
import 'bootstrap/dist/css/bootstrap.min.css';
import { useAuth } from "../context/AuthContext.tsx";
import { renderHighlight } from "../lib/highlight.ts";
import { prepareSequenceOptions, sequenceOptionTitle } from "../lib/sequencePicker.ts";
import VisibilityToggle from "../components/VisibilityToggle.tsx";
import { canOfferVisibilityControl, effectiveVisibility } from "../lib/worksheetVisibility.ts";
import type { ExportableWorksheet, ExportableSequence } from "../lib/export.ts";
import {
    exportWorksheetJSON,
    exportWorksheetCSV,
    exportWorksheetPDF,
} from "../lib/export.ts";
import {
    DndContext,
    closestCenter,
    PointerSensor,
    useSensor,
    useSensors,
    type DragEndEvent,
} from "@dnd-kit/core";
import { SortableContext, verticalListSortingStrategy, arrayMove } from "@dnd-kit/sortable";
import SortableSequenceRow from "../components/SortableSequenceRow.tsx";
import TagChip from "../components/TagChip.tsx";
import { useSequenceDetails } from "../hooks/useSequenceDetails.ts";

/** How much per-sequence detail the teacher-view list shows. See useSequenceDetails. */
type SequenceDetailLevel = "none" | "some" | "all";

export default function WorksheetPage() {
    const { t } = useTranslation();
    usePageTitle(t("worksheet.pageTitle"));
    const homePath = useHomePath();
    const { sheetId } = useParams<{ sheetId: string }>();
    const navigate = useNavigate();
    const location = useLocation();
    const justCopied = (location.state as any)?.justCopied === true;

    // Only show "back to class" when navigated FROM a class (via location.state),
    // the same way the sequence page decides on "back to worksheet".
    const fromClass = (location.state as { fromClass?: string })?.fromClass ?? null;

    // The marketplace is a third way in, and it carries its filters along so returning lands
    // on the list the teacher was reading rather than an unfiltered one.
    const marketplaceBack = marketplaceBackLink(location.state);
    const { user, isLoggedIn, isTeacher, isAdmin } = useAuth();
    const [worksheet, setWorksheet] = useState<Worksheet | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [showModal, setShowModal] = useState(false);
    const [sequenceId, setSequenceId] = useState("");
    const [adding, setAdding] = useState(false);
    const [modalError, setModalError] = useState<string | null>(null);
    const [addMode, setAddMode] = useState<"search" | "single" | "batch">("search");
    const [batchInput, setBatchInput] = useState("");

    // Add-sequence-by-search: same live/debounced-search shape as the Library page, scoped to
    // this modal. Own sequences are sorted first client-side (see searchSequences) — a teacher
    // adding a sequence to their own worksheet is almost always looking for one of their own.
    const [sequenceSearch, setSequenceSearch] = useState("");
    const [selectedSequence, setSelectedSequence] = useState<LibraryItem | null>(null);
    const [sequenceOptions, setSequenceOptions] = useState<LibraryItem[]>([]);
    const [loadingSequenceOptions, setLoadingSequenceOptions] = useState(false);
    const [showSequenceDropdown, setShowSequenceDropdown] = useState(false);
    const [copied, setCopied] = useState(false);
    const [tipsVisible, setTipsVisible] = useState(true);

    // Role-based teacher view: show toggle for owners and admins
    const isOwnerOrAdmin = isLoggedIn && worksheet && user && (worksheet.ownerId === user.userId || isAdmin);
    const canToggleTeacherView = !!isOwnerOrAdmin;
    const [teacherViewEnabled, setTeacherViewEnabled] = useState(false);

    // Auto-enable teacher view when worksheet loads and user is owner/admin
    useEffect(() => {
        if (isOwnerOrAdmin) {
            setTeacherViewEnabled(true);
        } else {
            setTeacherViewEnabled(false);
        }
    }, [isOwnerOrAdmin]);

    // Copy worksheet state
    const [copying, setCopying] = useState(false);

    // The visibility control is offered to owners and admins, but never for the Library Inbox:
    // the backend refuses to publish it, and a button that always fails is worse than none.
    const canSetVisibility = canOfferVisibilityControl(worksheet, !!isOwnerOrAdmin);

    async function handleVisibilityChange(next: WorksheetVisibility) {
        if (!sheetId) return;
        // Deliberately not caught here: VisibilityToggle reverts its own selection on a
        // rejected promise, which is the only place that knows what to revert to.
        const { data } = await api.put<Worksheet>(`/worksheets/${sheetId}/visibility`, {
            visibility: next,
        });
        setWorksheet(data);
    }

    // Assign to class state
    const [showAssignModal, setShowAssignModal] = useState(false);
    const [myClasses, setMyClasses] = useState<ClassInfo[]>([]);
    const [loadingClasses, setLoadingClasses] = useState(false);
    const [assigningClassId, setAssigningClassId] = useState<string | null>(null);
    const [assignError, setAssignError] = useState<string | null>(null);
    const [assignSuccess, setAssignSuccess] = useState<string | null>(null);

    // Determine if current user is the owner
    const isForeignWorksheet = isLoggedIn && worksheet && user && worksheet.ownerId !== user.userId;
    const canCopy = isLoggedIn && (isTeacher || isAdmin) && isForeignWorksheet;
    const canAssign = isLoggedIn && (isTeacher || isAdmin);
    const canEdit = teacherViewEnabled && isOwnerOrAdmin;

    // How much per-sequence detail the teacher-view list shows below the "Sequence N" label.
    // "some" (language + description) is the default — going by number and UUID alone is not
    // enough to tell sequences apart at a glance. Fetching is lazy and level-gated: see
    // useSequenceDetails.
    const [detailLevel, setDetailLevel] = useState<SequenceDetailLevel>("some");
    const sequenceIds = worksheet?.sequenceDTOS.map((s) => s.id) ?? [];
    const { details: sequenceDetails, tags: sequenceTagsById } = useSequenceDetails(
        sequenceIds,
        teacherViewEnabled && detailLevel !== "none",
        detailLevel === "all",
    );

    // Edit worksheet state
    const [editing, setEditing] = useState(false);
    const [editName, setEditName] = useState("");
    const [editDescription, setEditDescription] = useState("");
    const [saving, setSaving] = useState(false);

    // Hint credit input, kept as a string so the field can be empty mid-edit.
    const [creditsInput, setCreditsInput] = useState("5");

    useEffect(() => {
        if (worksheet?.hintCreditsPerTask != null) {
            setCreditsInput(String(worksheet.hintCreditsPerTask));
        }
    }, [worksheet?.hintCreditsPerTask]);

    /**
     * Persists a change to the hint settings.
     *
     * PUT /worksheets replaces the whole row, so every field has to be sent
     * back — omitting hintCreditsPerTask here would silently reset the budget
     * to unlimited whenever the hints switch is flipped.
     */
    async function saveHintSettings(patch: Partial<Worksheet>) {
        if (!worksheet) return;
        const next = { ...worksheet, ...patch };
        try {
            await api.put(`/worksheets/${sheetId}`, {
                id: sheetId,
                name: next.name,
                description: next.description,
                hintsEnabled: next.hintsEnabled,
                hintCreditsPerTask: next.hintCreditsPerTask,
            });
            setWorksheet(next);
        } catch {
            alert(t("worksheet.errorSavingHints"));
        }
    }

    function commitCredits() {
        const parsed = Number(creditsInput);
        if (!Number.isInteger(parsed) || parsed < 1 || parsed > 99) {
            // Reject nonsense by snapping back to the stored value.
            setCreditsInput(String(worksheet?.hintCreditsPerTask ?? 5));
            return;
        }
        if (parsed !== worksheet?.hintCreditsPerTask) {
            saveHintSettings({ hintCreditsPerTask: parsed });
        }
    }

    function startEditing() {
        if (!worksheet) return;
        setEditName(worksheet.name);
        setEditDescription(worksheet.description || "");
        setEditing(true);
    }

    async function handleSaveEdit() {
        if (!worksheet || !sheetId) return;
        setSaving(true);
        try {
            await api.put(`/worksheets/${sheetId}`, {
                id: sheetId,
                name: editName.trim(),
                description: editDescription.trim(),
            });
            setWorksheet({ ...worksheet, name: editName.trim(), description: editDescription.trim() });
            setEditing(false);
        } catch (err: any) {
            alert(err?.response?.data?.error || t("worksheet.errorSaving"));
        } finally {
            setSaving(false);
        }
    }

    async function handleRemoveSequence(sequenceId: string) {
        if (!window.confirm(t("worksheet.removeSequence"))) return;
        try {
            await api.delete(`/worksheets/${sheetId}/sequences/${sequenceId}`);
            const { data } = await api.get(`/worksheets/${sheetId}`);
            setWorksheet(data);
        } catch (err: any) {
            alert(apiErrorMessage(err, "Error removing sequence."));
        }
    }

    // Manual reordering of a worksheet's sequences, teacher-view only (see canEdit below). The
    // drag itself only touches client state; the new order is persisted as a whole so a page
    // reload — or another tab — sees the same order the teacher left it in.
    const dndSensors = useSensors(
        useSensor(PointerSensor, { activationConstraint: { distance: 4 } })
    );

    async function persistSequenceOrder(orderedIds: string[], previous: Worksheet["sequenceDTOS"]) {
        try {
            await api.put(`/worksheets/${sheetId}/sequences/order`, orderedIds);
        } catch (err: any) {
            // Revert: the drag already moved the row optimistically, and a rejected reorder
            // (stale data — a sequence added or removed elsewhere mid-drag) must not leave the
            // page showing an order the server never accepted.
            setWorksheet((current) => (current ? { ...current, sequenceDTOS: previous } : current));
            alert(apiErrorMessage(err, t("worksheet.errorReordering")));
        }
    }

    function handleSequenceDragEnd(event: DragEndEvent) {
        const { active, over } = event;
        if (!worksheet || !over || active.id === over.id) return;

        const previous = worksheet.sequenceDTOS;
        const ids = previous.map((s) => s.id);
        const oldIndex = ids.indexOf(String(active.id));
        const newIndex = ids.indexOf(String(over.id));
        if (oldIndex === -1 || newIndex === -1) return;

        const reordered = arrayMove(previous, oldIndex, newIndex);
        setWorksheet({ ...worksheet, sequenceDTOS: reordered }); // optimistic
        persistSequenceOrder(reordered.map((s) => s.id), previous);
    }


    async function handleCopyLink() {
        try {
            await navigator.clipboard.writeText(window.location.href);
            setCopied(true);
            setTimeout(() => setCopied(false), 1500);
        } catch {
            alert(t("solve.copyFailed"));
        }
    }

    async function handleCopyWorksheet() {
        if (!sheetId) return;
        setCopying(true);
        try {
            const { data } = await api.post<Worksheet>(`/worksheets/${sheetId}/copy`);
            navigate(`/worksheets/${data.id}`, { state: { justCopied: true } });
        } catch (err: any) {
            alert(err?.response?.data?.error || err?.response?.data || t("worksheet.errorSaving"));
        } finally {
            setCopying(false);
        }
    }

    async function handleOpenAssignModal() {
        setShowAssignModal(true);
        setAssignError(null);
        setAssignSuccess(null);
        setLoadingClasses(true);
        try {
            const { data } = await api.get<ClassInfo[]>("/classes");
            setMyClasses(data);
        } catch (err: any) {
            setAssignError(t("classes.errorLoading"));
        } finally {
            setLoadingClasses(false);
        }
    }

    async function handleAssignToClass(classId: string) {
        setAssigningClassId(classId);
        setAssignError(null);
        try {
            await api.post(`/classes/${classId}/worksheets/${sheetId}`);
            setAssignSuccess(t("worksheet.assignSuccess"));
            setTimeout(() => setAssignSuccess(null), 3000);
        } catch (err: any) {
            setAssignError(err?.response?.data?.error || err?.response?.data || t("classDetail.errorAssigning"));
        } finally {
            setAssigningClassId(null);
        }
    }

    // ── Export ──────────────────────────────────────────────
    const [exporting, setExporting] = useState(false);

    async function resolveWorksheetForExport(): Promise<ExportableWorksheet | null> {
        if (!worksheet) return null;
        setExporting(true);
        try {
            const seqPromises = (worksheet.sequenceDTOS ?? []).map((s) =>
                api.get(`/sequence/${s.id}`).then(({ data }): ExportableSequence => ({
                    id: String(data.id),
                    programmingLanguage: String(data.programmingLanguage ?? "unknown"),
                    concepts: Array.isArray(data.concepts) ? data.concepts.map(String) : [],
                    taskDescription: data.taskDescription ?? undefined,
                    tasks: Array.isArray(data.tasks)
                        ? data.tasks.map((t: any) => ({
                            id: String(t.id),
                            statement: String(t.statement ?? ""),
                            solution: String(t.solution ?? ""),
                            tierLevel: Number(t.tierLevel ?? 0),
                            cyclomaticComplexity: Number(t.cyclomaticComplexity ?? 0),
                            programmingLanguage: String(t.programmingLanguage ?? data.programmingLanguage ?? "unknown"),
                            distractors: t.distractors ?? null,
                            distractorPlan: t.distractorPlan ?? null,
                            distractorsEnabled: t.distractorsEnabled !== false,
                        }))
                        : [],
                }))
            );
            const sequences = await Promise.all(seqPromises);
            return {
                id: worksheet.id,
                name: worksheet.name,
                description: worksheet.description,
                sequences,
            };
        } catch (err: any) {
            alert(t("worksheet.errorSaving"));
            return null;
        } finally {
            setExporting(false);
        }
    }

    async function handleExport(format: "json" | "csv" | "pdf-freeform-teacher" | "pdf-freeform-student" | "pdf-puzzle") {
        const ws = await resolveWorksheetForExport();
        if (!ws) return;
        switch (format) {
            case "json": exportWorksheetJSON(ws); break;
            case "csv": exportWorksheetCSV(ws); break;
            case "pdf-freeform-teacher": exportWorksheetPDF(ws, "freeform", true); break;
            case "pdf-freeform-student": exportWorksheetPDF(ws, "freeform", false); break;
            case "pdf-puzzle": exportWorksheetPDF(ws, "puzzle"); break;
        }
    }

    /**
     * Fetches Library search results for the add-sequence dropdown.
     *
     * An empty query still fetches (sorted by recency) so opening the dropdown shows something
     * to browse rather than a blank list. Sequences already on this worksheet are filtered out —
     * re-adding one is either a no-op the backend would reject or, worse, a silent duplicate.
     * Own sequences are bubbled to the top client-side (stable within each group) rather than via
     * a backend sort mode, since that ranking only makes sense for this one picker, not the
     * Library page's general search.
     */
    async function searchSequences(query: string) {
        setLoadingSequenceOptions(true);
        try {
            const params: Record<string, string | number> = { size: 8 };
            const trimmed = query.trim();
            if (trimmed) params.q = trimmed;
            else params.sort = "recent";

            const { data } = await api.get<LibrarySearchResponse>("/library/sequences", { params });
            const existingIds = (worksheet?.sequenceDTOS ?? []).map((s) => s.id);
            setSequenceOptions(prepareSequenceOptions(data.items, existingIds, user?.userId));
        } catch {
            setSequenceOptions([]);
        } finally {
            setLoadingSequenceOptions(false);
        }
    }

    // Debounced live search, matching the Library page's 300ms debounce. Re-fires whenever the
    // modal opens in search mode too (dependencies include showModal/addMode), so opening it
    // shows a starter list without the teacher having to type anything first.
    useEffect(() => {
        if (!showModal || addMode !== "search") return;
        const handle = window.setTimeout(() => {
            searchSequences(sequenceSearch);
        }, 300);
        return () => window.clearTimeout(handle);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [sequenceSearch, addMode, showModal]);

    function resetAddSequenceModal() {
        setSequenceId("");
        setBatchInput("");
        setSequenceSearch("");
        setSelectedSequence(null);
        setSequenceOptions([]);
        setShowSequenceDropdown(false);
        setModalError(null);
    }

    async function handleAddSequence() {
        setModalError(null);

        if (addMode === "batch") {
            // split CSV or newline/space separated values into UUIDs
            const ids = batchInput
                .split(/[\s,;]+/)
                .map((x) => x.trim())
                .filter(Boolean);

            if (ids.length === 0) {
                setModalError("Please enter at least one UUID.");
                return;
            }

            setAdding(true);
            try {
                await api.post(`/worksheets/${sheetId}/sequences`, ids);
                const { data } = await api.get(`/worksheets/${sheetId}`);
                setWorksheet(data);
                setShowModal(false);
                resetAddSequenceModal();
            } catch (err: any) {
                setModalError(apiErrorMessage(err, "Error adding sequences."));
            } finally {
                setAdding(false);
            }
        } else if (addMode === "search") {
            if (!selectedSequence) {
                setModalError(t("worksheet.errorSelectSequence"));
                return;
            }

            setAdding(true);
            try {
                await api.post(`/worksheets/${sheetId}/sequences/${selectedSequence.sequenceId}`);
                const { data } = await api.get(`/worksheets/${sheetId}`);
                setWorksheet(data);
                setShowModal(false);
                resetAddSequenceModal();
            } catch (err: any) {
                setModalError(apiErrorMessage(err, "Error adding sequence."));
            } finally {
                setAdding(false);
            }
        } else {
            if (!sequenceId.trim()) {
                setModalError("Please enter a sequence UUID.");
                return;
            }

            setAdding(true);
            try {
                await api.post(`/worksheets/${sheetId}/sequences/${sequenceId.trim()}`);
                const { data } = await api.get(`/worksheets/${sheetId}`);
                setWorksheet(data);
                setShowModal(false);
                resetAddSequenceModal();
            } catch (err: any) {
                setModalError(apiErrorMessage(err, "Error adding sequence."));
            } finally {
                setAdding(false);
            }
        }
    }

    useEffect(() => {
        api.get(`/worksheets/${sheetId}`)
            .then(({ data }) => setWorksheet(data))
            .catch((err) =>
                // A 404 here means the worksheet is gone or is not shared with this visitor,
                // and the endpoint deliberately does not say which. It also carries no body, so
                // without this the page would show axios's English "Request failed with status
                // code 404" to someone who just opened a link.
                setError(isNotFound(err) ? t("worksheet.notAvailable") : apiErrorMessage(err)))
            .finally(() => setLoading(false));
    }, [sheetId, t]);

    if (loading) return <div className="container py-4">{t("common.loading")}</div>;
    if (error) return <div className="container py-4 text-danger">{error}</div>;
    if (!worksheet) return <div className="container py-4">{t("common.error")}</div>;

    return (
        <div className="container py-4" style={{ maxWidth: 900 }}>
            <div className="mb-3 d-flex gap-2">
                <button className="btn btn-link" onClick={() => navigate(homePath)}>
                    ← {t("common.home")}
                </button>
                {fromClass && (
                    <button
                        className="btn btn-link"
                        onClick={() => navigate(`/classes/${fromClass}`)}
                    >
                        ← {t("worksheet.backToClass")}
                    </button>
                )}
                {marketplaceBack && (
                    <button
                        className="btn btn-link"
                        onClick={() => navigate(marketplaceBack.to)}
                    >
                        ← {t(marketplaceBack.labelKey)}
                    </button>
                )}
            </div>
            {canToggleTeacherView && (
                <div className="d-flex justify-content-end mb-3">
                    <button
                        className={`btn btn-sm ${teacherViewEnabled ? "btn-warning" : "btn-outline-warning"}`}
                        onClick={() => setTeacherViewEnabled(!teacherViewEnabled)}
                    >
                        {teacherViewEnabled ? t("worksheet.hideTeacherMode") : t("worksheet.teacherMode")}
                    </button>
                </div>
            )}

            {teacherViewEnabled && (
                <>
                <div
                    className="alert alert-warning text-center fw-bold mb-4"
                    style={{
                        fontSize: "1.1rem",
                        background:
                            "linear-gradient(90deg, rgba(255,193,7,0.9) 0%, rgba(255,230,150,0.9) 100%)",
                        border: "none",
                        boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
                    }}
                >
                    {t("worksheet.teacherBanner")}
                </div>
                <div className="card card-body mb-3 d-flex flex-row align-items-center gap-3 flex-wrap">
                    <div className="form-check form-switch mb-0">
                        <input
                            className="form-check-input"
                            type="checkbox"
                            id="hintsEnabledToggle"
                            checked={worksheet.hintsEnabled}
                            onChange={() => saveHintSettings({ hintsEnabled: !worksheet.hintsEnabled })}
                        />
                        <label className="form-check-label" htmlFor="hintsEnabledToggle">
                            {worksheet.hintsEnabled ? t("worksheet.hintsEnabled") : t("worksheet.hintsDisabled")}
                        </label>
                    </div>

                    {/* Credit budget. Only meaningful while hints are on at all. */}
                    {worksheet.hintsEnabled && (
                        <div className="d-flex align-items-center gap-3 flex-wrap">
                            <div className="form-check form-switch mb-0">
                                <input
                                    className="form-check-input"
                                    type="checkbox"
                                    id="unlimitedCreditsToggle"
                                    checked={worksheet.hintCreditsPerTask === null}
                                    onChange={() => {
                                        // Leaving "unlimited" needs a concrete number; fall back to
                                        // whatever is in the input, or the documented default of 5.
                                        const next = worksheet.hintCreditsPerTask === null
                                            ? (Number(creditsInput) > 0 ? Number(creditsInput) : 5)
                                            : null;
                                        saveHintSettings({ hintCreditsPerTask: next });
                                    }}
                                />
                                <label className="form-check-label" htmlFor="unlimitedCreditsToggle">
                                    {t("worksheet.unlimitedHints")}
                                </label>
                            </div>

                            {worksheet.hintCreditsPerTask !== null && (
                                <div className="d-flex align-items-center gap-2">
                                    <label className="form-label mb-0" htmlFor="hintCreditsInput">
                                        {t("worksheet.creditsPerTask")}
                                    </label>
                                    <input
                                        id="hintCreditsInput"
                                        type="number"
                                        min={1}
                                        max={99}
                                        className="form-control form-control-sm"
                                        style={{ width: "5rem" }}
                                        value={creditsInput}
                                        onChange={(e) => setCreditsInput(e.target.value)}
                                        // Save on commit rather than per keystroke, so a half-typed
                                        // number never reaches the server.
                                        onBlur={() => commitCredits()}
                                        onKeyDown={(e) => {
                                            if (e.key === "Enter") (e.target as HTMLInputElement).blur();
                                        }}
                                    />
                                    <small className="text-muted">{t("worksheet.creditsHelp")}</small>
                                </div>
                            )}
                        </div>
                    )}

                    <div className="dropdown ms-auto">
                        <button
                            className="btn btn-outline-secondary btn-sm dropdown-toggle d-flex align-items-center gap-1"
                            type="button"
                            data-bs-toggle="dropdown"
                            disabled={exporting || !worksheet.sequenceDTOS?.length}
                        >
                            {exporting ? (
                                <><span className="spinner-border spinner-border-sm" /> {t("worksheet.exporting")}</>
                            ) : (
                                <>{t("worksheet.export")}</>
                            )}
                        </button>
                        <ul className="dropdown-menu">
                            <li><button className="dropdown-item" onClick={() => handleExport("json")}>{t("sequence.exportJSON")}</button></li>
                            <li><button className="dropdown-item" onClick={() => handleExport("csv")}>{t("sequence.exportCSV")}</button></li>
                            <li><hr className="dropdown-divider" /></li>
                            <li><button className="dropdown-item" onClick={() => handleExport("pdf-freeform-teacher")}>{t("sequence.exportPdfTeacher")}</button></li>
                            <li><button className="dropdown-item" onClick={() => handleExport("pdf-freeform-student")}>{t("sequence.exportPdfStudent")}</button></li>
                            <li><button className="dropdown-item" onClick={() => handleExport("pdf-puzzle")}>{t("sequence.exportPdfPuzzle")}</button></li>
                        </ul>
                    </div>
                </div>
                </>
            )}
            {editing ? (
                <div className="mb-3">
                    <input
                        type="text"
                        className="form-control form-control-lg mb-2"
                        value={editName}
                        onChange={(e) => setEditName(e.target.value)}
                        placeholder={t("home.worksheetName")}
                    />
                    <textarea
                        className="form-control mb-2"
                        value={editDescription}
                        onChange={(e) => setEditDescription(e.target.value)}
                        placeholder={t("home.worksheetDesc")}
                        rows={2}
                    />
                    <div className="d-flex gap-2">
                        <button className="btn btn-success btn-sm" onClick={handleSaveEdit} disabled={saving || !editName.trim()}>
                            {saving ? t("worksheet.saving") : t("worksheet.save")}
                        </button>
                        <button className="btn btn-outline-secondary btn-sm" onClick={() => setEditing(false)} disabled={saving}>
                            {t("worksheet.cancel")}
                        </button>
                    </div>
                </div>
            ) : (
                <div className="d-flex align-items-start gap-2">
                    <div>
                        <h1>{worksheet.name}</h1>
                        <p className="text-muted">{worksheet.description}</p>
                    </div>
                    {canEdit && (
                        <button className="btn btn-outline-secondary btn-sm mt-2" onClick={startEditing} title={t("worksheet.editName")}>
                            ✏️ {t("common.edit")}
                        </button>
                    )}
                </div>
            )}

            {/* Copy / Assign buttons for foreign worksheets */}
            {justCopied && (
                <div className="alert alert-success">
                    {t("worksheet.worksheetCopied")}
                </div>
            )}

            <div className="d-flex flex-wrap gap-2 mb-4">
                {canCopy && (
                    <button
                        className="btn btn-outline-primary btn-sm d-flex align-items-center gap-1"
                        onClick={handleCopyWorksheet}
                        disabled={copying}
                        title={t("worksheet.copy")}
                    >
                        {copying ? (
                            <>
                                <span className="spinner-border spinner-border-sm" />
                                {t("worksheet.copying")}
                            </>
                        ) : (
                            <>
                                <i className="bi bi-copy"></i>
                                {t("worksheet.copy")}
                            </>
                        )}
                    </button>
                )}

                {canAssign && (
                    <button
                        className="btn btn-outline-success btn-sm d-flex align-items-center gap-1"
                        onClick={handleOpenAssignModal}
                        title={t("worksheet.assign")}
                    >
                        <i className="bi bi-people"></i>
                        {t("worksheet.assign")}
                    </button>
                )}
            </div>

            {canSetVisibility && (
                <VisibilityToggle
                    value={effectiveVisibility(worksheet)}
                    onChange={handleVisibilityChange}
                />
            )}

            <h3 className="mt-4">{t("worksheet.sequences")}</h3>
            <div className="d-flex justify-content-between align-items-center gap-2 mb-3 flex-wrap">
                <div>
                    {teacherViewEnabled && (
                        <div className="d-flex align-items-center gap-2">
                            <span className="small text-muted fw-semibold">
                                {t("worksheet.detailLevelHeading")}
                            </span>
                            <div
                                className="btn-group btn-group-sm"
                                role="group"
                                aria-label={t("worksheet.detailLevelLabel") as string}
                            >
                                <button
                                    type="button"
                                    className={`btn ${detailLevel === "none" ? "btn-secondary" : "btn-outline-secondary"}`}
                                    onClick={() => setDetailLevel("none")}
                                    title={t("worksheet.detailLevelLabel") as string}
                                >
                                    {t("worksheet.detailLevelNone")}
                                </button>
                                <button
                                    type="button"
                                    className={`btn ${detailLevel === "some" ? "btn-secondary" : "btn-outline-secondary"}`}
                                    onClick={() => setDetailLevel("some")}
                                    title={t("worksheet.detailLevelLabel") as string}
                                >
                                    {t("worksheet.detailLevelSome")}
                                </button>
                                <button
                                    type="button"
                                    className={`btn ${detailLevel === "all" ? "btn-secondary" : "btn-outline-secondary"}`}
                                    onClick={() => setDetailLevel("all")}
                                    title={t("worksheet.detailLevelLabel") as string}
                                >
                                    {t("worksheet.detailLevelAll")}
                                </button>
                            </div>
                        </div>
                    )}
                </div>

                <div className="d-flex align-items-center gap-2">
                    <button
                        className={`btn btn-sm d-flex align-items-center gap-1 ${
                            copied ? "btn-success" : "btn-outline-secondary"
                        }`}
                        onClick={handleCopyLink}
                        title={t("worksheet.copyLinkTitle")}
                    >
                        <i className="bi bi-link-45deg"></i>
                        {copied ? t("worksheet.copied") : t("worksheet.shareWorksheet")}
                    </button>

                    {teacherViewEnabled && (
                        <button
                            className="btn btn-outline-secondary btn-sm"
                            onClick={() => setShowModal(true)}
                        >
                            + {t("worksheet.addSequence")}
                        </button>
                    )}
                </div>
            </div>
            {worksheet.sequenceDTOS?.length ? (
                <DndContext
                    sensors={dndSensors}
                    collisionDetection={closestCenter}
                    onDragEnd={handleSequenceDragEnd}
                >
                    <SortableContext
                        items={worksheet.sequenceDTOS.map((s) => s.id)}
                        strategy={verticalListSortingStrategy}
                    >
                        <ListGroup>
                            {worksheet.sequenceDTOS.map((s, idx) => (
                                <SortableSequenceRow
                                    key={s.id}
                                    id={s.id}
                                    draggable={!!canEdit}
                                    dragHandleLabel={t("worksheet.dragToReorder")}
                                    multiline={teacherViewEnabled && detailLevel !== "none"}
                                    title={teacherViewEnabled ? t("worksheet.sequenceIdTooltip", { id: s.id }) : undefined}
                                >
                                    <div
                                        style={{ cursor: "pointer" }}
                                        onClick={() =>
                                            navigate(`/sequence/${s.id}?fromWorksheet=${worksheet.id}`, {
                                                // The worksheet also rides in the state, and fromClass
                                                // with it, so the class link survives a detour into a
                                                // sequence. The query parameter is what survives F5.
                                                state: { fromWorksheet: worksheet.id, fromClass },
                                            })
                                        }
                                    >
                                        {s.title?.trim() || t("worksheet.sequenceLabel", { index: idx + 1 })}
                                        {/* Marks material the reader did not make. Without it the list
                                            is identical before and after forking a sequence, which
                                            makes the fork look like it did nothing. Admins are excluded:
                                            SequenceCuratorPolicy.isOwnerOrAdmin treats them as the owner
                                            of everything for editing purposes (they can edit any
                                            sequence in place, no fork needed), so a raw ownerId
                                            mismatch — including the null ownerId on unowned seed
                                            material — would mislabel material they already fully
                                            control as "not yours". */}
                                        {teacherViewEnabled && !isAdmin && s.ownerId !== user?.userId && (
                                            <span className="badge bg-secondary-subtle text-secondary-emphasis ms-2">
                                                {t("worksheet.foreignSequence")}
                                            </span>
                                        )}
                                        {teacherViewEnabled && (
                                            <small className="text-muted ms-2">
                                                ({s.id ? s.id.slice(0, 8) : "?"}...)
                                            </small>
                                        )}

                                        {/* Detail levels "some"/"all": going by number and UUID alone
                                            told a teacher nothing about which sequence was which — see
                                            useSequenceDetails for what's fetched at each level. */}
                                        {teacherViewEnabled && detailLevel !== "none" && (
                                            sequenceDetails[s.id] ? (
                                                <div className="mt-1">
                                                    <div className="d-flex flex-wrap gap-1 align-items-center mb-1">
                                                        <span className="badge text-bg-primary text-uppercase">
                                                            {sequenceDetails[s.id].programmingLanguage}
                                                        </span>
                                                        {detailLevel === "all" && (
                                                            <span className="badge text-bg-light text-muted">
                                                                {t("worksheet.tierCount", {
                                                                    count: sequenceDetails[s.id].tierCount,
                                                                })}
                                                            </span>
                                                        )}
                                                    </div>
                                                    {sequenceDetails[s.id].taskDescription && (
                                                        <p
                                                            className="small text-muted mb-1"
                                                            style={{ maxWidth: 560 }}
                                                        >
                                                            {sequenceDetails[s.id].taskDescription}
                                                        </p>
                                                    )}
                                                    {detailLevel === "all" && (
                                                        <div className="d-flex flex-wrap gap-1 align-items-center">
                                                            {sequenceDetails[s.id].concepts.map((c) => (
                                                                <span
                                                                    key={c}
                                                                    className="badge bg-secondary-subtle text-secondary-emphasis"
                                                                >
                                                                    {c}
                                                                </span>
                                                            ))}
                                                            {(sequenceTagsById[s.id] ?? []).map((tag) => (
                                                                <TagChip key={`${tag.tagName}-${tag.source}`} tag={tag} />
                                                            ))}
                                                        </div>
                                                    )}
                                                </div>
                                            ) : (
                                                <div className="mt-1">
                                                    <span className="spinner-border spinner-border-sm text-secondary" />
                                                </div>
                                            )
                                        )}
                                    </div>

                                    {teacherViewEnabled && (
                                        <button
                                            className="btn btn-sm btn-outline-danger"
                                            title={t("worksheet.removeSequence")}
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                handleRemoveSequence(s.id);
                                            }}
                                        >
                                            <i className="bi bi-trash"></i>
                                        </button>
                                    )}
                                </SortableSequenceRow>
                            ))}
                        </ListGroup>
                    </SortableContext>
                </DndContext>
            ) : (
                <div className="alert alert-info mt-3">
                    {t("worksheet.noSequences")}
                </div>
            )}
            {/* ADD SEQUENCE MODAL */}
            {showModal && (
                <>
                    <div className="modal fade show" style={{ display: "block" }} tabIndex={-1}>
                        <div className="modal-dialog modal-dialog-centered">
                            <div className="modal-content rounded-4 shadow">
                                <div className="modal-header">
                                    <h5 className="modal-title">{t("worksheet.addSequenceTitle")}</h5>
                                    <button
                                        type="button"
                                        className="btn-close"
                                        onClick={() => {
                                            setShowModal(false);
                                            resetAddSequenceModal();
                                        }}
                                    />
                                </div>
                                <div className="modal-body">
                                    <div className="mb-3">
                                        <div className="btn-group btn-group-sm mb-2" role="group">
                                            <button
                                                type="button"
                                                className={`btn ${addMode === "search" ? "btn-secondary" : "btn-outline-secondary"}`}
                                                onClick={() => setAddMode("search")}
                                                disabled={adding}
                                            >
                                                {t("worksheet.searchInsert")}
                                            </button>
                                            <button
                                                type="button"
                                                className={`btn ${addMode === "single" ? "btn-secondary" : "btn-outline-secondary"}`}
                                                onClick={() => setAddMode("single")}
                                                disabled={adding}
                                            >
                                                {t("worksheet.singleInsert")}
                                            </button>
                                            <button
                                                type="button"
                                                className={`btn ${addMode === "batch" ? "btn-secondary" : "btn-outline-secondary"}`}
                                                onClick={() => setAddMode("batch")}
                                                disabled={adding}
                                            >
                                                {t("worksheet.batchInsert")}
                                            </button>
                                        </div>

                                        {addMode === "search" ? (
                                            <div className="position-relative">
                                                <label className="form-label fw-semibold mb-1 d-block">
                                                    {t("worksheet.searchSequenceLabel")}
                                                </label>
                                                <input
                                                    type="text"
                                                    className="form-control"
                                                    value={sequenceSearch}
                                                    onChange={(e) => {
                                                        setSequenceSearch(e.target.value);
                                                        setSelectedSequence(null);
                                                        setShowSequenceDropdown(true);
                                                    }}
                                                    onFocus={() => setShowSequenceDropdown(true)}
                                                    onBlur={() => setShowSequenceDropdown(false)}
                                                    placeholder={t("worksheet.searchSequencePlaceholder")}
                                                    autoComplete="off"
                                                    disabled={adding}
                                                />
                                                {showSequenceDropdown && (
                                                    <ul
                                                        className="list-group position-absolute w-100 shadow-sm"
                                                        style={{ zIndex: 1000, maxHeight: 280, overflowY: "auto" }}
                                                    >
                                                        {loadingSequenceOptions ? (
                                                            <li className="list-group-item text-muted small">
                                                                {t("worksheet.loadingSequences")}
                                                            </li>
                                                        ) : sequenceOptions.length === 0 ? (
                                                            <li className="list-group-item text-muted small">
                                                                {t("worksheet.noMatchingSequences")}
                                                            </li>
                                                        ) : (
                                                            sequenceOptions.map((seq) => {
                                                                const title = sequenceOptionTitle(seq, t("worksheet.untitledSequence"));
                                                                return (
                                                                <li
                                                                    key={seq.sequenceId}
                                                                    className="list-group-item list-group-item-action"
                                                                    style={{ cursor: "pointer" }}
                                                                    onMouseDown={(e) => e.preventDefault()}
                                                                    onClick={() => {
                                                                        setSelectedSequence(seq);
                                                                        setSequenceSearch(title);
                                                                        setShowSequenceDropdown(false);
                                                                    }}
                                                                >
                                                                    <div className="d-flex justify-content-between align-items-start gap-2">
                                                                        <div>
                                                                            <div className="fw-semibold">{title}</div>
                                                                            {/* Library search shows the ts_headline match snippet under
                                                                                the title, not the semanticSummary paragraph (deliberately
                                                                                dropped there — see LibraryPage.tsx) — mirrored here. */}
                                                                            {seq.highlight && (
                                                                                <small
                                                                                    className="text-muted d-block"
                                                                                    dangerouslySetInnerHTML={{ __html: renderHighlight(seq.highlight) }}
                                                                                />
                                                                            )}
                                                                            <small className="text-muted">
                                                                                {seq.programmingLanguage} &middot;{" "}
                                                                                {t("worksheet.tierCount", { count: seq.tierCount })}
                                                                            </small>
                                                                        </div>
                                                                        {user && seq.ownerId === user.userId && (
                                                                            <span className="badge bg-primary-subtle text-primary flex-shrink-0">
                                                                                {t("worksheet.ownSequence")}
                                                                            </span>
                                                                        )}
                                                                    </div>
                                                                </li>
                                                                );
                                                            })
                                                        )}
                                                    </ul>
                                                )}
                                            </div>
                                        ) : addMode === "batch" ? (
                                            <>
                                                <label className="form-label fw-semibold mb-1 d-block">
                                                    {t("worksheet.sequenceUuids")}
                                                </label>
                                                <textarea
                                                    className="form-control"
                                                    rows={3}
                                                    value={batchInput}
                                                    onChange={(e) => setBatchInput(e.target.value)}
                                                    placeholder={t("worksheet.pasteUuids")}
                                                    disabled={adding}
                                                />
                                            </>
                                        ) : (
                                            <>
                                                <label className="form-label fw-semibold mb-1 d-block">
                                                    {t("worksheet.sequenceUuid")}
                                                </label>
                                                <input
                                                    type="text"
                                                    className="form-control"
                                                    value={sequenceId}
                                                    onChange={(e) => setSequenceId(e.target.value)}
                                                    placeholder={t("worksheet.enterUuid")}
                                                    disabled={adding}
                                                />
                                            </>
                                        )}
                                    </div>
                                    {modalError && <div className="alert alert-danger py-2">{modalError}</div>}
                                </div>
                                <div className="modal-footer">
                                    <button
                                        type="button"
                                        className="btn btn-secondary"
                                        onClick={() => {
                                            setShowModal(false);
                                            resetAddSequenceModal();
                                        }}
                                        disabled={adding}
                                    >
                                        {t("common.cancel")}
                                    </button>
                                    <button
                                        type="button"
                                        className="btn btn-primary"
                                        onClick={handleAddSequence}
                                        disabled={adding || (addMode === "search" && !selectedSequence)}
                                    >
                                        {adding ? (
                                            <>
                                                <span className="spinner-border spinner-border-sm me-2" />
                                                {t("worksheet.linking")}
                                            </>
                                        ) : (
                                            t("worksheet.linkSequence")
                                        )}
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div className="modal-backdrop fade show"></div>
                </>
            )}

            {/* ASSIGN TO CLASS MODAL */}
            {showAssignModal && (
                <>
                    <div className="modal fade show" style={{ display: "block" }} tabIndex={-1}>
                        <div className="modal-dialog modal-dialog-centered">
                            <div className="modal-content rounded-4 shadow">
                                <div className="modal-header">
                                    <h5 className="modal-title">{t("worksheet.assignTitle")}</h5>
                                    <button type="button" className="btn-close" onClick={() => setShowAssignModal(false)} />
                                </div>
                                <div className="modal-body">
                                    {assignError && <div className="alert alert-danger py-2">{assignError}</div>}
                                    {assignSuccess && <div className="alert alert-success py-2">{assignSuccess}</div>}

                                    {loadingClasses ? (
                                        <div className="text-center py-3">
                                            <span className="spinner-border spinner-border-sm me-2" />
                                            {t("worksheet.loadingClasses")}
                                        </div>
                                    ) : myClasses.length === 0 ? (
                                        <div className="alert alert-info mb-0">
                                            {t("worksheet.noClasses")}
                                            <br />
                                            <button
                                                className="btn btn-sm btn-outline-primary mt-2"
                                                onClick={() => {
                                                    setShowAssignModal(false);
                                                    navigate("/classes");
                                                }}
                                            >
                                                {t("worksheet.manageClasses")}
                                            </button>
                                        </div>
                                    ) : (
                                        <div className="list-group">
                                            {myClasses.map((cls) => (
                                                <div
                                                    key={cls.id}
                                                    className="list-group-item d-flex justify-content-between align-items-center"
                                                >
                                                    <div>
                                                        <strong>{cls.name}</strong>
                                                        {cls.description && (
                                                            <small className="text-muted d-block">{cls.description}</small>
                                                        )}
                                                    </div>
                                                    <button
                                                        className="btn btn-sm btn-outline-success"
                                                        onClick={() => handleAssignToClass(cls.id)}
                                                        disabled={assigningClassId === cls.id}
                                                    >
                                                        {assigningClassId === cls.id ? (
                                                            <span className="spinner-border spinner-border-sm" />
                                                        ) : (
                                                            t("worksheet.assign")
                                                        )}
                                                    </button>
                                                </div>
                                            ))}
                                        </div>
                                    )}
                                </div>
                                <div className="modal-footer">
                                    <button type="button" className="btn btn-secondary" onClick={() => setShowAssignModal(false)}>
                                        {t("worksheet.close")}
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div className="modal-backdrop fade show"></div>
                </>
            )}

            {teacherViewEnabled && tipsVisible && (
                <div
                    style={{
                        position: "fixed",
                        right: "clamp(0.5rem, 2vw, 2rem)",
                        bottom: "clamp(0.5rem, 2vw, 2rem)",
                        backgroundColor: "#fffbea",
                        border: "1px solid #ffe08a",
                        borderRadius: "0.75rem",
                        boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
                        padding: "1rem 1.25rem",
                        maxWidth: "min(400px, calc(100vw - 1rem))",
                        zIndex: 1000,
                    }}
                >
                    <div className="d-flex justify-content-between align-items-start gap-2 mb-2">
                        <h6 className="fw-bold mb-0">{t("worksheet.tips")}</h6>
                        <button
                            type="button"
                            className="btn-close"
                            aria-label={t("common.close")}
                            style={{ flexShrink: 0 }}
                            onClick={() => setTipsVisible(false)}
                        />
                    </div>
                    <ul className="mb-0 small text-muted" style={{ lineHeight: 1.5 }}>
                        <li dangerouslySetInnerHTML={{ __html: t("worksheet.tip1") }} />
                        <li dangerouslySetInnerHTML={{ __html: t("worksheet.tip2") }} />
                        <li>{t("worksheet.tip3")}</li>
                        <li>{t("worksheet.tip4")}</li>
                        <li dangerouslySetInnerHTML={{ __html: t("worksheet.tip5") }} />
                    </ul>
                </div>
            )}
        </div>
    );
}
