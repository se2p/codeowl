/* eslint-env browser */
import {useEffect, useMemo, useRef, useState} from "react";
import {useNavigate, useParams} from "react-router-dom";
import {api} from "../lib/api.ts";
import type {Task} from "../types/models.ts";
import {parseSplitPlan} from "../types/models.ts";
import $ from "jquery";
import {usePageTitle} from "../hooks/usePageTitle.ts";
import { useLocation } from "react-router-dom";
import {PersistentLogger} from "../PersistentLogger.ts";
import { useAuth } from "../context/AuthContext.tsx";
import { useEventTracker } from "../hooks/useEventTracker.ts";
import { useTranslation } from "react-i18next";
import DOMPurify from "dompurify";

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
    const { t } = useTranslation();
    const location = useLocation();
    const langFromNav = location.state?.lang;

    const [language, setLanguage] = useState(langFromNav ?? "unknown");
    const lastFeedbackAtRef = useRef(0);
    usePageTitle("Task Page | CodeOwl");
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

    // Fetch hint config (teacher toggle)
    useEffect(() => {
        if (!taskId) return;
        api.get(`/hints/${taskId}/config`).then(({ data }) => {
            setHintsEnabled(data.hintsEnabled ?? true);
        }).catch(() => { /* default: enabled */ });
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
                const msg = err?.response?.data?.message || err?.message || "Error during API-Request";
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
        setPreviousCorrectCount(0);
        setFeedbackHTML("");
        setHintText(null);
        setHintExplanation(null);
        setHintError(null);
        setHintsUnlocked(false);
    }, [taskId]);

    // Log task_opened on mount and task_closed on unmount (AP10)
    useEffect(() => {
        if (!taskId) return;
        startTimeRef.current = Date.now();
        activeTimeRef.current = 0;
        lastActivityRef.current = Date.now();

        logEvent(taskId, "task_opened", {}, task?.sequenceId, task?.tierLevel);

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
            handleBeforeUnload();
            window.removeEventListener("mousemove", handleActivity);
            window.removeEventListener("keydown", handleActivity);
            window.removeEventListener("beforeunload", handleBeforeUnload);
            if (activityIntervalRef.current) clearInterval(activityIntervalRef.current);
        };
    }, [taskId]);

    const widgetRef = useRef<any>(null);
    const [feedbackHTML, setFeedbackHTML] = useState("");
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

            // Fix: Initialisiere i18n direkt im Konstruktor
            i18n: {
                en: {
                    no_matching: t("solve.no_matching") || "No matching line found.",
                    one_item_error: t("solve.one_item_error") || "Solution should have more than one item.",
                    trash_label: t("solve.source") || "Available Blocks",
                    solution_label: t("solve.target") || "Your Solution",
                }
            },

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
                                .attr("title", "Indentation matters in Python — this line is incorrect.");
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
        // Fix: Zusätzliche Absicherung für die translations Property vor dem Init
        if (!widget.translations) {
            widget.translations = {
                no_matching: "No matching line found.",
                trash_label: "Available Blocks",
                solution_label: "Your Solution"
            };
        }

        widget.init(codeForWidget);
        widget.shuffleLines();
        widgetRef.current = widget;

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

        const blocks = sortableUl.querySelectorAll("li");
        const labels = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

        affectedBlockIndices.forEach((blockIdx, i) => {
            if (blockIdx >= 0 && blockIdx < blocks.length) {
                blocks[blockIdx].classList.add("block-hint-error");
                blocks[blockIdx].setAttribute("data-hint-label", labels[i] || String(i + 1));
            }
        });
    }

    async function requestHint(hintLevel: 1 | 2) {
        if (!task?.id) return;
        const widget = widgetRef.current;
        if (!widget) return;

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
            // Read indent level from CSS margin-left (x_indent default = 50px per level)
            const xIndent = widget.options?.x_indent || 50;
            const indentLevels = domBlocks.map((li) => {
                const ml = parseFloat((li as HTMLElement).style.marginLeft) || 0;
                return Math.round(ml / xIndent);
            });

            if (blockOrder.length === 0) {
                setHintError("Ziehe zuerst einige Bloecke in den Loesungsbereich.");
                return;
            }

            const response = await api.post(`/hints/${task.id}`, {
                blockOrder,
                indentLevels,
                hintLevel,
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

        } catch (err: any) {
            const msg = err?.response?.data?.hintText || err?.response?.data?.message || err?.message || "Hint konnte nicht geladen werden.";
            setHintError(msg);
        } finally {
            setHintLoading(false);
        }
    }

    if (!taskId) {
        return (
            <div className="container py-4">
                <div className="alert alert-warning">No task Id specified.</div>
                <button className="btn btn-link" onClick={() => navigate("/")}>{t("solve.home")}</button>
            </div>
        );
    }

    return (

        <div className="container-fluid py-4" style={{maxWidth: "90%"}}>
            <div className="mb-3 d-flex gap-2">
                <button
                    className="btn btn-link"
                    onClick={() => {
                        if (!sequenceId) return navigate("/");

                        navigate(`/sequence/${sequenceId}`, {
                            state: { fromWorksheet: location.state?.fromWorksheet },
                        });
                    }}
                    disabled={!sequenceId}
                >
                    ← {t("solve.backToSequence")}
                </button>
                <button className="btn btn-link" onClick={() => navigate("/")}>{t("solve.home")}</button>
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
                    👩‍🏫 Teacher Mode — Model solutions are visible on the bottom right
                </div>
            )}

            {loading && (
                <div className="alert alert-info d-flex align-items-center gap-2">
                    <span className="spinner-border spinner-border-sm" role="status" aria-hidden="true"/>
                    <div>Loading Task…</div>
                </div>
            )}
            {error && (
                <div className="alert alert-danger d-flex align-items-start gap-2">
                    <span>⚠️</span>
                    <div><strong>Error:</strong> {error}</div>
                </div>
            )}
            <button
                className={`btn btn-sm d-flex align-items-center gap-1 ms-auto ${
                    copied ? "btn-success" : "btn-outline-secondary"
                }`}
                onClick={handleCopyLink}
                title="Copies this task link"
            >
                <i className="bi bi-link-45deg"></i>
                {copied ? "✓ Copied!" : "Share Task"}
            </button>
            {task && (
                <>
                    <div className="card mb-3">
                        <div className="card-body">
                            <b>Task:</b>
                            <div className="mt-2" style={{whiteSpace: "pre-wrap"}}>{task.statement}</div>
                            <div className="text-muted mt-2">Tier {task.tierLevel} ·
                                Language {task.programmingLanguage}</div>
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
                            <h6 className="fw-bold mb-2">Source</h6>
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
                            <h6 className="fw-bold mb-2">Construct your solution here</h6>

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
                        <div className="btn-group btn-group-sm me-2" role="group" aria-label="Interaction mode">
                            <button
                                type="button"
                                className={`btn ${interactionMode === "drag" ? "btn-primary" : "btn-outline-secondary"}`}
                                onClick={() => {
                                    setInteractionMode("drag");
                                    try { localStorage.setItem("codeowl_interaction_mode", "drag"); } catch {}
                                }}
                                title="Drag and drop blocks to reorder"
                            >
                                &#x2725; Drag
                            </button>
                            <button
                                type="button"
                                className={`btn ${interactionMode === "click" ? "btn-primary" : "btn-outline-secondary"}`}
                                onClick={() => {
                                    setInteractionMode("click");
                                    try { localStorage.setItem("codeowl_interaction_mode", "click"); } catch {}
                                }}
                                title="Click a block to select, then click where to place it. Arrow keys to reorder, Tab to move between zones."
                            >
                                &#x1F446; Click
                            </button>
                        </div>
                        <button
                            className="btn btn-outline-secondary"
                            onClick={(e) => {
                                e.preventDefault();
                                widgetRef.current?.shuffleLines();
                                // Re-apply click-to-move after shuffle rebuilds DOM
                                if (interactionMode === "click" && widgetRef.current) {
                                    try {
                                        widgetRef.current.disableClickToMove();
                                        widgetRef.current.enableClickToMove();
                                    } catch {}
                                }
                                setFeedbackHTML("");
                                clearHintHighlights("feedback_clicked");
                                setHintText(null);
                                setHintExplanation(null);
                                setHintError(null);
                                // Log task_reset event (AP11)
                                try {
                                    if (task?.id) {
                                        logEvent(task.id, "task_reset", {
                                            attemptCount: attemptCountRef.current,
                                            feedbackCount: feedbackCountRef.current,
                                            activeTimeMs: activeTimeRef.current,
                                            totalTimeMs: Date.now() - startTimeRef.current,
                                        }, task.sequenceId, task.tierLevel);
                                    }
                                } catch { /* silent */ }
                            }}
                        >
                            Shuffle lines
                        </button>
                        <button
                            className="btn btn-primary"
                            onClick={(e) => {
                                e.preventDefault();
                                const widget = widgetRef.current;
                                if (!widget) return;

                                // Clear any hint highlights before grading
                                clearHintHighlights("feedback_clicked");
                                setHintText(null);
                                setHintExplanation(null);
                                setHintError(null);

                                // Run js-parsons grading
                                const fb = widget.getFeedback?.();
                                if (typeof fb === "string") setFeedbackHTML(fb);

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
                            Feedback
                        </button>

                        {/* AP13: Hint buttons (only if teacher enabled hints) */}
                        {hintsEnabled && (
                        <>
                        <button
                            className="btn btn-outline-info"
                            disabled={!hintsUnlocked || hintLoading || isCompleteRef.current}
                            onClick={async (e) => {
                                e.preventDefault();
                                await requestHint(1);
                            }}
                            title={hintsUnlocked ? "Zeigt dir, welche Bloecke problematisch sind" : "Klicke zuerst auf Feedback"}
                        >
                            {hintLoading ? (
                                <span className="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true" />
                            ) : (
                                <span className="me-1">💡</span>
                            )}
                            Hint
                        </button>
                        <button
                            className="btn btn-outline-warning"
                            disabled={!hintsUnlocked || hintLoading || isCompleteRef.current}
                            onClick={async (e) => {
                                e.preventDefault();
                                await requestHint(2);
                            }}
                            title={hintsUnlocked ? "Detaillierte Erklaerung vom Tutor" : "Klicke zuerst auf Feedback"}
                        >
                            {hintLoading ? (
                                <span className="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true" />
                            ) : (
                                <span className="me-1">🧑‍🏫</span>
                            )}
                            Erklaer es mir
                        </button>
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
                            <strong>Hint-Fehler:</strong> {hintError}
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
                                    <strong>Erklaerung:</strong>
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
                            <h3 className="fw-bold text-success mb-2">Richtig geloest!</h3>
                            <p className="text-muted mb-2">
                                {attemptCountRef.current === 1
                                    ? "Beim ersten Versuch — hervorragend!"
                                    : `In ${attemptCountRef.current} Versuchen geschafft.`}
                            </p>

                            {/* Self-report inline */}
                            {showSelfReport && (
                                <div className="mb-3">
                                    <p className="mb-2 fw-bold" style={{ fontSize: "0.95rem" }}>
                                        Wie sicher bist du, dass du es beim naechsten Mal wieder koenntest?
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
                                    <small className="text-muted mt-1 d-block">1 = unsicher · 5 = sehr sicher</small>
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
                                        Naechste Aufgabe &rarr;
                                    </button>
                                )}
                                <button
                                    className="btn btn-outline-secondary"
                                    onClick={() => setShowCompletionBanner(false)}
                                >
                                    Schliessen
                                </button>
                                {task?.sequenceId && (
                                    <button
                                        className="btn btn-outline-primary"
                                        onClick={() => navigate(`/sequence/${task.sequenceId}`)}
                                    >
                                        Zur Uebersicht
                                    </button>
                                )}
                            </div>
                        </div>
                    )}

                    {teacherViewEnabled && (
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
                            <h6 className="fw-bold mb-2">👩‍🏫 Teacher Tools</h6>
                            <details>
                                <summary className="fw-semibold mb-1">Show model solution</summary>
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
