import { useEffect, useState } from "react";
import { useParams, useNavigate, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import type { TieredTaskSequence, Task } from "../types/models.ts";
import { api, apiErrorMessage } from "../lib/api.ts";
import { usePageTitle } from "../hooks/usePageTitle.ts";
import { useHomePath } from "../hooks/useHomePath.ts";
import {PersistentLogger} from "../PersistentLogger.ts";
import { useLocation } from "react-router-dom";
import { useSequenceAccess } from "../hooks/useSequenceAccess.ts";
import { useAuth } from "../context/AuthContext.tsx";
import TaskEditModal from "../components/TaskEditModal.tsx";
import TaskTitleEditor from "../components/TaskTitleEditor.tsx";
import TaskDescriptionEditor from "../components/TaskDescriptionEditor.tsx";
import SequenceTitleEditor from "../components/SequenceTitleEditor.tsx";
import ConceptPicker from "../components/ConceptPicker.tsx";
import { useConceptCatalogue } from "../hooks/useConceptCatalogue.ts";
import { useEventTracker } from "../hooks/useEventTracker.ts";
import { useProgressiveTags } from "../hooks/useProgressiveTags.ts";
import TagChip from "../components/TagChip.tsx";
import TagChipSkeleton from "../components/TagChipSkeleton.tsx";
import TagEditor from "../components/TagEditor.tsx";
import SimilarSequencesPanel from "../components/SimilarSequencesPanel.tsx";
import AddToWorksheetModal from "../components/AddToWorksheetModal.tsx";
import {
    exportSequenceJSON,
    exportSequenceCSV,
    exportSequencePDF,
} from "../lib/export.ts";
import type { ExportableSequence } from "../lib/export.ts";
type LoadState = "idle" | "loading" | "ready" | "notfound" | "error";

export default function SequencePage() {
    const { t } = useTranslation();
    usePageTitle(t("sequence.pageTitle"));
    const homePath = useHomePath();
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();

    const location = useLocation();

    // Which worksheet the teacher came from. Router state was enough while this only decided
    // whether to show a "back to worksheet" button; it now also decides whether the teacher
    // view opens, and router state does not survive a reload — pressing F5 made the teacher
    // view and the fork offer disappear with no explanation. The query string survives a
    // reload and a shared link, and still says nothing a direct visitor could abuse: the
    // server checks the claim before granting anything.
    const [searchParams] = useSearchParams();
    const fromWorksheet =
        searchParams.get("fromWorksheet")
        ?? (location.state as { fromWorksheet?: string })?.fromWorksheet
        ?? null;
    // Not rendered here — only forwarded, so the worksheet still knows its class.
    const fromClass = (location.state as { fromClass?: string })?.fromClass ?? null;

    // Clean up stale localStorage entry from previous implementation
    useEffect(() => { localStorage.removeItem("lastFromWorksheet"); }, []);



    const { user, isLoggedIn, isAdmin, isTeacher } = useAuth();
    const [state, setState] = useState<LoadState>("idle");
    const [seq, setSeq] = useState<TieredTaskSequence | null>(null);
    const [errorMsg, setErrorMsg] = useState("");
    const [teacherViewEnabled, setTeacherViewEnabled] = useState(false);

    // Editing the sequence's concepts. The catalogue is only fetched to power
    // the picker's suggestions; a failure there costs autocomplete, nothing more.
    const conceptCatalogue = useConceptCatalogue();
    const [editingConcepts, setEditingConcepts] = useState(false);
    const [savingConcepts, setSavingConcepts] = useState(false);
    const [conceptError, setConceptError] = useState("");
    const [copied, setCopied] = useState(false);
    const [uuidCopied, setUuidCopied] = useState(false);
    const [editingTask, setEditingTask] = useState<Task | null>(null);
    const [editIsFirstEdit, setEditIsFirstEdit] = useState(true);
    const { logEvent } = useEventTracker();

    // AP15b: Distractor generation state
    const [generatingDistractors, setGeneratingDistractors] = useState<string | null>(null); // taskId or "batch"

    // Sequence rating modal state
    const [showRatingModal, setShowRatingModal] = useState(false);
    const [enjoymentRating, setEnjoymentRating] = useState(3);
    const [difficultyRating, setDifficultyRating] = useState(3);
    const [ratingSubmitted, setRatingSubmitted] = useState(false);

    // AP19 Teil A (A13): Progressive tag fetch. The /api/sequences/{id} endpoint doesn't include
    // AP19 tags, so we let the hook do a one-shot fetch on mount. No `createdAt` is passed (the
    // domain type doesn't track it), which effectively disables the poll loop — acceptable here
    // since the teacher is looking at a single sequence and can refresh if the LLM is late.
    const { tags: sequenceTags, isWaitingForLlm, setTags } = useProgressiveTags(id, undefined, undefined);
    const [editingTags, setEditingTags] = useState(false);

    // Role-based teacher view: show toggle for owners and admins
    const isOwnerOrAdmin = isLoggedIn && seq && user && (seq.ownerId === user.userId || isAdmin);

    // A teacher who put this sequence into a worksheet of theirs may look at it too, even
    // though they do not own it. Asked of the server rather than decided here, because it
    // depends on a worksheet link the client cannot see. See useSequenceAccess.
    const access = useSequenceAccess(id, fromWorksheet);

    const canToggleTeacherView = !!isOwnerOrAdmin || access.canInspect;

    // Writing stays with the owner. A curator's edit would rewrite the sequence inside every
    // other worksheet using it, so they are offered a fork instead of edit controls.
    const canEdit = !!isOwnerOrAdmin || access.canEditInPlace;

    // Auto-enable teacher view for anyone entitled to it, so the material a teacher curated
    // is visible without hunting for a toggle.
    useEffect(() => {
        setTeacherViewEnabled(canToggleTeacherView);
    }, [canToggleTeacherView]);

    const isTemplate = id?.toLowerCase().includes("template");

    function toClientSequence(raw: any): TieredTaskSequence {
        const seqId = String(raw?.id);
        const seqLang = String(raw?.programmingLanguage ?? "unknown");

        const tasks: Task[] = Array.isArray(raw?.tasks)
            ? raw.tasks.map((tk: any) => ({
                id: String(tk.id),
                statement: String(tk.statement ?? ""),
                solution: String(tk.solution ?? ""),
                tierLevel: Number(tk.tierLevel ?? 0),
                title: tk.title ?? null,
                cyclomaticComplexity: Number(tk.cyclomaticComplexity ?? 0),
                sequenceId: String(tk.sequenceId ?? seqId),
                programmingLanguage: String(tk.programmingLanguage ?? seqLang),
                distractors: tk.distractors ?? null,
                distractorPlan: tk.distractorPlan ?? null,
                distractorsEnabled: tk.distractorsEnabled !== false,
                contextBefore: tk.contextBefore ?? null,
                contextAfter: tk.contextAfter ?? null,
                splitPlan: tk.splitPlan ?? null, // AP31 — needed so the edit modal can reopen with existing splits
            }))
            : [];

        return {
            id: seqId,
            title: raw?.title ?? null,
            programmingLanguage: seqLang,
            concepts: Array.isArray(raw?.concepts) ? raw.concepts.map(String) : [],
            tasks: tasks.sort((a, b) => a.tierLevel - b.tierLevel),
            codeSnippet: raw?.codeSnippet ?? undefined,
            taskDescription: raw?.taskDescription ?? undefined,
            ownerId: raw?.ownerId ?? null,
            constraints: raw?.constraints ?? null,
        };
    }

    const [forking, setForking] = useState(false);

    // Add-to-worksheet: the teacher picks which of their own worksheets this sequence should be
    // linked into. The dialog itself is shared with the Library page — see AddToWorksheetModal.
    const canAddToWorksheet = isLoggedIn && (isTeacher || isAdmin);
    const [showAddToWorksheetModal, setShowAddToWorksheetModal] = useState(false);

    /**
     * Takes a private copy and moves the worksheet onto it, then navigates there.
     *
     * <p>Navigating is the point rather than a nicety: after the fork the sequence in the URL is
     * no longer the one the teacher's worksheet contains, so staying put would leave them
     * editing a page whose changes their worksheet no longer sees.
     */
    async function handleFork() {
        if (!fromWorksheet || !id) return;
        setForking(true);
        try {
            const { data } = await api.post<{ sequenceId: string }>(
                `/worksheets/${fromWorksheet}/sequences/${id}/fork`);
            navigate(`/sequence/${data.sequenceId}?fromWorksheet=${fromWorksheet}`,
                { state: { fromWorksheet }, replace: true });
        } catch (err) {
            alert(apiErrorMessage(err, t("sequence.forkFailed")));
        } finally {
            setForking(false);
        }
    }

    /**
     * Each add or remove writes the whole set, matching how TagEditor saves a
     * tag change immediately — an explicit Save button on a two-field row is
     * more ceremony than the edit deserves. The server answers with the stored
     * names, which may differ in case from what was typed, so its reply wins
     * over the optimistic value.
     */
    async function saveConcepts(next: string[]) {
        if (!seq) return;
        const previous = seq.concepts;
        setSeq({ ...seq, concepts: next });   // optimistic, so chips react at once
        setSavingConcepts(true);
        setConceptError("");
        try {
            const res = await api.put<string[]>(`/sequence/${seq.id}/concepts`, { concepts: next });
            setSeq((current) => (current ? { ...current, concepts: res.data } : current));
        } catch (e) {
            setSeq((current) => (current ? { ...current, concepts: previous } : current));
            setConceptError(apiErrorMessage(e, t("sequence.conceptsSaveError")));
        } finally {
            setSavingConcepts(false);
        }
    }

    async function handleOpenEdit(task: Task) {
        try {
            const res = await api.get(`/tasks/${task.id}/has-original`);
            setEditIsFirstEdit(!res.data.hasOriginal);
        } catch {
            setEditIsFirstEdit(true);
        }
        setEditingTask(task);
    }

    function handleEditSave(_updated: { statement: string; solution: string; distractors?: string | null }) {
        setEditingTask(null);
        // Reload the sequence data
        if (!id) return;
        api.get(`/sequence/${id}`)
            .then(({ data }) => {
                const mapped = toClientSequence(data);
                setSeq(mapped);
            })
            .catch(() => {});
    }

    function handleSubmitRating() {
        if (!seq) return;
        const firstTaskId = seq.tasks[0]?.id || "";
        logEvent(
            firstTaskId,
            "sequence_rating",
            { enjoyment: enjoymentRating, difficulty: difficultyRating },
            seq.id
        );
        logEvent(
            firstTaskId,
            "sequence_completed",
            {
                totalTasks: seq.tasks.length,
                enjoymentRating,
                difficultyRating,
            },
            seq.id
        );
        setRatingSubmitted(true);
        setTimeout(() => setShowRatingModal(false), 1500);
    }

    async function handleCopyUuid() {
        if (!seq) return;
        try {
            await navigator.clipboard.writeText(seq.id);
            setUuidCopied(true);
            setTimeout(() => setUuidCopied(false), 1500);
        } catch {
            alert(t("solve.copyFailed"));
        }
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

    function toExportableSequence(): ExportableSequence | null {
        if (!seq) return null;
        return {
            id: seq.id,
            programmingLanguage: seq.programmingLanguage,
            concepts: seq.concepts,
            taskDescription: seq.taskDescription,
            tasks: seq.tasks,
        };
    }

    function handleExportSequence(format: "json" | "csv" | "pdf-freeform-teacher" | "pdf-freeform-student" | "pdf-puzzle") {
        const exportSeq = toExportableSequence();
        if (!exportSeq) return;
        switch (format) {
            case "json": exportSequenceJSON(exportSeq); break;
            case "csv": exportSequenceCSV(exportSeq); break;
            case "pdf-freeform-teacher": exportSequencePDF(exportSeq, "freeform", true); break;
            case "pdf-freeform-student": exportSequencePDF(exportSeq, "freeform", false); break;
            case "pdf-puzzle": exportSequencePDF(exportSeq, "puzzle"); break;
        }
    }
    useEffect(() => {
        PersistentLogger.logWithContext("SequencePage", "info", "Mounted SequencePage with id:", id, "isTemplate:", isTemplate);

        if (!id) {
            PersistentLogger.logWithContext("SequencePage", "warn", "No id provided — aborting fetch");
            return;
        }

        setState("loading");
        setErrorMsg("");

        if (isTemplate) {
            PersistentLogger.logWithContext("SequencePage", "info", "Loading local templateSequence");
            (async () => {
                const { TEMPLATE_SEQUENCE } = await import("../dev/templateSequence.ts");
                PersistentLogger.logWithContext("SequencePage", "info", "Loaded template sequence:", JSON.stringify(TEMPLATE_SEQUENCE));
                setSeq(TEMPLATE_SEQUENCE);
                setState("ready");
            })().catch((e) => {
                PersistentLogger.logWithContext("SequencePage", "error", "Template load failed:", e?.message);
                setState("error");
                setErrorMsg(String(e?.message ?? e));
            });
            return;
        }

        const ac = new AbortController();
        PersistentLogger.logWithContext("SequencePage", "info", "Fetching sequence from API:", `/sequence/${id}`);

        api.get(`/sequence/${id}`, { signal: ac.signal })
            .then(({ data }) => {
                PersistentLogger.logWithContext("SequencePage", "info", "Sequence API success:", JSON.stringify(data));
                const mapped = toClientSequence(data);
                PersistentLogger.logWithContext("SequencePage", "info", "Mapped sequence object:", JSON.stringify(mapped));

                setSeq(mapped);
                setState("ready");

                // ✅ write enriched objects to localStorage here (scoped to `mapped`)
                try {
                    localStorage.setItem(`sequence:${mapped.id}`, JSON.stringify(mapped));
                    for (const tk of mapped.tasks) {
                        localStorage.setItem(`task:${tk.id}`, JSON.stringify(tk));
                        localStorage.setItem(`task->sequence:${tk.id}`, mapped.id);
                    }
                } catch (e) {
                    PersistentLogger.logWithContext(
                        "SequencePage",
                        "warn",
                        "Failed writing enriched sequence/tasks to LS:",
                        String(e)
                    );
                }
            })
            .catch((err: any) => {
                if (ac.signal.aborted) {
                    PersistentLogger.logWithContext("SequencePage", "warn", "Fetch aborted for sequence:", id);
                    return;
                }
                const msg = err?.response?.data?.message || err?.message || "Error during API-Request";
                PersistentLogger.logWithContext("SequencePage", "error", "Sequence fetch failed:", msg);
                setState("error");
                setErrorMsg(msg);
            });

        return () => {
            PersistentLogger.logWithContext("SequencePage", "info", "Unmounting — aborting request for id:", id);
            ac.abort();
        };
    }, [id, isTemplate]);


    if (state === "loading" && !seq) return <div>{t("sequence.loading")}</div>;
    if (state === "notfound") return <div>{t("sequence.notFound", { id })}</div>;
    if (state === "error" && !seq) return <div>{t("sequence.error", { msg: errorMsg })}</div>;
    if (!seq) return null;

    return (
        <main className="container py-4 px-3" style={{ maxWidth: 900 }}>
            <div className="mb-3 d-flex gap-2">
                <button className="btn btn-link" onClick={() => navigate(homePath)}>
                    ← {t("common.home")}
                </button>
                {fromWorksheet && (
                    <button
                        className="btn btn-link"
                        onClick={() =>
                            navigate(`/worksheets/${fromWorksheet}`, {
                                state: { fromClass },
                            })
                        }
                    >
                        ← {t("sequence.backToWorksheet")}
                    </button>
                )}
            </div>

            {import.meta.env.DEV && isTemplate && (
                <div style={{ marginBottom: 12, padding: 8, border: "1px dashed #bbb", borderRadius: 6 }}>
                    🧪 <strong>{t("sequence.devTemplate")}</strong>
                </div>
            )}

            {access.mustForkToEdit && teacherViewEnabled && (
                <div className="alert alert-info d-flex flex-wrap align-items-center gap-2 mb-3">
                    <div className="flex-grow-1">
                        <strong>{t("sequence.curatorNoticeTitle")}</strong>
                        <div className="small">{t("sequence.curatorNoticeBody")}</div>
                    </div>
                    <button
                        className="btn btn-sm btn-primary"
                        disabled={forking}
                        onClick={handleFork}
                    >
                        {forking ? t("sequence.forking") : t("sequence.forkToEdit")}
                    </button>
                </div>
            )}

            {canToggleTeacherView && (
                <div className="d-flex justify-content-end mb-3">
                    <button
                        className={`btn btn-sm ${teacherViewEnabled ? "btn-warning" : "btn-outline-warning"}`}
                        onClick={() => setTeacherViewEnabled(!teacherViewEnabled)}
                    >
                        {teacherViewEnabled ? t("sequence.hideTeacherMode") : t("sequence.teacherMode")}
                    </button>
                </div>
            )}

            {teacherViewEnabled && (
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
                    👩‍🏫 {t("sequence.teacherBanner")}
                </div>
            )}

            <div className="mb-2">
                <SequenceTitleEditor
                    sequenceId={seq.id}
                    title={seq.title}
                    canEdit={canEdit}
                    onRenamed={(title) => setSeq((current) => current && { ...current, title })}
                />
            </div>
            <div className="d-flex justify-content-end gap-2 mb-3">
                <button
                    className={`btn btn-sm d-flex align-items-center gap-1 ${
                        copied ? "btn-success" : "btn-outline-secondary"
                    }`}
                    onClick={handleCopyLink}
                    title={t("sequence.copyLinkTitle")}
                >
                    <i className="bi bi-link-45deg"></i>
                    {copied ? t("sequence.copied") : t("sequence.shareSequence")}
                </button>

                {canAddToWorksheet && (
                    <button
                        className="btn btn-sm btn-outline-secondary d-flex align-items-center gap-1"
                        onClick={() => setShowAddToWorksheetModal(true)}
                        title={t("sequence.addToWorksheetTitle")}
                    >
                        <i className="bi bi-journal-plus"></i>
                        {t("sequence.addToWorksheet")}
                    </button>
                )}

                {teacherViewEnabled && (
                    <div className="dropdown">
                        <button
                            className="btn btn-outline-secondary btn-sm dropdown-toggle d-flex align-items-center gap-1"
                            type="button"
                            data-bs-toggle="dropdown"
                            disabled={!seq?.tasks.length}
                        >
                            {t("sequence.export")}
                        </button>
                        <ul className="dropdown-menu">
                            <li><button className="dropdown-item" onClick={() => handleExportSequence("json")}>{t("sequence.exportJSON")}</button></li>
                            <li><button className="dropdown-item" onClick={() => handleExportSequence("csv")}>{t("sequence.exportCSV")}</button></li>
                            <li><hr className="dropdown-divider" /></li>
                            <li><button className="dropdown-item" onClick={() => handleExportSequence("pdf-freeform-teacher")}>{t("sequence.exportPdfTeacher")}</button></li>
                            <li><button className="dropdown-item" onClick={() => handleExportSequence("pdf-freeform-student")}>{t("sequence.exportPdfStudent")}</button></li>
                            <li><button className="dropdown-item" onClick={() => handleExportSequence("pdf-puzzle")}>{t("sequence.exportPdfPuzzle")}</button></li>
                        </ul>
                    </div>
                )}
            </div>

            <p className="mb-1 d-flex align-items-center gap-2">
                <strong>{t("sequence.uuid")}:</strong>
                <code className="user-select-all">{seq.id}</code>
                <button
                    className={`btn btn-sm py-0 px-1 ${uuidCopied ? "btn-success" : "btn-outline-secondary"}`}
                    onClick={handleCopyUuid}
                    title={t("sequence.copyUuidTitle")}
                    aria-label={t("sequence.copyUuidTitle")}
                >
                    <i className={`bi ${uuidCopied ? "bi-check2" : "bi-clipboard"}`}></i>
                </button>
            </p>

            <p>
                <strong>{t("sequence.language")}:</strong> {seq.programmingLanguage}
            </p>

            {/* The concepts the sequence was generated for. Teacher view only: on a
                Parsons puzzle "recursion" or "for-loop" names the shape of the
                solution, which is a hint the solver has not earned yet.

                Teacher view is no longer owner-only — a curator inspecting someone
                else's sequence sees it too — so editing is gated on canEdit
                separately. Concepts are part of the shared sequence, so a curator's
                change would rewrite them in every other worksheet using it, exactly
                what the fork exists to avoid. */}
            {teacherViewEnabled && (
                <div className="mb-3">
                    <div className="d-flex flex-wrap gap-1 align-items-center">
                        <span className="me-1 text-muted small">
                            <i className="bi bi-diagram-3 me-1"></i>
                            {t("sequence.concepts", "Concepts")}:
                        </span>
                        {!editingConcepts && seq.concepts.map((c) => (
                            <span key={c} className="badge bg-secondary-subtle text-secondary-emphasis">
                                {c}
                            </span>
                        ))}
                        {!editingConcepts && seq.concepts.length === 0 && (
                            <span className="text-muted small fst-italic">{t("sequence.conceptsNone")}</span>
                        )}
                        {!editingConcepts && (
                            <button
                                className="btn btn-sm btn-link text-secondary p-0 ms-1"
                                disabled={!canEdit}
                                title={canEdit ? t("sequence.conceptsEdit") : t("sequence.conceptsEditLocked")}
                                aria-label={canEdit ? t("sequence.conceptsEdit") : t("sequence.conceptsEditLocked")}
                                onClick={() => { setConceptError(""); setEditingConcepts(true); }}
                            >
                                <i className="bi bi-pencil"></i>
                            </button>
                        )}
                    </div>

                    {canEdit && editingConcepts && (
                        <div className="mt-2" style={{ maxWidth: 520 }}>
                            <ConceptPicker
                                inputId="sequenceConcepts"
                                value={seq.concepts}
                                catalogue={conceptCatalogue}
                                onChange={saveConcepts}
                            />
                            {conceptError && <div className="text-danger small mt-1">{conceptError}</div>}
                            <div className="d-flex gap-2 align-items-center mt-2">
                                <button
                                    className="btn btn-sm btn-outline-secondary"
                                    onClick={() => { setConceptError(""); setEditingConcepts(false); }}
                                    disabled={savingConcepts}
                                >
                                    {t("sequence.conceptsDone")}
                                </button>
                                {savingConcepts && (
                                    <span className="spinner-border spinner-border-sm text-secondary" role="status" />
                                )}
                                <span className="form-text m-0">{t("sequence.conceptsEditHint")}</span>
                            </div>
                        </div>
                    )}
                </div>
            )}

            {/* AP19 Teil A (A13): Library tags, teacher view only. LLM / manual / auto
                sources are colour-coded via TagChip, and a pulsing skeleton appears
                while the async LLM tagger is still working on freshly-generated
                sequences. Teacher view is owner/admin-only — the same condition the
                tag endpoints enforce — so the row is editable, as in the library. */}
            {teacherViewEnabled && (
                <div className="mb-3">
                    <div className="d-flex flex-wrap gap-1 align-items-center">
                        <span className="me-1 text-muted small">
                            <i className="bi bi-tag-fill me-1"></i>
                            {t("sequence.libraryTags", "Library tags")}:
                        </span>
                        {!editingTags && sequenceTags.map((tag) => (
                            <TagChip
                                key={`${tag.tagName}-${tag.source}`}
                                tag={tag}
                                fadeIn={tag.source === "llm"}
                            />
                        ))}
                        {!editingTags && sequenceTags.length === 0 && !isWaitingForLlm && (
                            <span className="text-muted small fst-italic">{t("library.tagEditor.noTags")}</span>
                        )}
                        {isWaitingForLlm && (
                            <TagChipSkeleton ariaLabel={t("sequence.llmTagLoading", "Waiting for semantic tag") as string} />
                        )}
                        {!editingTags && (
                            <button
                                className="btn btn-sm btn-link text-secondary p-0 ms-1"
                                title={t("library.editTags")}
                                aria-label={t("library.editTags")}
                                onClick={() => setEditingTags(true)}
                            >
                                <i className="bi bi-pencil"></i>
                            </button>
                        )}
                    </div>

                    {/* The same editor the library cards use, so a tag added here
                        behaves exactly as one added there. Its response is the full
                        server-side list, which outranks whatever the poll last saw. */}
                    {editingTags && (
                        <div className="mt-2" style={{ maxWidth: 520 }}>
                            <TagEditor
                                sequenceId={seq.id}
                                tags={sequenceTags}
                                onTagsChanged={setTags}
                            />
                            <div className="mt-2">
                                <button
                                    className="btn btn-sm btn-outline-secondary"
                                    onClick={() => setEditingTags(false)}
                                >
                                    {t("library.doneEditing")}
                                </button>
                            </div>
                        </div>
                    )}
                </div>
            )}

            {teacherViewEnabled && (
                <div>
                    <TaskDescriptionEditor
                        sequenceId={seq.id}
                        taskDescription={seq.taskDescription}
                        canEdit={canEdit}
                        onSaved={(taskDescription) =>
                            setSeq((current) => current && { ...current, taskDescription })
                        }
                    />
                    {seq.codeSnippet && (
                        <div className="accordion my-4" id="snippetAccordion">
                            <div className="accordion-item border-0 shadow-sm rounded-3">
                                <h2 className="accordion-header">
                                    <button
                                        className="accordion-button collapsed fw-semibold"
                                        type="button"
                                        data-bs-toggle="collapse"
                                        data-bs-target="#collapseSnippet"
                                        aria-expanded="false"
                                        aria-controls="collapseSnippet"
                                    >
                                        {t("sequence.codeSnippet")}
                                    </button>
                                </h2>
                                <div
                                    id="collapseSnippet"
                                    className="accordion-collapse collapse"
                                    data-bs-parent="#snippetAccordion"
                                >
                                    <div className="accordion-body bg-light rounded-bottom">
                                        <pre
                                            className="mb-0"
                                            style={{
                                                overflowX: "auto",
                                                background: "#f8f9fa",
                                                borderRadius: "6px",
                                                padding: "1rem",
                                                fontFamily: "monospace",
                                            }}
                                        >
                                            <code>{seq.codeSnippet}</code>
                                        </pre>
                                    </div>
                                </div>
                            </div>
                        </div>
                    )}
                </div>
            )}

            {/* AP20: Display generation constraints in teacher view */}
            {teacherViewEnabled && seq.constraints && (
                <div className="card border-0 shadow-sm rounded-3 mb-3" style={{ background: "#f0f4ff" }}>
                    <div className="card-body py-2 px-3">
                        <div className="fw-semibold mb-1" style={{ fontSize: "0.85rem", color: "#4a5568" }}>
                            🎛️ {t("sequence.constraints", "Generation Constraints")}
                        </div>
                        <div className="d-flex flex-wrap gap-2" style={{ fontSize: "0.82rem" }}>
                            {seq.constraints.blacklist && seq.constraints.blacklist.length > 0 && (
                                <span className="badge text-bg-danger bg-opacity-75">
                                    🚫 {seq.constraints.blacklist.join(", ")}
                                </span>
                            )}
                            {seq.constraints.whitelist && seq.constraints.whitelist.length > 0 && (
                                <span className="badge text-bg-success bg-opacity-75">
                                    ✅ {seq.constraints.whitelist.join(", ")}
                                </span>
                            )}
                            {seq.constraints.difficultySlope && (
                                <span className="badge text-bg-info bg-opacity-75">
                                    📈 {seq.constraints.difficultySlope}
                                </span>
                            )}
                            {seq.constraints.additionalContext && (
                                <span className="badge text-bg-secondary bg-opacity-75" title={seq.constraints.additionalContext}>
                                    💬 {seq.constraints.additionalContext.length > 40
                                        ? seq.constraints.additionalContext.substring(0, 40) + "…"
                                        : seq.constraints.additionalContext}
                                </span>
                            )}
                        </div>
                    </div>
                </div>
            )}

            {/* AP15b: Batch distractor generation for entire sequence */}
            {teacherViewEnabled && seq.tasks.some(t => !t.distractors) && (
                <div className="mb-3 d-flex justify-content-end">
                    <button
                        className="btn btn-outline-danger fw-semibold"
                        disabled={!canEdit || generatingDistractors !== null}
                        onClick={async () => {
                            setGeneratingDistractors("batch");
                            try {
                                const { data } = await api.post(`/sequence/${seq.id}/generate-distractors`);
                                // Reload sequence with updated tasks
                                if (data?.tasks) {
                                    setSeq({
                                        ...seq,
                                        tasks: data.tasks.map((tk: any) => ({
                                            id: String(tk.id),
                                            statement: tk.statement ?? "",
                                            solution: tk.solution ?? "",
                                            tierLevel: tk.tierLevel ?? 0,
                                            title: tk.title ?? null,
                                            cyclomaticComplexity: tk.cyclomaticComplexity ?? 0,
                                            sequenceId: String(tk.sequenceId ?? seq.id),
                                            programmingLanguage: tk.programmingLanguage ?? seq.programmingLanguage,
                                            distractors: tk.distractors ?? null,
                                            distractorPlan: tk.distractorPlan ?? null,
                                            distractorsEnabled: tk.distractorsEnabled !== false,
                                        })),
                                    });
                                }
                            } catch (err: any) {
                                alert(err?.response?.data?.message || "Batch distractor generation failed");
                            } finally {
                                setGeneratingDistractors(null);
                            }
                        }}
                    >
                        {generatingDistractors === "batch" ? (
                            <span className="spinner-border spinner-border-sm me-1" />
                        ) : null}
                        {t("sequence.batchGenerateDistractors")}
                    </button>
                </div>
            )}

            <section>
                <h2>{t("sequence.tasks")}</h2>
                {seq.tasks.length === 0 ? (
                    <p>{t("sequence.noTasks")}</p>
                ) : (
                    seq.tasks.map((task) => (
                        <article
                            key={task.id}
                            className="card"
                            style={{
                                border: "1px solid #ddd",
                                borderRadius: 8,
                                padding: 16,
                                marginBottom: 16,
                                position: "relative",
                            }}
                        >
                            <div className="d-flex justify-content-between align-items-center">
                                <TaskTitleEditor
                                    task={task}
                                    canEdit={canEdit && teacherViewEnabled}
                                    onRenamed={(title) =>
                                        setSeq((current) => current && {
                                            ...current,
                                            tasks: current.tasks.map((tk) => tk.id === task.id ? { ...tk, title } : tk),
                                        })
                                    }
                                />
                                <div className="d-flex gap-2">
                                    {teacherViewEnabled && (
                                        <>
                                        <button
                                            className="btn btn-sm btn-outline-secondary fw-semibold"
                                            disabled={!canEdit}
                                            onClick={() => handleOpenEdit(task)}
                                            title={t("sequence.editTask")}
                                        >
                                            {t("sequence.editTask")}
                                        </button>
                                        {/* AP15b: On-demand distractor generation */}
                                        <button
                                            className={`btn btn-sm fw-semibold ${task.distractors ? "btn-outline-danger" : "btn-outline-info"}`}
                                            disabled={!canEdit || generatingDistractors !== null}
                                            title={task.distractors ? t("sequence.regenerateDistractors") : t("sequence.generateDistractors")}
                                            onClick={async () => {
                                                setGeneratingDistractors(task.id);
                                                try {
                                                    const { data } = await api.post(`/tasks/${task.id}/generate-distractors`);
                                                    // Update task in-place
                                                    if (seq) {
                                                        setSeq({
                                                            ...seq,
                                                            tasks: seq.tasks.map(t =>
                                                                t.id === task.id ? { ...t, distractors: data.distractors ?? null } : t
                                                            ),
                                                        });
                                                    }
                                                } catch (err: any) {
                                                    alert(err?.response?.data?.message || "Distractor generation failed");
                                                } finally {
                                                    setGeneratingDistractors(null);
                                                }
                                            }}
                                        >
                                            {generatingDistractors === task.id ? (
                                                <span className="spinner-border spinner-border-sm me-1" />
                                            ) : null}
                                            {task.distractors ? t("sequence.regenerateDistractors") : t("sequence.generateDistractors")}
                                        </button>
                                        </>
                                    )}
                                    <button
                                        className={`btn btn-sm fw-semibold ${
                                            teacherViewEnabled ? "btn-warning" : "btn-primary"
                                        }`}
                                        style={{ minWidth: "120px" }}
                                        onClick={() => {
                                            try {
                                                logEvent(task.id, "tier_opened", {
                                                    tierLevel: task.tierLevel,
                                                    sequenceTaskCount: seq?.tasks.length ?? 0,
                                                }, seq?.id, task.tierLevel);
                                            } catch { /* silent */ }
                                            navigate(`/solve/${task.id}`, {
                                                state: {
                                                    lang: seq?.programmingLanguage,
                                                    task,
                                                    fromWorksheet,
                                                    fromClass,
                                                },
                                            });
                                        }}
                                    >
                                        {teacherViewEnabled ? t("sequence.reviewTask") : t("sequence.solveTask")}
                                    </button>
                                </div>
                            </div>
                            {teacherViewEnabled && (
                                <div
                                    className="accordion my-3 position-relative"
                                    id={`solution-${task.id}`}
                                    style={{ zIndex: 2 }}
                                >
                                    <div className="accordion-item border-0 shadow-sm">
                                        <h2 className="accordion-header">
                                            <button
                                                className="accordion-button collapsed fw-semibold"
                                                type="button"
                                                data-bs-toggle="collapse"
                                                data-bs-target={`#collapseSolution-${task.id}`}
                                                aria-expanded="false"
                                                aria-controls={`collapseSolution-${task.id}`}
                                            >
                                                {t("sequence.showSolution")}
                                            </button>
                                        </h2>
                                        <div
                                            id={`collapseSolution-${task.id}`}
                                            className="accordion-collapse collapse"
                                            data-bs-parent={`#solution-${task.id}`}
                                        >
                                            <div className="accordion-body bg-light rounded-bottom">
                                                <pre style={{ overflowX: "auto" }}>
                                                    <p className="mt-3" style={{ whiteSpace: "pre-wrap" }}>
                                                        <strong>{t("sequence.taskDescription")}:</strong> {task.statement}
                                                    </p>
                                                    <code>{task.solution}</code>
                                                </pre>
                                                {/* AP22: Context mode indicator in teacher view */}
                                                {(task.contextBefore || task.contextAfter) && (() => {
                                                    const ctxBeforeCount = task.contextBefore ? task.contextBefore.split("\n").length : 0;
                                                    const ctxAfterCount = task.contextAfter ? task.contextAfter.split("\n").length : 0;
                                                    const contextCount = ctxBeforeCount + ctxAfterCount;
                                                    const puzzleCount = task.solution.split("\n").filter(l => l.trim() && !/#distractor\s*$/.test(l)).length;
                                                    return (
                                                        <div className="mt-2 mb-2">
                                                            <small className="text-info d-flex align-items-center gap-1">
                                                                <span>&#x1F4D6;</span>
                                                                Context-Modus: {contextCount} Kontext-Zeilen, {puzzleCount} Puzzle-Zeilen
                                                            </small>
                                                        </div>
                                                    );
                                                })()}

                                                {/* AP15: Show distractors in teacher view */}
                                                {task.distractors && (
                                                    <div className="mt-3 pt-3 border-top">
                                                        <div className="d-flex align-items-center justify-content-between mb-2">
                                                            <strong className="text-danger d-flex align-items-center gap-1">
                                                                <span>⚠</span> Distraktoren ({task.distractors.split("\n").filter(l => l.trim()).length})
                                                            </strong>
                                                            {/* AP15: Toggle distractors enabled/disabled */}
                                                            <div className="form-check form-switch ms-3">
                                                                <input
                                                                    className="form-check-input"
                                                                    type="checkbox"
                                                                    role="switch"
                                                                    disabled={!canEdit}
                                                                    id={`distractors-toggle-${task.id}`}
                                                                    checked={task.distractorsEnabled !== false}
                                                                    onChange={async (e) => {
                                                                        const enabled = e.target.checked;
                                                                        try {
                                                                            await api.patch(`/tasks/${task.id}/distractors-enabled`, { enabled });
                                                                            if (seq) {
                                                                                setSeq({
                                                                                    ...seq,
                                                                                    tasks: seq.tasks.map(t =>
                                                                                        t.id === task.id ? { ...t, distractorsEnabled: enabled } : t
                                                                                    ),
                                                                                });
                                                                            }
                                                                        } catch (err: any) {
                                                                            alert(err?.response?.data?.message || "Toggle failed");
                                                                        }
                                                                    }}
                                                                />
                                                                <label className="form-check-label small" htmlFor={`distractors-toggle-${task.id}`}>
                                                                    {task.distractorsEnabled !== false
                                                                        ? t("sequence.distractorsActive")
                                                                        : t("sequence.distractorsInactive")}
                                                                </label>
                                                            </div>
                                                        </div>
                                                        {task.distractorPlan && (() => {
                                                            try {
                                                                const plan = JSON.parse(task.distractorPlan);
                                                                return (
                                                                    <small className="text-muted d-block mb-2">
                                                                        {t("sequence.typeLabel")} <code>{plan.type}</code> — {plan.rationale}
                                                                    </small>
                                                                );
                                                            } catch { return null; }
                                                        })()}
                                                        <pre className="bg-white border border-danger border-opacity-25 rounded p-2 mb-0" style={{ overflowX: "auto", fontSize: "0.85rem" }}>
                                                            <code className="text-danger">
                                                                {task.distractors.split("\n").filter(l => l.trim()).join("\n")}
                                                            </code>
                                                        </pre>
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            )}
                        </article>
                    ))
                )}
            </section>

            {/* AP19 A15: "You might also like…" — teacher-only discovery panel.
                The Library is teacher-facing, so students never see this. The panel
                fetches its own data and silently hides itself when there are no
                meaningful neighbours, so gating on `teacherViewEnabled && id` is enough. */}
            {teacherViewEnabled && id && (
                <SimilarSequencesPanel sequenceId={id} limit={3} />
            )}

            {state === "error" && <p style={{ color: "#b00" }}>(Note) {errorMsg}</p>}

            {/* Sequence Rating Button (student view) */}
            {!teacherViewEnabled && seq.tasks.length > 0 && (
                <div className="text-center mt-4 mb-3">
                    <button
                        className="btn btn-outline-primary"
                        onClick={() => { setShowRatingModal(true); setRatingSubmitted(false); }}
                    >
                        {t("sequence.rateSequence")}
                    </button>
                </div>
            )}

            {/* Rating Modal */}
            {showRatingModal && (
                <div className="modal show d-block" style={{ backgroundColor: "rgba(0,0,0,0.5)" }}>
                    <div className="modal-dialog modal-dialog-centered">
                        <div className="modal-content">
                            <div className="modal-header">
                                <h5 className="modal-title">{t("sequence.rateTitle")}</h5>
                                <button className="btn-close" onClick={() => setShowRatingModal(false)} />
                            </div>
                            <div className="modal-body">
                                {ratingSubmitted ? (
                                    <div className="alert alert-success mb-0">{t("sequence.ratingThanks")}</div>
                                ) : (
                                    <>
                                        <div className="mb-3">
                                            <label className="form-label fw-semibold">
                                                {t("sequence.enjoyment", { rating: enjoymentRating })}
                                            </label>
                                            <input type="range" className="form-range" min={1} max={5} step={1}
                                                value={enjoymentRating}
                                                onChange={(e) => setEnjoymentRating(parseInt(e.target.value))} />
                                        </div>
                                        <div className="mb-3">
                                            <label className="form-label fw-semibold">
                                                {t("sequence.difficulty", { rating: difficultyRating })}
                                            </label>
                                            <input type="range" className="form-range" min={1} max={5} step={1}
                                                value={difficultyRating}
                                                onChange={(e) => setDifficultyRating(parseInt(e.target.value))} />
                                        </div>
                                    </>
                                )}
                            </div>
                            {!ratingSubmitted && (
                                <div className="modal-footer">
                                    <button className="btn btn-secondary" onClick={() => setShowRatingModal(false)}>
                                        {t("common.cancel")}
                                    </button>
                                    <button className="btn btn-primary" onClick={handleSubmitRating}>
                                        {t("sequence.submitRating")}
                                    </button>
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            )}

            {/* ADD TO WORKSHEET MODAL */}
            {showAddToWorksheetModal && (
                <AddToWorksheetModal
                    sequenceId={seq.id}
                    onClose={() => setShowAddToWorksheetModal(false)}
                />
            )}

            {/* TaskEditModal */}
            {editingTask && (
                <TaskEditModal
                    taskId={editingTask.id}
                    initialProblemStatement={editingTask.statement}
                    initialCode={editingTask.solution}
                    initialDistractors={editingTask.distractors}
                    distractorPlan={editingTask.distractorPlan}
                    initialSplitPlan={editingTask.splitPlan}
                    isFirstEdit={editIsFirstEdit}
                    onSave={handleEditSave}
                    onClose={() => setEditingTask(null)}
                />
            )}
        </main>
    );
}
