/* eslint-env browser */
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {useNavigate, useParams} from "react-router-dom";
import {api} from "../lib/api.ts";
import type {Task} from "../types/models.ts";
import {parseSplitPlan} from "../types/models.ts";
import $ from "jquery";
import {usePageTitle} from "../hooks/usePageTitle.ts";
import {useHomePath} from "../hooks/useHomePath.ts";
import { useLocation } from "react-router-dom";
import {PersistentLogger} from "../PersistentLogger.ts";
import { useAuth } from "../context/AuthContext.tsx";
import { useEventTracker } from "../hooks/useEventTracker.ts";
import { useTranslation } from "react-i18next";
import DOMPurify from "dompurify";
import HintButton from "../components/HintButton.tsx";
import { formatGraderFeedback } from "../lib/graderFeedback.ts";
import { restoreSubmission } from "../lib/restoreSubmission.ts";
import { readArrangement } from "../lib/readArrangement.ts";
import { applyHintLabels } from "../lib/hintHighlight.ts";
import { shuffleElementChildren } from "../lib/shuffleSource.ts";
import { taskName } from "../lib/taskName.ts";

declare global {
    interface Window {
        ParsonsWidget?: any;
        prettyPrint?: (whenDone?: () => void) => void;
    }
}

/**
 * AP30: client-side evaluation of a fill-in-the-gap input.
 * When `expected` is null the gap is not auto-graded (always neutral/correct).
 * mode ∈ {exact, trim, ci}; default handling done by the caller.
 */
function checkBlank(value: string, expected: string | null, mode: string): boolean {
    if (expected == null) return true;
    let a = value;
    let b = expected;
    if (mode === "trim" || mode === "ci") { a = a.trim(); b = b.trim(); }
    if (mode === "ci") { a = a.toLowerCase(); b = b.toLowerCase(); }
    return a === b;
}

export default function SolvePage() {
    const { t, i18n } = useTranslation();
    const location = useLocation();
    const langFromNav = location.state?.lang;

    const [language, setLanguage] = useState(langFromNav ?? "unknown");
    const lastFeedbackAtRef = useRef(0);
    usePageTitle(t("solve.pageTitle"));
    const homePath = useHomePath();
    const {taskId} = useParams<{ taskId: string }>();

    useEffect(() => {
        if (!language && taskId) {
            api.get(`/tasks/${taskId}`).then(({ data }) => setLanguage(data.programmingLanguage));
        }
    }, [language, taskId]);

    const { user, isLoggedIn, isAdmin } = useAuth();
    const [copied, setCopied] = useState(false);
    const navigate = useNavigate();
    const [task, setTask] = useState<Task | null>(null);
    const [loading, setLoading] = useState(false);
    const [teacherViewEnabled, setTeacherViewEnabled] = useState(false);
    const [teacherToolsVisible, setTeacherToolsVisible] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [sequenceOwnerId, setSequenceOwnerId] = useState<string | null>(null);

    // Event tracking (AP10/AP11)
    const { logEvent } = useEventTracker();
    const startTimeRef = useRef(Date.now());
    const [, setAttemptCount] = useState(0);
    const [, setFeedbackCount] = useState(0);
    const attemptCountRef = useRef(0);
    const feedbackCountRef = useRef(0);
    const lastActivityRef = useRef(Date.now());
    const activeTimeRef = useRef(0);
    const activityIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
    // Which task the open/close pair currently considers open, and a close that has been
    // scheduled but not yet written. Both survive a StrictMode remount, which is the point —
    // see the task_opened effect below.
    const openTaskRef = useRef<string | null>(null);
    const pendingCloseRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const [previousCorrectCount, setPreviousCorrectCount] = useState(0);
    const [, setBacktrackCount] = useState(0);
    const backtrackCountRef = useRef(0);
    const correctBlocksRef = useRef<Set<string>>(new Set());
    const isCompleteRef = useRef(false);

    // Hint system (AP13)
    const [hintText, setHintText] = useState<string | null>(null);
    const [hintExplanation, setHintExplanation] = useState<string | null>(null);
    const [, setHintAffectedBlocks] = useState<number[]>([]);
    const [hintLoading, setHintLoading] = useState(false);
    const [hintError, setHintError] = useState<string | null>(null);
    const [hintsUnlocked, setHintsUnlocked] = useState(false);
    const [hintsEnabled, setHintsEnabled] = useState(true);
    // null = unlimited (no budget configured on the worksheet)
    const [creditsRemaining, setCreditsRemaining] = useState<number | null>(null);
    const [creditsPerTask, setCreditsPerTask] = useState<number | null>(null);
    // A previously solved arrangement, restored into the puzzle on open.
    const [solvedPreviously, setSolvedPreviously] = useState(false);
    /** Unfinished work was restored, so the student is offered a way to drop it. */
    const [hasSavedProgress, setHasSavedProgress] = useState(false);
    /**
     * The task is solved, so grading it again would say nothing and the check button is closed
     * off until the student asks for another go with Redo.
     *
     * Deliberately not solvedPreviously, which stays true through a Redo so the Redo button
     * keeps its place -- and deliberately not showCompletionBanner, which the student can
     * dismiss. This is the one signal that means "solved, right now".
     */
    const [solutionLocked, setSolutionLocked] = useState(false);
    // False once a worksheet deadline (plus grace) has passed: the task can
    // still be read, but not worked on.
    const [canSolve, setCanSolve] = useState(true);
    const restoredRef = useRef(false);
    // When the restored solution was first solved, reported with the redo event.
    const solvedAtRef = useRef<string | null>(null);

    // ── Autosave of work in progress, and the research trail of how it got there ──
    // Both are driven by the same widget hook (see the addLogEntry wrapper below): one read
    // of the DOM, two destinations. The autosave is debounced because a drag produces one
    // change and a student produces many; the research buffer is flushed in batches because
    // useEventTracker posts one request per call and a move-by-move stream would be one
    // request every few seconds, per student.
    const progressTimerRef = useRef<number | null>(null);
    const progressPendingRef = useRef(false);
    const moveBufferRef = useRef<Array<Record<string, unknown>>>([]);
    const moveBatchStartRef = useRef<number>(0);
    const moveFlushTimerRef = useRef<number | null>(null);
    /** Set by the widget effect; lets "start over" cancel pending saves it cannot otherwise see. */
    const discardProgressRef = useRef<(() => void) | null>(null);
    /**
     * Set by the widget effect; lets the Feedback handler empty the move buffer before it logs
     * a verdict. Same reason discardProgressRef exists — the buffer lives in a closure the
     * handler has no other way to reach.
     */
    const flushMovesRef = useRef<(() => void) | null>(null);
    const hintShownAtRef = useRef<number>(0);
    const lastHintLevelRef = useRef<number>(0);
    const correctCountBeforeHintRef = useRef<number | null>(null);
    const hintWasActiveRef = useRef<boolean>(false);

    // AP18: Completion banner + next task
    const [showCompletionBanner, setShowCompletionBanner] = useState(false);
    const [nextTaskId, setNextTaskId] = useState<string | null>(null);

    // AP17: Click-to-Move mode toggle
    const [interactionMode, setInteractionMode] = useState<"drag" | "click">(() => {
        try { return (localStorage.getItem("codeowl_interaction_mode") as "drag" | "click") || "drag"; }
        catch { return "drag"; }
    });

    // AP14: Advanced analytics state
    const moveCountRef = useRef(0);
    const lastMoveAtRef = useRef(0);
    const firstMoveAtRef = useRef(0);
    const movePausesRef = useRef<number[]>([]);
    const stateHistoryRef = useRef<string[]>([]);
    const noProgressCountRef = useRef(0);

    // AP15: Distractor tracking
    const distractorTextsRef = useRef<Set<string>>(new Set());
    const distractorsInSolutionRef = useRef<Set<string>>(new Set());
    const distractorPlacedAtRef = useRef<Map<string, number>>(new Map());
    const lastFeedbackBeforeRemovalRef = useRef<boolean>(false);
    const lastHintBeforeRemovalRef = useRef<boolean>(false);
    const [showSelfReport, setShowSelfReport] = useState(false);

    // AP22: Context code blocks (read-only) — derived from task, no separate state needed
    const contextBefore = useMemo(() => task?.contextBefore ? task.contextBefore.split("\n") : [], [task?.contextBefore]);
    const contextAfter = useMemo(() => task?.contextAfter ? task.contextAfter.split("\n") : [], [task?.contextAfter]);

    const blockOrderAfterFeedbackRef = useRef<string[]>([]);
    const movesSinceFeedbackRef = useRef(0);
    const correctUndoCountRef = useRef(0);

    // Fetch hint config: teacher toggle plus this student's remaining credits.
    const refreshHintConfig = useCallback(() => {
        if (!taskId) return;
        api.get(`/hints/${taskId}/config`).then(({ data }) => {
            setHintsEnabled(data.hintsEnabled ?? true);
            setCreditsPerTask(data.creditsPerTask ?? null);
            setCreditsRemaining(data.creditsRemaining ?? null);
        }).catch(() => { /* default: enabled, unlimited */ });
    }, [taskId]);

    useEffect(() => { refreshHintConfig(); }, [refreshHintConfig]);

    // Whether the worksheet this task belongs to is still open for this user.
    // Advisory only — the services that record solving re-check it.
    useEffect(() => {
        if (!taskId) return;
        api.get(`/tasks/${taskId}/access`)
            .then(({ data }) => setCanSolve(data?.canSolve !== false))
            .catch(() => setCanSolve(true));
    }, [taskId]);

    // Role-based teacher view: show toggle for owners and admins
    const isOwnerOrAdmin = isLoggedIn && user && (sequenceOwnerId === user.userId || isAdmin);
    const canToggleTeacherView = !!isOwnerOrAdmin;

    // Auto-enable teacher view when ownership is determined
    useEffect(() => {
        if (isOwnerOrAdmin) {
            setTeacherViewEnabled(true);
        } else {
            setTeacherViewEnabled(false);
        }
    }, [isOwnerOrAdmin]);

    async function handleCopyLink() {
        try {
            await navigator.clipboard.writeText(window.location.href);
            setCopied(true);
            setTimeout(() => setCopied(false), 1500);
        } catch {
            alert(t("solve.copyFailed"));
        }
    }
    useEffect(() => {
        PersistentLogger.logWithContext("SolvePage", "debug", "Mounted SolvePage with taskId:", taskId, "language:", language);

        if (!language && taskId) {
            PersistentLogger.logWithContext("SolvePage", "debug", "Fetching language for task:", taskId);
            api.get(`/task/${taskId}`)
                .then(({ data }) => {
                    PersistentLogger.logWithContext("SolvePage", "debug", "Language fetch success:", data.programmingLanguage);
                    setLanguage(data.programmingLanguage);
                })
                .catch((err: any) => {
                    PersistentLogger.logWithContext("SolvePage", "error", "Language fetch failed:", err?.message);
                });
        }
    }, [language, taskId]);

    useEffect(() => {
        if (!taskId) {
            PersistentLogger.logWithContext("SolvePage", "warn", "No taskId present — skipping fetch");
            return;
        }

        PersistentLogger.logWithContext("SolvePage", "debug", "Starting task fetch for ID:", taskId);
        setError(null);
        setLoading(true);

        if (taskId === "t1" && import.meta.env.DEV) {
            PersistentLogger.logWithContext("SolvePage", "debug", "DEV template mode for task t1 — skipping API");
            import("../dev/template.ts").then(({ loadTemplateSequence }) => {
                const seq = loadTemplateSequence();
                const firstTask = seq.tasks?.find(t => t.id === "t1");
                if (firstTask) {
                    PersistentLogger.logWithContext("SolvePage", "debug", "Loaded template task:", JSON.stringify(firstTask));
                    setTask(firstTask);
                }
            });
            return;
        }

        api.get(`/tasks/${taskId}`)
            .then(({ data }) => {
                PersistentLogger.logWithContext("SolvePage", "debug", "Task API success, raw data:", JSON.stringify(data));
                const lang =
                    data.programmingLanguage ??
                    location.state?.task?.programmingLanguage ??
                    location.state?.sequence?.programmingLanguage ??
                    "unknown";

                const t: Task = {
                    id: String(data.id),
                    statement: String(data.statement ?? ""),
                    solution: String(data.solution ?? ""),
                    tierLevel: Number(data.tierLevel ?? 0),
                    title: data.title ?? null,
                    cyclomaticComplexity: Number(data.cyclomaticComplexity ?? 0),
                    sequenceId: String(data.sequenceId ?? ""),
                    programmingLanguage: String(lang),
                    distractors: data.distractors ?? null,
                    distractorPlan: data.distractorPlan ?? null,
                    contextBefore: data.contextBefore ?? null,
                    contextAfter: data.contextAfter ?? null,
                    splitPlan: data.splitPlan ?? null, // AP31 — required for inline-split rendering
                };
                PersistentLogger.logWithContext("SolvePage", "debug", "Mapped Task object:", JSON.stringify(t));
                PersistentLogger.logWithContext("SolvePage", "debug", "AP22 context fields — before:", t.contextBefore ? "present (" + t.contextBefore.split("\n").length + " lines)" : "null", "after:", t.contextAfter ? "present (" + t.contextAfter.split("\n").length + " lines)" : "null");
                setTask(t);
            })
            .catch((err: any) => {
                const msg = err?.response?.data?.message || err?.message || t("solve.apiError");
                PersistentLogger.logWithContext("SolvePage", "error", "Task fetch failed:", msg);
                setError(msg);
            })
            .finally(() => {
                PersistentLogger.logWithContext("SolvePage", "debug", "Task fetch completed for", taskId);
                setLoading(false);
            });
    }, [taskId]);

    const sequenceId = task?.sequenceId;

    // Resolve sequence data: ownerId (teacher view) + nextTaskId (AP18)
    // Single API call instead of two separate ones
    useEffect(() => {
        if (!sequenceId) return;

        // Try localStorage first for ownerId (cached by SequencePage)
        let ownerResolved = false;
        try {
            const cached = localStorage.getItem(`sequence:${sequenceId}`);
            if (cached) {
                const parsed = JSON.parse(cached);
                if (parsed?.ownerId) {
                    setSequenceOwnerId(parsed.ownerId);
                    ownerResolved = true;
                }
            }
        } catch { /* ignore parse errors */ }

        // Always fetch for nextTaskId (and ownerId if not cached)
        api.get(`/sequence/${sequenceId}`)
            .then(({ data }) => {
                if (!ownerResolved && data?.ownerId) {
                    setSequenceOwnerId(data.ownerId);
                }
                // Resolve next task in the same sequence
                if (taskId) {
                    const tasks: { id: string; tierLevel: number }[] = data?.tasks ?? [];
                    const currentIdx = tasks.findIndex(t => String(t.id) === String(taskId));
                    if (currentIdx >= 0 && currentIdx < tasks.length - 1) {
                        setNextTaskId(String(tasks[currentIdx + 1].id));
                    } else {
                        setNextTaskId(null);
                    }
                }
            })
            .catch(() => setNextTaskId(null));
    }, [sequenceId, taskId]);

    // AP18: Reset completion state when navigating to a new task
    useEffect(() => {
        setShowCompletionBanner(false);
        setShowSelfReport(false);
        isCompleteRef.current = false;
        attemptCountRef.current = 0;
        feedbackCountRef.current = 0;
        moveCountRef.current = 0;
        firstMoveAtRef.current = 0;
        lastMoveAtRef.current = 0;
        movePausesRef.current = [];
        stateHistoryRef.current = [];
        noProgressCountRef.current = 0;
        blockOrderAfterFeedbackRef.current = [];
        movesSinceFeedbackRef.current = 0;
        correctUndoCountRef.current = 0;
        backtrackCountRef.current = 0;
        correctBlocksRef.current = new Set();
        distractorTextsRef.current = new Set();
        distractorsInSolutionRef.current = new Set();
        distractorPlacedAtRef.current = new Map();
        lastFeedbackBeforeRemovalRef.current = false;
        lastHintBeforeRemovalRef.current = false;
        moveBufferRef.current = [];
        moveBatchStartRef.current = 0;
        setHasSavedProgress(false);
        setSolutionLocked(false);
        setPreviousCorrectCount(0);
        setFeedbackHTML("");
        setHintText(null);
        setHintExplanation(null);
        setHintError(null);
        setHintsUnlocked(false);
    }, [taskId]);

    // Log task_opened on mount and task_closed on unmount (AP10)
    //
    // The close is deferred by a tick rather than written in the cleanup, because a cleanup is
    // not proof the student left. StrictMode mounts this effect, tears it down and mounts it
    // again in development, and writing the close synchronously turned every single task open
    // into task_opened -> task_closed(total_time_ms: 0) -> task_opened. That was 47% of all
    // task_closed rows by the time anyone noticed, and it looked like real data.
    //
    // So: on teardown, schedule the close. If the same task mounts straight back, cancel it and
    // carry on with the session already in progress -- no second task_opened, no phantom close,
    // and the timings keep running from the original open. A real navigation has nothing to
    // cancel it, so the close lands a tick later as it should.
    useEffect(() => {
        if (!taskId) return;

        const resuming = pendingCloseRef.current !== null && openTaskRef.current === taskId;
        if (resuming) {
            clearTimeout(pendingCloseRef.current!);
            pendingCloseRef.current = null;
        } else {
            startTimeRef.current = Date.now();
            activeTimeRef.current = 0;
            lastActivityRef.current = Date.now();
            openTaskRef.current = taskId;
            logEvent(taskId, "task_opened", {}, task?.sequenceId, task?.tierLevel);
        }

        // Activity tracking (AP11): track active time
        const INACTIVITY_THRESHOLD = 90_000; // 90 seconds
        const handleActivity = () => { lastActivityRef.current = Date.now(); };
        window.addEventListener("mousemove", handleActivity);
        window.addEventListener("keydown", handleActivity);

        activityIntervalRef.current = setInterval(() => {
            const now = Date.now();
            if (now - lastActivityRef.current < INACTIVITY_THRESHOLD) {
                activeTimeRef.current += 1000; // add 1 second of active time
            }
        }, 1000);

        const handleBeforeUnload = () => {
            const totalTime = Date.now() - startTimeRef.current;
            logEvent(taskId, "task_closed", {
                total_time_ms: totalTime,
                active_time_ms: activeTimeRef.current,
                attempts: attemptCountRef.current,
                feedback_requests: feedbackCountRef.current,
                completed: isCompleteRef.current,
                backtrack_count: backtrackCountRef.current,
            }, task?.sequenceId, task?.tierLevel);

            // Log task_abandoned if user leaves without completing (AP11)
            if (!isCompleteRef.current && attemptCountRef.current > 0) {
                logEvent(taskId, "task_abandoned", {
                    total_time_ms: totalTime,
                    active_time_ms: activeTimeRef.current,
                    attempts: attemptCountRef.current,
                    feedback_requests: feedbackCountRef.current,
                    backtrack_count: backtrackCountRef.current,
                }, task?.sequenceId, task?.tierLevel);
            }
        };
        window.addEventListener("beforeunload", handleBeforeUnload);

        return () => {
            // Deferred, so an immediate remount of the same task can cancel it. Anything that
            // really ends the visit -- navigating away, unmounting for good -- leaves nothing
            // to cancel it and the close is written on the next tick.
            //
            // Both refs are checked before being cleared: moving from task A to task B runs
            // A's cleanup, then B's effect, and only then A's timer. Clearing unconditionally
            // would wipe the state B had just set for itself.
            const closingTaskId = taskId;
            const timer = setTimeout(() => {
                if (pendingCloseRef.current === timer) pendingCloseRef.current = null;
                if (openTaskRef.current === closingTaskId) openTaskRef.current = null;
                handleBeforeUnload();
            }, 0);
            pendingCloseRef.current = timer;

            window.removeEventListener("mousemove", handleActivity);
            window.removeEventListener("keydown", handleActivity);
            window.removeEventListener("beforeunload", handleBeforeUnload);
            if (activityIntervalRef.current) clearInterval(activityIntervalRef.current);
        };
    }, [taskId]);

    const widgetRef = useRef<any>(null);
    const [feedbackHTML, setFeedbackHTML] = useState("");

    // Grading produces already-resolved strings, so text on screen cannot
    // retranslate itself when the language changes. Clear it rather than leave a
    // German page showing an English verdict; the next grading refills it.
    useEffect(() => {
        const onLanguageChanged = () => setFeedbackHTML("");
        i18n.on("languageChanged", onLanguageChanged);
        return () => { i18n.off("languageChanged", onLanguageChanged); };
    }, [i18n]);
    useEffect(() => {
        if (!task?.solution || !window.ParsonsWidget) return;

        const widget = new window.ParsonsWidget({
            sortableId: "jsparsons-target",
            trashId: "jsparsons-source",
            can_indent: true,
            max_wrong_lines: task.distractors
                ? task.distractors.split("\n").filter((l: string) => l.trim()).length
                : 0,
            programmingLang: task.programmingLanguage?.toLowerCase() ?? "",

            // Empty strings suppress the library's own column labels
            // (parsons.js:1805 only emits the <p> when these are truthy). They are
            // baked into the DOM at construction time and this effect does not
            // re-run on a language change, so they would be stuck in whichever
            // language was active when the task loaded. The <h6> headings that
            // SolvePage renders above each column say the same thing and, being
            // React, follow the language switch.
            trash_label: "",
            solution_label: "",

            grader: function CustomLineBasedGrader(p: any) {
                const base = new window.ParsonsWidget._graders.LineBasedGrader(p);

                this.grade = (elementId?: string) => {
                    // Run base grader
                    const fb = base.grade(elementId);
                    const lang: string = (p.options.programmingLang || "").toLowerCase();

                    // Apply stricter indentation rules for Python
                    // Recognize all DB variations: "Python", "python", "PYTHON", "py", "Python3", etc.
                    const isPython = /^py(thon)?(3)?$/i.test(lang.trim());
                    if (isPython) {
                        $(`#ul-${p.options.sortableId} li.incorrectIndent`).each(function (this: HTMLElement) {
                            $(this)
                                .removeClass("incorrectIndent")
                                .addClass("incorrectPosition")
                                .css("border", "1px solid red")
                                .css("background-color", "#ffbaba")
                                .attr("title", t("solve.indentationWarning"));
                        });
                    }

                    return fb;
                };
            },
        });


        // AP22: Context lines come from dedicated DB fields (contextBefore/contextAfter).
        // They are derived via useMemo from task — no setState needed here.

        // AP15: Append distractor lines to solution before init
        let codeForWidget = task.solution;
        if (task.distractors) {
            codeForWidget = codeForWidget + "\n" + task.distractors;
        }
        // The constructor always populates `translations` (falling back to its
        // English table), so it is never missing. Its entries are functions, and
        // they are what the grader writes into the feedback box.
        //
        // Only these four are reachable here: order/lines_too_many/lines_missing
        // come from LineBasedGrader, which is the grader below, and no_matching
        // from the widget's own indentation check. The remaining entries belong
        // to graders this app never instantiates (LanguageTranslationGrader,
        // UnitTestGrader, VariableCheckGrader), so translating them would only
        // create the appearance of coverage.
        if (widget.translations) {
            //
            // These call i18n.t rather than the t from useTranslation. This effect
            // runs once per task, so a captured t would stay bound to the language
            // that was active then and keep answering in it after a switch;
            // i18n.t resolves against the instance's current language every call.
            widget.translations.no_matching = (lineNro: number) =>
                i18n.t("solve.no_matching", { line: lineNro });
            widget.translations.order = () => i18n.t("solve.grader_order");
            widget.translations.lines_too_many = () => i18n.t("solve.grader_lines_too_many");
            widget.translations.lines_missing = () => i18n.t("solve.grader_lines_missing");
            // Not part of upstream js-parsons: the Python indent message was
            // hardcoded in our vendored copy and is now routed through here too.
            widget.translations.indent_python = () => i18n.t("solve.grader_indent_python");
        }

        widget.init(codeForWidget);
        widget.shuffleLines();
        widgetRef.current = widget;

        // If this student already solved the task, put their arrangement back
        // rather than handing them a reshuffled puzzle. Fire-and-forget: a failed
        // lookup simply means they start fresh, which is the old behaviour.
        // Anonymous visitors get 204, since there is nobody to have stored work.
        restoredRef.current = false;
        api.get(`/tasks/${task.id}/submission`)
            .then(({ data }) => {
                if (!data?.blockOrder?.length) return;
                const { restored, placed } = restoreSubmission(
                    document.getElementById("ul-jsparsons-source"),
                    document.getElementById("ul-jsparsons-target"),
                    data.blockOrder,
                    data.indentLevels,
                    widget.options?.x_indent || 50,
                );
                // restoreSubmission only sets margin-left, which is how indentation is drawn.
                // Grading reads line.indent on the widget's own model, which init left at 0, so
                // without this a restored solution is graded as fully unindented until the
                // student drags something -- a correct solution marked wrong.
                for (const { id, indent } of placed) {
                    const line = widget.getLineById?.(id);
                    if (line) line.indent = widget.options?.can_indent ? indent : 0;
                }
                if (restored > 0) {
                    restoredRef.current = true;
                    solvedAtRef.current = data.solvedAt ?? null;
                    // Only a solved arrangement makes the task solved. An in-progress restore
                    // that claimed otherwise would offer Redo for work not yet finished, and
                    // tell the student they had already completed it.
                    if (data.solved) {
                        setSolvedPreviously(true);
                        // Reopened already solved: what is on screen is the arrangement that
                        // was graded correct, so there is nothing to check until they redo it.
                        setSolutionLocked(true);
                    }
                    else setHasSavedProgress(true);
                }
            })
            .catch(() => { /* nothing stored, or not logged in */ });

        // AP15: Populate distractor text set for analytics tracking
        if (task.distractors) {
            const dTexts = new Set<string>();
            task.distractors.split("\n").forEach((line: string) => {
                const trimmed = line.trim();
                if (trimmed) {
                    // Strip #distractor suffix to get the display text
                    const displayText = trimmed.replace(/\s*#distractor\s*$/, "").trim();
                    if (displayText) dTexts.add(displayText);
                }
            });
            distractorTextsRef.current = dTexts;
        } else {
            distractorTextsRef.current = new Set();
        }
        distractorsInSolutionRef.current = new Set();
        distractorPlacedAtRef.current = new Map();

        // ── Autosave + research trail ───────────────────────────────────────
        // parsons.js funnels every mutation through addLogEntry -- drag, click-to-move and
        // keyboard alike -- and updateIndent runs in the same handlers immediately before it,
        // so one wrapper catches reorders and indents both. Wrapped on the instance, shadowing
        // the prototype, exactly as getFeedback is wrapped just below.
        const PROGRESS_DEBOUNCE_MS = 1500;
        const MOVE_FLUSH_MS = 10_000;
        const MOVE_BATCH_MAX = 25;
        const MOVE_ACTIONS = new Set(["moveOutput", "addOutput", "removeOutput", "moveInput"]);

        const currentArrangement = () =>
            readArrangement(
                document.getElementById(`ul-${widget.options.sortableId}`),
                widget.options?.x_indent || 50,
            );

        const saveProgressNow = () => {
            if (progressTimerRef.current !== null) {
                window.clearTimeout(progressTimerRef.current);
                progressTimerRef.current = null;
            }
            if (!progressPendingRef.current || !task?.id) return;
            progressPendingRef.current = false;
            // Fire-and-forget, like the solved save: losing an autosave costs the student the
            // last few seconds of work, and blocking the page on it would cost them more.
            api.put(`/tasks/${task.id}/submission/progress`, currentArrangement())
                .catch(() => { /* works fine without persistence */ });
        };

        const flushMoves = () => {
            if (moveFlushTimerRef.current !== null) {
                window.clearTimeout(moveFlushTimerRef.current);
                moveFlushTimerRef.current = null;
            }
            const moves = moveBufferRef.current;
            if (moves.length === 0 || !task?.id) return;
            moveBufferRef.current = [];
            logEvent(task.id, "arrangement_changed", {
                batchStartMs: moveBatchStartRef.current,
                moves,
            }, task.sequenceId, task.tierLevel);
            moveBatchStartRef.current = 0;
        };
        flushMovesRef.current = flushMoves;

        const originalAddLogEntry = widget.addLogEntry.bind(widget);
        widget.addLogEntry = function (entry: any, isMove?: boolean) {
            const result = originalAddLogEntry(entry, isMove);
            try {
                if (!entry || !MOVE_ACTIONS.has(entry.type)) return result;

                const now = Date.now();
                if (moveBufferRef.current.length === 0) moveBatchStartRef.current = now;

                const moved = entry.target ? document.getElementById(entry.target) : null;
                moveBufferRef.current.push({
                    atMs: now - moveBatchStartRef.current,
                    action: entry.type,
                    blockText: moved?.textContent?.trim() ?? "",
                    ...currentArrangement(),
                });

                if (moveBufferRef.current.length >= MOVE_BATCH_MAX) {
                    flushMoves();
                } else if (moveFlushTimerRef.current === null) {
                    moveFlushTimerRef.current = window.setTimeout(flushMoves, MOVE_FLUSH_MS);
                }

                progressPendingRef.current = true;
                if (progressTimerRef.current !== null) window.clearTimeout(progressTimerRef.current);
                progressTimerRef.current = window.setTimeout(saveProgressNow, PROGRESS_DEBOUNCE_MS);
            } catch {
                // Recording how the student worked must never break the widget they work in.
            }
            return result;
        };

        discardProgressRef.current = () => {
            if (progressTimerRef.current !== null) {
                window.clearTimeout(progressTimerRef.current);
                progressTimerRef.current = null;
            }
            progressPendingRef.current = false;
            moveBufferRef.current = [];
            moveBatchStartRef.current = 0;
        };

        const originalGetFeedback = widget.getFeedback.bind(widget);
        widget.getFeedback = function () {
            const feedback = originalGetFeedback();
            const ul = document.getElementById(`ul-${this.options.sortableId}`);
            (this as any)._lastSuccess = !!ul?.classList.contains("correct");
            // The Parsons widget sets CSS classes on DOM elements after grading:
            // correctPosition, incorrectPosition, correctIndent, incorrectIndent
            // We read those classes to determine correctness and apply our own styling.
            // Determine which blocks are in the solution zone (target UL)
            const solutionUl = document.getElementById(`ul-${this.options.sortableId}`);
            const solutionBlockIds = new Set<string>();
            if (solutionUl) {
                solutionUl.querySelectorAll("li").forEach((li) => {
                    if (li.id) solutionBlockIds.add(li.id);
                });
            }

            const allLines = [...(this.modified_lines ?? []), ...(this.extra_lines ?? [])];
            allLines.forEach((line: any) => {
                // line.elem() returns a jQuery object — use [0] to get the raw HTMLElement
                const jqEl = line.elem ? line.elem() : null;
                const el: HTMLElement | null = jqEl ? (jqEl[0] ?? jqEl.get?.(0) ?? null) : document.getElementById(line.id);
                if (!el) return;

                // reset our custom classes
                el.classList.remove("line-correct", "line-warning", "line-incorrect");
                el.style.backgroundColor = "";

                const langRaw = task.programmingLanguage?.toLowerCase()?.trim() ?? "";
                const isPythonLang = /^py(thon)?(3)?$/i.test(langRaw);
                const isInSolution = solutionBlockIds.has(line.id);
                const hasCorrectPos = el.classList.contains("correctPosition");
                const hasIncorrectPos = el.classList.contains("incorrectPosition");
                const hasIncorrectIndent = el.classList.contains("incorrectIndent");

                // The Parsons grader only marks correctPosition when ALL blocks are
                // in order (errors.length === 0). Otherwise it only marks the WRONG
                // blocks with incorrectPosition via LIS inverse. So:
                // - correctPosition → definitely correct
                // - incorrectPosition → definitely wrong
                // - neither + in solution zone → implicitly correct (not flagged by LIS)
                // - not in solution zone → not placed yet

                if (!isInSolution) {
                    // Block still in source zone — not graded
                    line.isCorrect = false;
                } else if (hasIncorrectPos) {
                    line.isCorrect = false;
                    el.classList.add("line-incorrect");
                    el.style.backgroundColor = "rgba(255, 99, 71, 0.4)";
                } else if (hasCorrectPos && hasIncorrectIndent) {
                    // Correct position but wrong indentation
                    if (isPythonLang) {
                        line.isCorrect = false;
                        el.classList.add("line-incorrect");
                        el.style.backgroundColor = "rgba(255, 99, 71, 0.4)";
                        el.title = t("solve.indentationWarning");
                    } else {
                        line.isCorrect = true;
                        el.classList.add("line-warning");
                        el.style.backgroundColor = "rgba(255, 215, 0, 0.4)";
                        el.title = t("solve.positionCorrectIndentWrong");
                    }
                } else if (hasCorrectPos) {
                    // ✅ Explicitly correct (all blocks in order, indent OK)
                    line.isCorrect = true;
                    el.classList.add("line-correct");
                    el.style.backgroundColor = "rgba(144, 238, 144, 0.4)";
                } else {
                    // In solution zone, not marked incorrect → implicitly correct position
                    // (LIS only marks the minimum set to move, rest is implicitly fine)
                    line.isCorrect = true;
                    el.classList.add("line-correct");
                    el.style.backgroundColor = "rgba(144, 238, 144, 0.4)";
                }
            });

            // AP30: evaluate fill-in-the-gap inputs. A solution line is only fully correct
            // when its position/indent AND all its gap values are correct. A wrong (or empty)
            // required gap must ALSO veto the overall success verdict below — otherwise a
            // perfectly ordered puzzle reports "solved" while a gap value is still wrong.
            let anyGapIncorrect = false;
            (this.modified_lines ?? []).forEach((line: any) => {
                const blanks = line._blanks ?? [];
                if (!blanks.length) return;
                const jqEl = line.elem ? line.elem() : null;
                const el: HTMLElement | null = jqEl ? (jqEl[0] ?? jqEl.get?.(0) ?? null) : null;
                // AP30 fix: window.prettyPrint() re-renders each block's innerHTML AFTER
                // _addBlanks captured its <input> nodes, so line._blanks[].el is a detached
                // node whose .value is always "". Read the CURRENT field from the live line
                // element instead (matched by order); fall back to the stored ref.
                const liveInputs = el
                    ? (Array.from(el.querySelectorAll("input.jsparson-blank")) as HTMLInputElement[])
                    : [];
                const isInSolution = solutionBlockIds.has(line.id);
                let allGapsCorrect = true;
                blanks.forEach((b: any, gi: number) => {
                    const input = (liveInputs[gi] ?? b.el) as HTMLInputElement;
                    if (!input) return;
                    const val = input.value ?? "";
                    const ok = checkBlank(val, b.expected, b.mode ?? "trim");
                    input.classList.remove("blank-correct", "blank-incorrect");
                    if (isInSolution && val.length > 0) {
                        input.classList.add(ok ? "blank-correct" : "blank-incorrect");
                    }
                    if (b.expected != null && !ok) allGapsCorrect = false;
                    if (isInSolution && val.length > 0 && task?.id) {
                        logEvent(task.id, ok ? "gap_correct" : "gap_incorrect", {
                            lineId: line.id,
                            gapIndex: gi,
                            value: val,
                            expected: b.expected,
                            mode: b.mode ?? "trim",
                        }, task.sequenceId, task.tierLevel);
                    }
                });
                if (isInSolution && !allGapsCorrect) {
                    anyGapIncorrect = true;
                    line.isCorrect = false;
                    if (el) {
                        el.classList.remove("line-correct", "line-warning");
                        el.classList.add("line-incorrect");
                        el.style.backgroundColor = "rgba(255, 99, 71, 0.4)";
                    }
                }
            });

            // AP31: evaluate inline-split lines. A split line is fully correct only when its
            // block position AND its fragment order are correct ("UND"-Urteil). Mirrors gaps.
            let anySplitIncorrect = false;
            (this.modified_lines ?? []).forEach((line: any) => {
                if (!line?.isSplitLine || !line.isSplitLine()) return;
                const jqEl = line.elem ? line.elem() : null;
                const el: HTMLElement | null = jqEl ? (jqEl[0] ?? jqEl.get?.(0) ?? null) : null;
                const isInSolution = solutionBlockIds.has(line.id);
                const order = line._split.currentOrder();
                const canonical = line._split.canonical as string[];
                const ok = line.splitCorrect();

                if (el) {
                    const slotEls = el.querySelectorAll(".split-slot");
                    slotEls.forEach((s) => s.classList.remove("split-correct", "split-incorrect"));
                    if (isInSolution) {
                        order.forEach((o: any, i: number) => {
                            if (!o) return;
                            const good = !o.distractor && o.text === canonical[i];
                            slotEls[i]?.classList.add(good ? "split-correct" : "split-incorrect");
                        });
                    }
                }

                if (isInSolution && !ok) {
                    anySplitIncorrect = true;
                    line.isCorrect = false;
                    if (el) {
                        el.classList.remove("line-correct", "line-warning");
                        el.classList.add("line-incorrect");
                        el.style.backgroundColor = "rgba(255, 99, 71, 0.4)";
                    }
                }
                if (isInSolution && task?.id) {
                    logEvent(task.id, ok ? "split_line_correct" : "split_line_incorrect", {
                        lineId: line.id,
                        order: order.map((o: any) => (o ? o.text : null)),
                        expected: canonical,
                    }, task.sequenceId, task.tierLevel);
                }
            });

            // AP30/AP31: a wrong gap OR a wrong split vetoes the overall "solved" verdict.
            // _lastSuccess (read by the Feedback handler) is otherwise position-only, and
            // js-parsons' own `correct` class on the solution list must be retracted too.
            if (anyGapIncorrect || anySplitIncorrect) {
                (this as any)._lastSuccess = false;
                ul?.classList.remove("correct");
            }

            return feedback;
        };

        // AP30 fix: clear a gap's stale correct/incorrect styling the moment the learner
        // edits it, so a corrected field responds immediately. Delegated on document because
        // window.prettyPrint() re-creates the input nodes (see gap grading above), which would
        // strand a listener bound to a specific node.
        const gapClearHandler = (e: Event) => {
            const tgt = e.target as HTMLElement | null;
            if (tgt && tgt.classList?.contains("jsparson-blank")) {
                tgt.classList.remove("blank-correct", "blank-incorrect");
            }
        };
        document.addEventListener("input", gapClearHandler);

        // Lock hints + track move counters whenever blocks are moved
        const targetUl = document.getElementById("ul-jsparsons-target");
        const sourceUl = document.getElementById("ul-jsparsons-source");

        const observer = new MutationObserver(() => {
            setHintsUnlocked(false);
            movesSinceFeedbackRef.current += 1;

            // Track move timing (used by repair_strategy + finer timing in feedback/correct events)
            const now = Date.now();
            moveCountRef.current += 1;
            if (firstMoveAtRef.current === 0) firstMoveAtRef.current = now;
            const timeSinceLastMove = lastMoveAtRef.current > 0 ? now - lastMoveAtRef.current : 0;
            if (lastMoveAtRef.current > 0) movePausesRef.current.push(timeSinceLastMove);
            lastMoveAtRef.current = now;

            // Log hint_viewed if hint was showing when block moved
            if (hintShownAtRef.current > 0 && task?.id) {
                const viewDuration = now - hintShownAtRef.current;
                logEvent(task.id, "hint_viewed", {
                    hintLevel: lastHintLevelRef.current,
                    viewDurationMs: viewDuration,
                    dismissedBy: "block_moved",
                }, task.sequenceId, task.tierLevel);
                hintShownAtRef.current = 0;
            }

            // AP15 Teil 5: Distractor movement detection
            if (distractorTextsRef.current.size > 0 && task?.id) {
                const targetEl = document.getElementById("ul-jsparsons-target");
                if (!targetEl) return;

                // Scan target (solution) zone for distractor blocks
                const currentDistractorsInSolution = new Set<string>();
                targetEl.querySelectorAll("li").forEach((li) => {
                    const blockText = (li as HTMLElement).textContent?.trim() ?? "";
                    if (distractorTextsRef.current.has(blockText)) {
                        currentDistractorsInSolution.add(blockText);
                    }
                });

                const prevInSolution = distractorsInSolutionRef.current;

                // Detect newly placed distractors (entered solution zone)
                currentDistractorsInSolution.forEach((dt) => {
                    if (!prevInSolution.has(dt)) {
                        distractorPlacedAtRef.current.set(dt, now);
                        logEvent(task.id!, "distractor_placed", {
                            blockContent: dt,
                            moveNumber: moveCountRef.current,
                            distractorCountInSolution: currentDistractorsInSolution.size,
                            totalDistractors: distractorTextsRef.current.size,
                        }, task.sequenceId, task.tierLevel);
                    }
                });

                // Detect removed distractors (left solution zone)
                prevInSolution.forEach((dt) => {
                    if (!currentDistractorsInSolution.has(dt)) {
                        const placedAt = distractorPlacedAtRef.current.get(dt);
                        const wasInSolutionMs = placedAt ? now - placedAt : 0;
                        distractorPlacedAtRef.current.delete(dt);
                        logEvent(task.id!, "distractor_removed", {
                            blockContent: dt,
                            wasInSolutionMs,
                            moveNumber: moveCountRef.current,
                            removedBeforeFeedback: !lastFeedbackBeforeRemovalRef.current,
                            removedAfterHint: lastHintBeforeRemovalRef.current,
                            distractorCountInSolution: currentDistractorsInSolution.size,
                            totalDistractors: distractorTextsRef.current.size,
                        }, task.sequenceId, task.tierLevel);
                    }
                });

                distractorsInSolutionRef.current = currentDistractorsInSolution;
            }
        });
        const observerOpts = { childList: true };
        if (targetUl) observer.observe(targetUl, observerOpts);
        if (sourceUl) observer.observe(sourceUl, observerOpts);

        // AP31: render inline-split lines AFTER prettify finishes so the rebuilt DOM and its
        // click handlers survive (prettify re-tokenizes .prettyprint blocks — see gap note above).
        const wireSplitLines = () => {
            const plan = parseSplitPlan(task.splitPlan ?? null);
            if (!plan?.splits?.length) return;
            plan.splits.forEach((entry) => {
                const reconstructed = ((entry.prefix ?? "") + (entry.fragments ?? []).join("") + (entry.suffix ?? "")).trim();
                const line = (widget.modified_lines ?? []).find((l: any) =>
                    l && !l._split && typeof l.code === "string" && l.code.trim() === reconstructed);
                if (line && typeof line._renderSplit === "function") {
                    // AP31: per-line handler so we can also detect when a split-distractor
                    // (a chip with data-distractor="1") is dragged into a slot.
                    let prevDistractorsInSlots = 0;
                    const onSplitChange = () => {
                        setHintsUnlocked(false);
                        if (!task?.id) return;
                        logEvent(task.id, "fragment_placed", {}, task.sequenceId, task.tierLevel);
                        try {
                            const order = line._split?.currentOrder?.() ?? [];
                            const distractorsInSlots = order.filter((s: any) => s && s.distractor).length;
                            if (distractorsInSlots > prevDistractorsInSlots) {
                                logEvent(task.id, "split_distractor_placed", {}, task.sequenceId, task.tierLevel);
                            }
                            prevDistractorsInSlots = distractorsInSlots;
                        } catch { /* non-fatal analytics */ }
                    };
                    line._renderSplit(entry, onSplitChange);
                }
            });
        };
        if (window.prettyPrint) {
            window.prettyPrint(wireSplitLines);
        } else {
            wireSplitLines();
        }

        return () => {
            // Flush before the timers die, or the last edit before leaving the page is exactly
            // the one that is lost. In-app navigation runs this synchronously and the requests
            // complete; a real tab close may kill them, which is a known limit shared with the
            // task_closed event and not something beforeunload would fix.
            saveProgressNow();
            flushMoves();
            if (moveFlushTimerRef.current !== null) {
                window.clearTimeout(moveFlushTimerRef.current);
                moveFlushTimerRef.current = null;
            }
            discardProgressRef.current = null;
            flushMovesRef.current = null;
            observer.disconnect();
            document.removeEventListener("input", gapClearHandler);
            widgetRef.current = null;
        };
    }, [task?.solution]);

    // ── AP17: Apply interaction mode when widget or mode changes ──
    useEffect(() => {
        // Small timeout to ensure widget init effect has completed first
        const timer = setTimeout(() => {
            const widget = widgetRef.current;
            if (!widget) return;
            try {
                if (interactionMode === "click") {
                    widget.enableClickToMove();
                } else {
                    if (widget._clickToMoveState) widget.disableClickToMove();
                }
            } catch (e) {
                console.warn("Click-to-move toggle error:", e);
            }
        }, 50);
        return () => clearTimeout(timer);
    }, [interactionMode, task?.solution]);

    // ── AP13: Hint system functions ──
    function clearHintHighlights(reason?: "block_moved" | "feedback_clicked" | "new_hint") {
        // Log hint_viewed if a hint was showing
        if (hintShownAtRef.current > 0 && task?.id) {
            const viewDuration = Date.now() - hintShownAtRef.current;
            logEvent(task.id, "hint_viewed", {
                hintLevel: lastHintLevelRef.current,
                viewDurationMs: viewDuration,
                dismissedBy: reason || "unknown",
            }, task.sequenceId, task.tierLevel);
            hintShownAtRef.current = 0;
        }
        document.querySelectorAll(".block-hint-error").forEach((el) => {
            el.classList.remove("block-hint-error");
            el.removeAttribute("data-hint-label");
        });
    }

    function applyHintHighlights(affectedBlockIndices: number[]) {
        clearHintHighlights("new_hint");
        const widget = widgetRef.current;
        if (!widget) return;

        const sortableUl = document.getElementById(`ul-${widget.options.sortableId}`);
        if (!sortableUl) return;

        applyHintLabels(sortableUl, affectedBlockIndices);
    }

    async function requestHint(hintLevel: 1 | 2) {
        if (!task?.id) return;
        const widget = widgetRef.current;
        if (!widget) return;

        // Same reason the Feedback handler flushes: a hint is generated from the arrangement as
        // it stands right now, and hint_usage stores that arrangement server-side. If the moves
        // that built it are still buffered, the trail shows the hint being asked for on a state
        // the student had already moved past -- and the moves that produced it arriving
        // afterwards, as though they were a reaction to the hint rather than the setup for it.
        //
        // Flushed before the request, not just before the log line, so the record holds even if
        // the hint call fails or is refused for want of credits.
        flushMovesRef.current?.();

        setHintLoading(true);
        setHintError(null);
        setHintText(null);
        setHintExplanation(null);
        clearHintHighlights("new_hint");

        try {
            // Get current block order + indentation from DOM
            const sortableUl = document.getElementById(`ul-${widget.options.sortableId}`);
            const domBlocks = sortableUl
                ? Array.from(sortableUl.querySelectorAll("li"))
                : [];
            const blockOrder = domBlocks.map(
                (li) => (li as HTMLElement).textContent?.trim() ?? ""
            );
            const { indentLevels } = readArrangement(sortableUl, widget.options?.x_indent || 50);

            if (blockOrder.length === 0) {
                setHintError(t("solve.hintDragFirst"));
                return;
            }

            const response = await api.post(`/hints/${task.id}`, {
                blockOrder,
                indentLevels,
                hintLevel,
                // Hint text is written server-side, so the backend has to be told
                // which language the UI is currently in.
                locale: i18n.language,
            });

            const data = response.data;

            if (data.hintText) setHintText(data.hintText);
            if (data.explanation) setHintExplanation(data.explanation);

            if (data.affectedBlocks && data.affectedBlocks.length > 0) {
                setHintAffectedBlocks(data.affectedBlocks);
                applyHintHighlights(data.affectedBlocks);
            }

            // Track hint display timing + correctCount for post_hint_action
            hintShownAtRef.current = Date.now();
            lastHintLevelRef.current = hintLevel;
            hintWasActiveRef.current = true;
            // Capture current correctCount (from last feedback) for comparison
            correctCountBeforeHintRef.current = previousCorrectCount;

            // Log hint event
            logEvent(task.id, hintLevel === 1 ? "hint_requested" : "hint_explanation_requested", {
                hintLevel,
                feedbackCountBeforeHint: feedbackCountRef.current,
                attemptCountBeforeHint: attemptCountRef.current,
                findingsCount: data.findings?.length ?? 0,
                affectedBlocks: data.affectedBlocks,
            }, task.sequenceId, task.tierLevel);

            // The hint was charged server-side; re-read the budget rather than
            // guessing the cost here, so the two can never drift apart.
            refreshHintConfig();

        } catch (err: any) {
            // A 403 here means the server refused (hints disabled, or no credits
            // left). Re-read the config so the buttons reflect that immediately.
            if (err?.response?.status === 403) refreshHintConfig();
            const msg = err?.response?.data?.hintText || err?.response?.data?.message || err?.message || t("solve.hintLoadFailed");
            setHintError(msg);
        } finally {
            setHintLoading(false);
        }
    }

    // Level 2 costs two credits, level 1 costs one; a null budget is unlimited.
    const hintCost = (level: 1 | 2) => (level === 2 ? 2 : 1);
    const canAfford = (level: 1 | 2) =>
        creditsRemaining === null || creditsRemaining >= hintCost(level);

    /** Tooltip for a hint button, explaining whichever thing is blocking it. */
    const hintTooltip = (level: 1 | 2, enabledTitle: string) => {
        // Credits first: running out is permanent for this task, while "click
        // Feedback first" is a step the student can still take. Reporting the
        // transient blocker would send them off to do something that cannot help.
        if (!canAfford(level)) {
            return creditsRemaining === 0
                ? t("solve.creditsExhausted")
                : t("solve.creditsInsufficient", { cost: hintCost(level), remaining: creditsRemaining });
        }
        if (!hintsUnlocked) return t("solve.feedbackFirst");
        return enabledTitle;
    };

    if (!taskId) {
        return (
            <div className="container py-4">
                <div className="alert alert-warning">{t("solve.noTaskId")}</div>
                <button className="btn btn-link" onClick={() => navigate(homePath)}>
                    ← {t("solve.home")}
                </button>
            </div>
        );
    }

    return (

        <div className="container-fluid py-4" style={{maxWidth: "90%"}}>
            <div className="mb-3 d-flex gap-2">
                <button className="btn btn-link" onClick={() => navigate(homePath)}>
                    ← {t("solve.home")}
                </button>
                <button
                    className="btn btn-link"
                    onClick={() => {
                        // A task reached without a sequence has nothing to go back
                        // to, so fall through to wherever Home would lead.
                        if (!sequenceId) return navigate(homePath);

                        navigate(`/sequence/${sequenceId}`, {
                            state: {
                                fromWorksheet: location.state?.fromWorksheet,
                                fromClass: location.state?.fromClass,
                            },
                        });
                    }}
                    disabled={!sequenceId}
                >
                    ← {t("solve.backToSequence")}
                </button>
            </div>
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
                    👩‍🏫 {t("solve.teacherBanner")}
                </div>
            )}

            {loading && (
                <div className="alert alert-info d-flex align-items-center gap-2">
                    <span className="spinner-border spinner-border-sm" role="status" aria-hidden="true"/>
                    <div>{t("solve.loadingTask")}</div>
                </div>
            )}
            {error && (
                <div className="alert alert-danger d-flex align-items-start gap-2">
                    <span>⚠️</span>
                    <div><strong>{t("solve.errorLabel")}</strong> {error}</div>
                </div>
            )}
            <button
                className={`btn btn-sm d-flex align-items-center gap-1 ms-auto ${
                    copied ? "btn-success" : "btn-outline-secondary"
                }`}
                onClick={handleCopyLink}
                title={t("solve.copyLinkTitle")}
            >
                <i className="bi bi-link-45deg"></i>
                {copied ? `✓ ${t("solve.copied")}` : t("solve.shareTask")}
            </button>
            {task && (
                <>
                    <div className="card mb-3">
                        <div className="card-body">
                            <b>{t("solve.taskLabel")}</b>
                            <div className="mt-2" style={{whiteSpace: "pre-wrap"}}>{task.statement}</div>
                            <div className="text-muted mt-2">
                                {t("solve.tierAndLanguage", { name: taskName(task, t), language: task.programmingLanguage })}
                            </div>
                            <div className="mt-2"></div>
                        </div>
                    </div>

                    {/* AP22: Context mode info banner */}
                    {(contextBefore.length > 0 || contextAfter.length > 0) && (
                        <div className="alert alert-info d-flex align-items-center gap-2 mb-3 py-2" role="alert">
                            <span style={{ fontSize: "1.2rem" }}>&#x1F4D6;</span>
                            <span>{t("solve.contextBanner")}</span>
                        </div>
                    )}

                    {!canSolve && (
                        <div className="alert alert-secondary d-flex align-items-center gap-2 mb-3 py-2" role="alert">
                            <span style={{ fontSize: "1.2rem" }}>&#x1F512;</span>
                            <span>{t("solve.closedBanner")}</span>
                        </div>
                    )}

                    {/* AP15: Distractor info banner */}
                    {task.distractors && (
                        <div className="alert alert-warning d-flex align-items-center gap-2 mb-3 py-2" role="alert">
                            <span style={{ fontSize: "1.2rem" }}>&#x26A0;&#xFE0F;</span>
                            <span>{t("solve.distractorBanner")}</span>
                        </div>
                    )}

                    <div
                        className="d-flex flex-wrap justify-content-between align-items-stretch"
                        style={{
                            marginTop: "1rem",
                            gap: "1.5rem",
                        }}
                    >
                        <div
                            style={{
                                flex: "1 1 min(480px, 100%)",
                                minWidth: 0,
                                display: "flex",
                                flexDirection: "column",
                                fontSize: "0.9rem",
                            }}
                        >
                            <h6 className="fw-bold mb-2">{t("solve.sourceHeading")}</h6>
                            <div
                                id="jsparsons-source"
                                className="sortable-code"
                                style={{
                                    border: "1px solid #ddd",
                                    borderRadius: "8px",
                                    backgroundColor: "#fafafa",
                                    padding: "0.75rem",
                                    minHeight: "320px",
                                    overflowY: "auto",
                                    overflowX: "hidden",
                                    width: "100%",
                                    boxSizing: "border-box",
                                    position: "static",
                                }}
                            />
                        </div>

                        <div
                            style={{
                                flex: "1 1 min(480px, 100%)",
                                minWidth: 0,
                                display: "flex",
                                flexDirection: "column",
                                fontSize: "0.9rem",
                            }}
                        >
                            <h6 className="fw-bold mb-2">{t("solve.targetHeading")}</h6>

                            {/* AP22: Context before puzzle */}
                            {contextBefore.length > 0 && (
                                <div className="context-block" style={{
                                    background: "#f8f9fa",
                                    borderLeft: "3px solid #6c757d",
                                    borderRadius: "0 8px 0 0",
                                    padding: "0.7rem 1rem",
                                    fontFamily: "'Courier New', Consolas, monospace",
                                    fontSize: "0.9rem",
                                    lineHeight: "1.55",
                                    color: "#495057",
                                    whiteSpace: "pre",
                                    userSelect: "none",
                                    overflowX: "auto",
                                    marginBottom: "0",
                                    borderBottom: "1px dashed #ced4da",
                                }}>
                                    <div style={{ fontSize: "0.65rem", color: "#6c757d", textTransform: "uppercase", letterSpacing: "0.05em", marginBottom: "0.35rem", fontFamily: "system-ui, sans-serif", fontWeight: 600 }}>
                                        &#128274; {t("solve.contextLabel")}
                                    </div>
                                    {contextBefore.join("\n")}
                                </div>
                            )}

                            <div
                                id="jsparsons-target"
                                className="sortable-code"
                                style={{
                                    border: "1px solid #ddd",
                                    borderRadius: "10px",
                                    backgroundColor: "#fafafa",
                                    padding: "1rem",
                                    minHeight: contextBefore.length > 0 || contextAfter.length > 0 ? "250px" : "450px",
                                    overflowY: "auto",
                                    overflowX: "hidden",
                                    width: "100%",
                                    boxSizing: "border-box",
                                    position: "static",
                                    whiteSpace: "pre-wrap",
                                    wordWrap: "break-word",
                                }}
                            />

                            {/* AP22: Context after puzzle */}
                            {contextAfter.length > 0 && (
                                <div className="context-block" style={{
                                    background: "#f8f9fa",
                                    borderLeft: "3px solid #6c757d",
                                    borderRadius: "0 0 0 0",
                                    padding: "0.7rem 1rem",
                                    fontFamily: "'Courier New', Consolas, monospace",
                                    fontSize: "0.9rem",
                                    lineHeight: "1.55",
                                    color: "#495057",
                                    whiteSpace: "pre",
                                    userSelect: "none",
                                    overflowX: "auto",
                                    marginTop: "0",
                                    borderTop: "1px dashed #ced4da",
                                }}>
                                    <div style={{ fontSize: "0.65rem", color: "#6c757d", textTransform: "uppercase", letterSpacing: "0.05em", marginBottom: "0.35rem", fontFamily: "system-ui, sans-serif", fontWeight: 600 }}>
                                        &#128274; {t("solve.contextLabel")}
                                    </div>
                                    {contextAfter.join("\n")}
                                </div>
                            )}
                        </div>
                    </div>


                    <div className="mt-3 d-flex gap-2 flex-wrap align-items-center">
                        {/* AP17: Interaction mode toggle */}
                        <div className="btn-group btn-group-sm me-2" role="group" aria-label={t("solve.interactionMode")}>
                            <button
                                type="button"
                                className={`btn ${interactionMode === "drag" ? "btn-primary" : "btn-outline-secondary"}`}
                                onClick={() => {
                                    setInteractionMode("drag");
                                    try { localStorage.setItem("codeowl_interaction_mode", "drag"); } catch {}
                                }}
                                title={t("solve.dragModeTitle")}
                            >
                                &#x2725; {t("solve.dragMode")}
                            </button>
                            <button
                                type="button"
                                className={`btn ${interactionMode === "click" ? "btn-primary" : "btn-outline-secondary"}`}
                                onClick={() => {
                                    setInteractionMode("click");
                                    try { localStorage.setItem("codeowl_interaction_mode", "click"); } catch {}
                                }}
                                title={t("solve.clickModeTitle")}
                            >
                                &#x1F446; {t("solve.clickMode")}
                            </button>
                        </div>
                        <button
                            className="btn btn-outline-secondary"
                            disabled={!canSolve}
                            onClick={(e) => {
                                e.preventDefault();

                                // Only the blocks still in the source area are
                                // reordered; the solution the student has built
                                // stays exactly as it is.
                                const sourceUl = document.getElementById("ul-jsparsons-source");
                                const moved = shuffleElementChildren(sourceUl);
                                if (moved === 0) return;

                                // jQuery UI caches each item's position for dragging,
                                // so it has to recompute them after the nodes move.
                                try { ($(sourceUl as HTMLElement) as any).sortable("refresh"); } catch { /* not sortable */ }

                                // The nodes themselves are reused, so click-to-move
                                // handlers survive; re-binding is cheap insurance
                                // against any selection state pointing at a moved node.
                                if (interactionMode === "click" && widgetRef.current) {
                                    try {
                                        widgetRef.current.disableClickToMove();
                                        widgetRef.current.enableClickToMove();
                                    } catch {}
                                }

                                // Feedback and hints are deliberately left alone: they
                                // describe the solution area, which this no longer
                                // touches, so clearing them would discard information
                                // that is still accurate.

                                try {
                                    if (task?.id) {
                                        // Not task_reset any more — nothing is reset.
                                        logEvent(task.id, "source_shuffled", {
                                            blocksShuffled: moved,
                                            attemptCount: attemptCountRef.current,
                                            feedbackCount: feedbackCountRef.current,
                                            activeTimeMs: activeTimeRef.current,
                                            totalTimeMs: Date.now() - startTimeRef.current,
                                        }, task.sequenceId, task.tierLevel);
                                    }
                                } catch { /* silent */ }
                            }}
                        >
                            {t("solve.shuffleLines")}
                        </button>

                        {/*
                          * Offered only while unfinished work has been restored. Resuming is
                          * silent, so this is the way back out for a student who would rather
                          * start clean than undo their way there -- and it throws the stored
                          * arrangement away, or the next visit would simply restore it again.
                          */}
                        {hasSavedProgress && !solvedPreviously && canSolve && (
                            <button
                                className="btn btn-outline-secondary"
                                onClick={(e) => {
                                    e.preventDefault();
                                    const widget = widgetRef.current;
                                    if (!widget) return;

                                    // Drop the pending autosave first: it would otherwise fire
                                    // after the delete and store the arrangement right back.
                                    discardProgressRef.current?.();

                                    widget.shuffleLines();
                                    setHasSavedProgress(false);
                                    restoredRef.current = false;
                                    isCompleteRef.current = false;
                                    setSolutionLocked(false);
                                    setFeedbackHTML("");
                                    clearHintHighlights("new_hint");
                                    setHintText(null);
                                    setHintExplanation(null);
                                    setHintError(null);
                                    startTimeRef.current = Date.now();
                                    attemptCountRef.current = 0;

                                    if (interactionMode === "click") {
                                        try {
                                            widget.disableClickToMove();
                                            widget.enableClickToMove();
                                        } catch { /* not enabled */ }
                                    }

                                    if (task?.id) {
                                        api.delete(`/tasks/${task.id}/submission`)
                                            .catch(() => { /* nothing stored, or not logged in */ });
                                    }
                                }}
                            >
                                {t("solve.startOver")}
                            </button>
                        )}

                        {/*
                          * Offered only once the task has been solved, since that is
                          * the only time the puzzle comes back already filled in.
                          * The stored solution is deliberately left in place: an
                          * abandoned redo should not cost the student their work.
                          */}
                        {solvedPreviously && canSolve && (
                            <button
                                className="btn btn-outline-primary"
                                onClick={(e) => {
                                    e.preventDefault();
                                    const widget = widgetRef.current;
                                    if (!widget) return;

                                    // Deal the task out again from scratch.
                                    widget.shuffleLines();
                                    isCompleteRef.current = false;
                                    setSolutionLocked(false);
                                    setShowCompletionBanner(false);
                                    setShowSelfReport(false);
                                    setFeedbackHTML("");
                                    clearHintHighlights("new_hint");
                                    setHintText(null);
                                    setHintExplanation(null);
                                    setHintError(null);
                                    setHintsUnlocked(false);
                                    startTimeRef.current = Date.now();
                                    attemptCountRef.current = 0;

                                    if (interactionMode === "click") {
                                        try {
                                            widget.disableClickToMove();
                                            widget.enableClickToMove();
                                        } catch { /* not enabled */ }
                                    }

                                    try {
                                        if (task?.id) {
                                            logEvent(task.id, "task_redo_started", {
                                                previouslySolvedAt: solvedAtRef.current,
                                                hintsUsedBefore: creditsPerTask !== null && creditsRemaining !== null
                                                    ? creditsPerTask - creditsRemaining
                                                    : null,
                                            }, task.sequenceId, task.tierLevel);
                                        }
                                    } catch { /* silent */ }
                                }}
                            >
                                {t("solve.redo")}
                            </button>
                        )}

                        {/*
                          * The title lives on a wrapper, not on the button. A disabled control
                          * receives no pointer events, so its own tooltip never fires -- and
                          * the hover does not reach the parent either while the button is
                          * still in the way, which is what pointerEvents: none clears. This is
                          * Bootstrap's own advice for tooltips on disabled buttons, done with
                          * a plain title because that is what the rest of the app uses.
                          */}
                        <span
                            className="d-inline-block"
                            title={solutionLocked ? (t("solve.alreadySolvedHint") as string) : undefined}
                        >
                        <button
                            className="btn btn-primary"
                            disabled={!canSolve || solutionLocked}
                            style={solutionLocked ? { pointerEvents: "none" } : undefined}
                            onClick={(e) => {
                                e.preventDefault();
                                const widget = widgetRef.current;
                                if (!widget) return;

                                // Clear any hint highlights before grading
                                clearHintHighlights("feedback_clicked");
                                setHintText(null);
                                setHintExplanation(null);
                                setHintError(null);

                                // Run js-parsons grading. getFeedback returns an
                                // array of messages for this grader, never a bare
                                // string — testing for a string dropped every
                                // message on the floor.
                                const fb = widget.getFeedback?.();
                                setFeedbackHTML(formatGraderFeedback(fb));

                                // Unlock hints after feedback
                                setHintsUnlocked(true);

                                // AP15: Mark that feedback was given (for distractor_removed tracking)
                                lastFeedbackBeforeRemovalRef.current = true;
                                lastHintBeforeRemovalRef.current = lastHintLevelRef.current > 0;

                                // Determine if all lines are correct
                                const allCorrect =
                                    widgetRef.current?._lastSuccess ??
                                    document.getElementById(`ul-${widget.options.sortableId}`)
                                        ?.classList.contains("correct") ?? false;

                                // ── Bug 1 fix: Read ACTUAL block order from the DOM ──
                                // The Parsons widget renders blocks as <li> inside the
                                // sortable UL. We read text + data-id directly from DOM
                                // to get the current user arrangement.
                                const sortableUl = document.getElementById(`ul-${widget.options.sortableId}`);
                                const domBlocks = sortableUl
                                    ? Array.from(sortableUl.querySelectorAll("li"))
                                    : [];
                                const blockOrder: string[] = domBlocks.map(
                                    (li) => (li as HTMLElement).textContent?.trim() ?? ""
                                );
                                const totalBlocks = domBlocks.length;

                                // Count correct blocks from modified_lines (after grading sets isCorrect)
                                const codeLines = [...(widget.modified_lines ?? []), ...(widget.extra_lines ?? [])] as any[];
                                let currentCorrectCount = 0;
                                const currentCorrectIds = new Set<string>();

                                codeLines.forEach((line: any) => {
                                    if (line.isCorrect) {
                                        currentCorrectCount++;
                                        if (line.id) currentCorrectIds.add(line.id);
                                    }
                                    // Backtracking detection (AP11): previously correct block now wrong
                                    if (line.id && correctBlocksRef.current.has(line.id) && !line.isCorrect) {
                                        backtrackCountRef.current += 1;
                                        setBacktrackCount((prev) => prev + 1);
                                    }
                                });

                                // Update correct blocks reference for next feedback
                                correctBlocksRef.current = currentCorrectIds;

                                // Calculate improvement delta
                                const improvement = currentCorrectCount - previousCorrectCount;
                                setPreviousCorrectCount(currentCorrectCount);

                                // Update counters
                                attemptCountRef.current += 1;
                                setAttemptCount((prev) => prev + 1);
                                feedbackCountRef.current += 1;
                                const newFeedbackCount = feedbackCountRef.current;
                                setFeedbackCount(newFeedbackCount);

                                // Write the buffered moves out before any verdict is logged.
                                //
                                // The buffer is flushed on a 10s timer, at 25 moves, or when the
                                // page is left -- none of which is "the student asked whether
                                // they are done". Without this the moves that produced the
                                // solution were still sitting in the buffer when task_correct
                                // went out, so the trail read: an arrangement_changed ending on
                                // a wrong arrangement, then task_correct, then an
                                // arrangement_changed arriving up to ten seconds *after* the
                                // task was solved, as if the student had kept working. They had
                                // not; the batch had simply not been sent yet.
                                //
                                // Flushing here keeps the order causal: every move that led to
                                // a verdict is recorded before the verdict it led to.
                                flushMovesRef.current?.();

                                // Time since last feedback (AP11)
                                const now = Date.now();
                                const timeSinceLastFeedbackMs = lastFeedbackAtRef.current > 0
                                    ? now - lastFeedbackAtRef.current
                                    : now - startTimeRef.current;
                                lastFeedbackAtRef.current = now;

                                // ── Bug 2 fix: Do NOT log feedback_requested when correct ──
                                // When 100% correct, only log task_correct (the definitive
                                // completion event). feedback_requested = intermediate only.
                                if (allCorrect && task?.id) {
                                    isCompleteRef.current = true;
                                    const pauses = movePausesRef.current;
                                    const meanPause = pauses.length > 0 ? pauses.reduce((a, b) => a + b, 0) / pauses.length : 0;
                                    logEvent(task.id, "task_correct", {
                                        totalAttempts: attemptCountRef.current,
                                        totalFeedbackRequests: newFeedbackCount,
                                        activeTimeMs: activeTimeRef.current,
                                        totalTimeMs: now - startTimeRef.current,
                                        backtrackCount: backtrackCountRef.current,
                                        blockOrder,
                                        totalBlocks,
                                        correctCount: currentCorrectCount,
                                        // AP14: finer timing
                                        latencyToFirstDragMs: firstMoveAtRef.current > 0 ? firstMoveAtRef.current - startTimeRef.current : null,
                                        meanPauseBetweenMovesMs: Math.round(meanPause),
                                        totalMoves: moveCountRef.current,
                                        // AP15: distractor info
                                        distractorCountInSolution: distractorsInSolutionRef.current.size,
                                        totalDistractors: distractorTextsRef.current.size,
                                    }, task.sequenceId, task.tierLevel);
                                } else if (task?.id) {
                                    // Intermediate attempt — log feedback_requested with full data
                                    const pausesFb = movePausesRef.current;
                                    const meanPauseFb = pausesFb.length > 0 ? pausesFb.reduce((a, b) => a + b, 0) / pausesFb.length : 0;
                                    logEvent(task.id, "feedback_requested", {
                                        correct: false,
                                        attemptNumber: attemptCountRef.current,
                                        feedbackCount: newFeedbackCount,
                                        correctCount: currentCorrectCount,
                                        totalBlocks,
                                        blockOrder,
                                        improvement,
                                        backtrackCount: backtrackCountRef.current,
                                        timeSinceLastFeedbackMs,
                                        activeTimeMs: activeTimeRef.current,
                                        totalTimeMs: now - startTimeRef.current,
                                        // AP14: finer timing
                                        latencyToFirstDragMs: firstMoveAtRef.current > 0 ? firstMoveAtRef.current - startTimeRef.current : null,
                                        meanPauseBetweenMovesMs: Math.round(meanPauseFb),
                                        totalMoves: moveCountRef.current,
                                        // AP15: distractor info
                                        distractorCountInSolution: distractorsInSolutionRef.current.size,
                                        totalDistractors: distractorTextsRef.current.size,
                                    }, task.sequenceId, task.tierLevel);
                                }

                                // post_hint_action: was a hint active before this feedback?
                                if (hintWasActiveRef.current && task?.id) {
                                    const correctBefore = correctCountBeforeHintRef.current ?? 0;
                                    logEvent(task.id, "post_hint_action", {
                                        hintLevel: lastHintLevelRef.current,
                                        correctBefore,
                                        correctAfter: currentCorrectCount,
                                        improved: currentCorrectCount > correctBefore,
                                        delta: currentCorrectCount - correctBefore,
                                        attemptNumber: attemptCountRef.current,
                                    }, task.sequenceId, task.tierLevel);
                                    hintWasActiveRef.current = false;
                                    correctCountBeforeHintRef.current = null;
                                }

                                // AP14: stuckness + repair strategy
                                if (task?.id) {
                                    const stateHash = blockOrder.join("|");

                                    // AP14: stuckness_detected
                                    const prevStates = stateHistoryRef.current;
                                    if (improvement <= 0) {
                                        noProgressCountRef.current += 1;
                                    } else {
                                        noProgressCountRef.current = 0;
                                    }

                                    let stucknessType: string | null = null;
                                    // repeat_state: exact same arrangement as before
                                    if (prevStates.includes(stateHash)) {
                                        stucknessType = "repeat_state";
                                        // oscillation: A→B→A pattern
                                        if (prevStates.length >= 2 && prevStates[prevStates.length - 2] === stateHash) {
                                            stucknessType = "oscillation";
                                        }
                                    }
                                    // no_progress: 3+ feedbacks without improvement
                                    if (!stucknessType && noProgressCountRef.current >= 3) {
                                        stucknessType = "no_progress";
                                    }
                                    // rapid_clicking: 3+ feedbacks in < 10 seconds
                                    if (!stucknessType && timeSinceLastFeedbackMs < 3500 && newFeedbackCount >= 3) {
                                        stucknessType = "rapid_clicking";
                                    }

                                    // AP15: Distractor-specific stuckness types
                                    const distractorsCurrentlyInSolution = distractorsInSolutionRef.current.size;
                                    if (!stucknessType && distractorsCurrentlyInSolution > 0) {
                                        // distractor_loop: student keeps rearranging but distractors stay in solution
                                        if (noProgressCountRef.current >= 2 && distractorsCurrentlyInSolution > 0) {
                                            stucknessType = "distractor_loop";
                                        }
                                        // trap_blindness: 4+ feedbacks with distractors still present, no hint used
                                        if (!stucknessType && newFeedbackCount >= 4 && distractorsCurrentlyInSolution > 0
                                            && lastHintLevelRef.current === 0) {
                                            stucknessType = "trap_blindness";
                                        }
                                    }

                                    if (stucknessType) {
                                        logEvent(task.id, "stuckness_detected", {
                                            stucknessType,
                                            stateHash,
                                            repeatCount: prevStates.filter(s => s === stateHash).length + 1,
                                            feedbacksSinceProgress: noProgressCountRef.current,
                                            correctBlocks: currentCorrectCount,
                                            totalBlocks,
                                            hintUsed: hintWasActiveRef.current || lastHintLevelRef.current > 0,
                                            distractorsInSolution: distractorsCurrentlyInSolution,
                                            totalDistractors: distractorTextsRef.current.size,
                                        }, task.sequenceId, task.tierLevel);
                                    }
                                    stateHistoryRef.current.push(stateHash);

                                    // AP14: repair_strategy classification
                                    const blocksMoved = movesSinceFeedbackRef.current;
                                    let strategyType = "local_repair";

                                    // AP15: Check if distractors were removed since last feedback
                                    const prevDistractorCount = blockOrderAfterFeedbackRef.current.filter(
                                        (b) => distractorTextsRef.current.has(b)
                                    ).length;
                                    const currDistractorCount = blockOrder.filter(
                                        (b) => distractorTextsRef.current.has(b)
                                    ).length;
                                    const distractorsRemoved = prevDistractorCount - currDistractorCount;

                                    if (blocksMoved === 0) {
                                        strategyType = timeSinceLastFeedbackMs < 2000 ? "immediate_refeedback" : "no_change";
                                    } else if (distractorsRemoved > 0 && blocksMoved <= distractorsRemoved + 1) {
                                        // AP15: Student primarily removed distractors
                                        strategyType = "distractor_discard";
                                    } else if (distractorsRemoved > 0 && blocksMoved > distractorsRemoved + 1) {
                                        // AP15: Student removed distractors AND reorganized
                                        strategyType = "distractor_substitution";
                                    } else if (backtrackCountRef.current > 0 && correctUndoCountRef.current > 0) {
                                        strategyType = "undo_correct";
                                    } else if (blocksMoved > totalBlocks * 0.5) {
                                        strategyType = "global_reshuffle";
                                    } else if (blocksMoved <= 2) {
                                        strategyType = "local_repair";
                                    } else {
                                        strategyType = "targeted_fix";
                                    }

                                    logEvent(task.id, "repair_strategy", {
                                        strategyType,
                                        feedbackNumber: newFeedbackCount,
                                        blocksMoved,
                                        improvement,
                                        correctUndone: correctUndoCountRef.current,
                                        timeBetweenFeedbackAndNextFeedbackMs: timeSinceLastFeedbackMs,
                                    }, task.sequenceId, task.tierLevel);

                                    // Reset per-feedback counters
                                    movesSinceFeedbackRef.current = 0;
                                    correctUndoCountRef.current = 0;
                                    blockOrderAfterFeedbackRef.current = blockOrder;
                                }

                                // AP14: Show self-report after task_correct
                                if (allCorrect) {
                                    setShowSelfReport(true);
                                    setShowCompletionBanner(true); // AP18
                                    setSolvedPreviously(true);
                                    setSolutionLocked(true);

                                    // Keep the arrangement so reopening the task shows
                                    // their work. Fire-and-forget: failing to store it
                                    // must not take away having solved it.
                                    api.put(
                                        `/tasks/${task.id}/submission`,
                                        readArrangement(sortableUl, widget.options?.x_indent || 50),
                                    ).catch(() => { /* works fine without persistence */ });
                                }

                                // Send feedback-log to legacy endpoint (fire-and-forget)
                                if (task?.id && now - (lastFeedbackAtRef.current - timeSinceLastFeedbackMs) > 500) {
                                    api.post(`/tasks/${task.id}/feedback-log`, { correct: allCorrect })
                                        .then(() => {
                                            PersistentLogger.logWithContext(
                                                "SolvePage",
                                                "info",
                                                `Logged feedback click for task ${task.id}, correct=${allCorrect}`
                                            );
                                        })
                                        .catch((err: any) => {
                                            PersistentLogger.logWithContext(
                                                "SolvePage",
                                                "warn",
                                                `Non-blocking feedback log failed for task ${task.id}: ${err?.message}`
                                            );
                                        });
                                }
                            }}
                        >
                            {t("solve.feedback")}
                        </button>
                        </span>

                        {/* AP13: Hint buttons (only if teacher enabled hints) */}
                        {hintsEnabled && canSolve && (
                        <>
                        <HintButton
                            level={1}
                            className="btn btn-outline-info"
                            icon="💡"
                            label={t("solve.hint")}
                            tooltip={hintTooltip(1, t("solve.hintTitle"))}
                            disabled={!hintsUnlocked || hintLoading || isCompleteRef.current || !canAfford(1)}
                            loading={hintLoading}
                            onRequest={requestHint}
                        />
                        <HintButton
                            level={2}
                            className="btn btn-outline-warning"
                            icon="🧑‍🏫"
                            label={t("solve.explain")}
                            tooltip={hintTooltip(2, t("solve.explainTitle"))}
                            disabled={!hintsUnlocked || hintLoading || isCompleteRef.current || !canAfford(2)}
                            loading={hintLoading}
                            onRequest={requestHint}
                        />
                        {creditsPerTask !== null && (
                            <span
                                className={`badge align-self-center ${creditsRemaining === 0 ? "text-bg-secondary" : "text-bg-light"}`}
                                title={t("solve.creditsBadgeTitle")}
                            >
                                💡 {creditsRemaining ?? creditsPerTask}/{creditsPerTask}
                            </span>
                        )}
                        </>
                        )}
                    </div>

                    <div id="feedback" className="mt-3">
                        {feedbackHTML && (
                            <div className="alert alert-info" dangerouslySetInnerHTML={{__html: DOMPurify.sanitize(feedbackHTML)}}/>
                        )}
                    </div>

                    {/* AP13: Hint display area */}
                    {hintError && (
                        <div className="alert alert-danger mt-2">
                            <strong>{t("solve.hintErrorLabel")}</strong> {hintError}
                        </div>
                    )}
                    {hintText && (
                        <div className="alert alert-info mt-2" style={{ borderLeft: "4px solid #0dcaf0" }}>
                            <div className="d-flex align-items-start gap-2">
                                <span style={{ fontSize: "1.5rem" }}>💡</span>
                                <div style={{ whiteSpace: "pre-wrap" }}>{hintText}</div>
                            </div>
                        </div>
                    )}
                    {hintExplanation && (
                        <div className="alert alert-warning mt-2" style={{ borderLeft: "4px solid #ffc107" }}>
                            <div className="d-flex align-items-start gap-2">
                                <span style={{ fontSize: "1.5rem" }}>🧑‍🏫</span>
                                <div>
                                    <strong>{t("solve.explanationLabel")}</strong>
                                    <div className="mt-1" style={{ whiteSpace: "pre-wrap" }}>{hintExplanation}</div>
                                </div>
                            </div>
                        </div>
                    )}

                    {/* AP18: Combined Completion Banner + Self-Report */}
                    {showCompletionBanner && (
                        <div
                            className="mt-4 p-4 text-center rounded-3"
                            style={{
                                background: "linear-gradient(135deg, #d4edda 0%, #c3e6cb 50%, #b1dfbb 100%)",
                                border: "2px solid #28a745",
                                boxShadow: "0 4px 15px rgba(40, 167, 69, 0.3)",
                            }}
                        >
                            <div style={{ fontSize: "3rem", marginBottom: "0.5rem" }}>&#x2705;</div>
                            <h3 className="fw-bold text-success mb-2">{t("solve.solvedTitle")}</h3>
                            <p className="text-muted mb-2">
                                {attemptCountRef.current === 1
                                    ? t("solve.solvedFirstTry")
                                    : t("solve.solvedAfterAttempts", { attempts: attemptCountRef.current })}
                            </p>

                            {/* Self-report inline */}
                            {showSelfReport && (
                                <div className="mb-3">
                                    <p className="mb-2 fw-bold" style={{ fontSize: "0.95rem" }}>
                                        {t("solve.selfReportQuestion")}
                                    </p>
                                    <div className="d-flex justify-content-center gap-3">
                                        {[1, 2, 3, 4, 5].map((v) => (
                                            <button
                                                key={v}
                                                className="btn btn-outline-primary btn-lg"
                                                style={{ fontSize: "1.5rem", minWidth: 50 }}
                                                onClick={() => {
                                                    if (task?.id) {
                                                        logEvent(task.id, "self_report", {
                                                            trigger: "after_correct",
                                                            confidence: v,
                                                            totalAttempts: attemptCountRef.current,
                                                            totalTimeMs: Date.now() - startTimeRef.current,
                                                        }, task.sequenceId, task.tierLevel);
                                                    }
                                                    setShowSelfReport(false);
                                                }}
                                            >
                                                {["\uD83D\uDE1F", "\uD83D\uDE15", "\uD83D\uDE10", "\uD83D\uDE42", "\uD83D\uDE0E"][v - 1]}
                                            </button>
                                        ))}
                                    </div>
                                    <small className="text-muted mt-1 d-block">{t("solve.selfReportScale")}</small>
                                </div>
                            )}

                            <div className="d-flex justify-content-center gap-3 flex-wrap">
                                {nextTaskId && (
                                    <button
                                        className="btn btn-success btn-lg"
                                        onClick={() => {
                                            setShowCompletionBanner(false);
                                            setShowSelfReport(false);
                                            navigate(`/solve/${nextTaskId}`, { state: { lang: language } });
                                        }}
                                    >
                                        {t("solve.nextTask")} &rarr;
                                    </button>
                                )}
                                <button
                                    className="btn btn-outline-secondary"
                                    onClick={() => setShowCompletionBanner(false)}
                                >
                                    {t("solve.close")}
                                </button>
                                {task?.sequenceId && (
                                    <button
                                        className="btn btn-outline-primary"
                                        onClick={() => navigate(`/sequence/${task.sequenceId}`)}
                                    >
                                        {t("solve.toOverview")}
                                    </button>
                                )}
                            </div>
                        </div>
                    )}

                    {teacherViewEnabled && teacherToolsVisible && (
                        <div
                            style={{
                                position: "fixed",
                                right: "clamp(0.5rem, 2vw, 2rem)",
                                bottom: "clamp(0.5rem, 2vw, 2rem)",
                                backgroundColor: "#fffbea",
                                border: "1px solid #ffe08a",
                                borderRadius: "0.75rem",
                                boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
                                padding: "1rem",
                                maxWidth: "min(400px, calc(100vw - 1rem))",
                                zIndex: 1000,
                            }}
                        >
                            <div className="d-flex justify-content-between align-items-start gap-2 mb-2">
                                <h6 className="fw-bold mb-0">👩‍🏫 {t("solve.teacherTools")}</h6>
                                <button
                                    type="button"
                                    className="btn-close"
                                    aria-label={t("common.close")}
                                    style={{ flexShrink: 0 }}
                                    onClick={() => setTeacherToolsVisible(false)}
                                />
                            </div>
                            <details>
                                <summary className="fw-semibold mb-1">{t("solve.showModelSolution")}</summary>
                                <pre
                                    className="bg-light p-2 rounded mt-2"
                                    style={{ whiteSpace: "pre-wrap", fontSize: "0.85rem" }}
                                >
        {task.solution}
      </pre>
                            </details>
                        </div>
                    )}

                </>
            )}
        </div>
    );
}
