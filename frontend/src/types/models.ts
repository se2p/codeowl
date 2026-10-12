
/**
 * AP30 (Fill-in-the-Gaps) matching mode for a gap. Gaps are encoded inline in
 * `Task.solution` as `$$blank:<expected>$$` or `$$blank:<expected>|<mode>$$`
 * (default `trim`). parsons.js renders them as inline inputs; evaluation is client-side.
 * No separate DB column — the markers live inside `solution`.
 */
export type GapMatchMode = "exact" | "trim" | "ci";

/**
 * AP31 (Inline-Split-Blocks). A split line is decomposed into 2–4 draggable fragments in
 * inline slots, with optional fixed display `prefix`/`suffix` and fragment distractors.
 * Canonical: `prefix + fragments.join("") + suffix === solution.split("\n")[lineIndex]`.
 * Stored as a JSON string in `Task.splitPlan` (parse with {@link parseSplitPlan}).
 */
export interface SplitDistractor {
    text: string;
    /** Distractor taxonomy label (AP15), e.g. "OFF_BY_ONE". */
    type?: string | null;
}

export interface SplitEntry {
    /** 0-based index into `solution.split("\n")`. */
    lineIndex: number;
    /** 2–4 fragments in canonical order (exact contiguous substrings of the line body). */
    fragments: string[];
    /** Optional fixed display prefix (not draggable). */
    prefix?: string | null;
    /** Optional fixed display suffix (not draggable). */
    suffix?: string | null;
    /** Optional fragment distractors shown in the tray. */
    distractors?: SplitDistractor[] | null;
}

export interface SplitPlan {
    splits: SplitEntry[];
}

/** Safely parse `Task.splitPlan` (a JSON string) into a {@link SplitPlan}, or null. */
export function parseSplitPlan(raw: string | null | undefined): SplitPlan | null {
    if (!raw) return null;
    try {
        const parsed = typeof raw === "string" ? JSON.parse(raw) : raw;
        if (parsed && Array.isArray(parsed.splits)) return parsed as SplitPlan;
    } catch {
        /* ignore malformed split_plan */
    }
    return null;
}

export type Task = {
    id: string;
    statement: string;
    /** Parsons solution lines. May contain inline `$$blank:…$$` gap markers (AP30). */
    solution: string;
    tierLevel: number;
    /** Name the sequence owner gave the task; null/absent means the numbered default. See taskName(). */
    title?: string | null;
    cyclomaticComplexity: number;
    sequenceId?: string;
    programmingLanguage: string;
    /** AP15: Newline-separated distractor lines (each ending with #distractor). Null when disabled. */
    distractors?: string | null;
    /** AP15: JSON string with distractor plan from sequence generation. */
    distractorPlan?: string | null;
    /** AP15: Whether distractors are active (delivered to students). Default true. */
    distractorsEnabled?: boolean;
    /** AP22: Read-only context lines displayed before the puzzle. */
    contextBefore?: string | null;
    /** AP22: Read-only context lines displayed after the puzzle. */
    contextAfter?: string | null;
    /** AP31: JSON string with the inline-split plan. Parse with `parseSplitPlan`. Null when none. */
    splitPlan?: string | null;
};

/** AP20: Generation constraints for task sequences */
export type GenerationConstraints = {
    blacklist?: string[];
    whitelist?: string[];
    difficultySlope?: string;
    additionalContext?: string;
};

export type TieredTaskSequence = {
    id: string;
    /** The name the owner gave the sequence; null shows the default heading. */
    title?: string | null;
    programmingLanguage: string;
    concepts: string[];
    tasks: Task[];
    codeSnippet?: string;
    taskDescription?: string;
    ownerId?: string | null;
    /** AP20: Constraints used during generation */
    constraints?: GenerationConstraints | null;
    /** AP21: Number of tiers (3 = default, 5 = five-tier beta mode) */
    tierCount?: number;
    /** AP19: Curriculum topic this sequence was generated from */
    curriculumTopicId?: string | null;
};

// ── AP19: Curriculum Types ─────────────────────────────

export interface CurriculumTopic {
    id: string;
    name: string;
    description: string;
    gradeLevel: number | null;
    track: string | null;
    learningArea: string | null;
    parentId: string | null;
    sortOrder: number;
    // English translations (backend/src/main/resources/db/migration/V11__curriculum_i18n.sql).
    // German is the source language; these are null for a topic added without
    // a translation, so read them through src/lib/curriculumLabels.ts.
    nameEn: string | null;
    descriptionEn: string | null;
    trackEn: string | null;
    // "SCHOOL" (Jahrgangsstufe) or "UNIVERSITY" (module), from
    // backend/src/main/resources/db/migration/V12__curriculum_education_level.sql.
    // Read it through src/lib/curriculumFilter.ts, which treats anything else as SCHOOL.
    educationLevel: string | null;
    /**
     * How many sequences match this topic's mapped concepts — the same set "View sequences"
     * filters the Library by. Only populated on sub-topics (GET /curriculum/topics/{id}/children);
     * top-level topics carry no concept mapping of their own and always read 0.
     */
    sequenceCount: number;
}

export interface CurriculumCodeExample {
    id: string;
    topicId: string;
    language: string;
    title: string;
    code: string;
    problemStatement: string;
    difficulty: string;
    notes: string | null;
    createdAt: string;
}

export interface Worksheet {
    id: string;
    name: string;
    description: string;
    /**
     * The sequences in this worksheet, as shells. `ownerId` is null for seed material nobody
     * owns; it is here so the list can mark which of them are the reader's own.
     */
    sequenceDTOS: { id: string; ownerId?: string | null; title?: string | null }[];
    ownerId: string | null;
    hintsEnabled: boolean;
    /** Hint credits each student gets per task; null means unlimited. */
    hintCreditsPerTask: number | null;
    /** How far the worksheet reaches beyond its owner. See {@link WorksheetVisibility}. */
    visibility: WorksheetVisibility;
    /** ISO-8601 instant. */
    createdAt: string | null;
    /**
     * The worksheet this one was copied from. Never shown — it exists so the backend can tell
     * a copy from an original.
     */
    copiedFrom: string | null;
    copiedAt: string | null;
}

/**
 * PRIVATE — owner and admins only, not even readable by link.
 * UNLISTED — readable by anyone holding the link, listed nowhere. What every worksheet was
 *   before the marketplace, and still the default.
 * PUBLIC — listed in the marketplace for other teachers to find and copy.
 */
export type WorksheetVisibility = "PRIVATE" | "UNLISTED" | "PUBLIC";

// ── Marketplace ─────────────────────────────────────────

/** One published worksheet as the marketplace lists it. Mirrors `WorksheetSearchResult.Item`. */
export interface MarketplaceItem {
    worksheetId: string;
    name: string;
    description: string | null;
    ownerId: string | null;
    /** ISO-8601 instant. */
    createdAt: string | null;
    sequenceCount: number;
    /** Distinct programming languages of the sequences inside, sorted. */
    languages: string[];
    /** How often this worksheet has been copied. */
    copyCount: number;
    /** Whether the current user already copied it, so the card can say so. */
    alreadyCopied: boolean;
    /**
     * A `ts_headline` snippet with the match wrapped in control-character markers, or null when
     * there was no query. Render it with {@link renderHighlight}, never as raw HTML.
     */
    highlight: string | null;
}

export interface MarketplaceFacetBucket {
    name: string;
    count: number;
}

/** Mirrors `WorksheetSearchResult`. */
export interface MarketplaceSearchResponse {
    items: MarketplaceItem[];
    page: number;
    size: number;
    total: number;
    facets: {
        languages: MarketplaceFacetBucket[];
        mine: number;
        others: number;
    };
}

// ── Class Types ─────────────────────────────────────────

export interface ClassInfo {
    id: string;
    name: string;
    description: string;
    ownerId: string;
    /** Creator's name/email; only sent in the admin overview of all classes. */
    ownerName?: string | null;
    ownerEmail?: string | null;
    trackingEnabled: boolean;
    createdAt: string;
    updatedAt: string;
    students: StudentInfo[];
    worksheets: ClassWorksheetInfo[];
}

export interface StudentProgress {
    userId: string | null;
    studentName: string;
    solvedTaskIds: string[];
    totalAttempts: number;
    correctAttempts: number;
}

export interface StudentProgressTask {
    taskId: string;
    tierLevel: number;
    statement: string;
    attempts: number;
    correctAttempts: number;
    solved: boolean;
}

/** `index` is the sequence's 1-based position in the worksheet — sequences have no name of their own. */
export interface StudentProgressSequence {
    sequenceId: string;
    index: number;
    programmingLanguage: string;
    concepts: string[] | null;
    taskDescription: string | null;
    tasks: StudentProgressTask[];
}

export interface StudentProgressWorksheet {
    worksheetId: string;
    name: string;
    sequences: StudentProgressSequence[];
}

/**
 * What one student last built for one task, beside the solution it was meant to become.
 *
 * The arrangement comes from the solve page's autosave, which is independent of the attempt
 * counts on the progress page around it — those count Feedback clicks. A student can therefore
 * have no attempts and still have left a substantial arrangement here.
 */
export interface StudentTaskState {
    taskId: string;
    statement: string;
    programmingLanguage: string;
    /** The expected solution, annotations intact; run it through toDisplayLines before showing it. */
    solution: string;
    /** False when this student has never arranged anything for this task. */
    hasState: boolean;
    blockOrder: string[];
    indentLevels: number[];
    solved: boolean;
    solvedAt: string | null;
    updatedAt: string | null;
}

/** One student's work across every worksheet assigned to one class. */
export interface StudentProgressDetail {
    studentId: string;
    studentName: string;
    worksheets: StudentProgressWorksheet[];
}

export interface StudentInfo {
    userId: string;
    firstName: string;
    lastName: string;
    email: string | null;
    inviteToken: string | null;
    /** FIM username if the student logs in with a FIM-Account. */
    fimUid?: string | null;
}

/** How a worksheet assignment stands relative to its schedule, for one class. */
export type WorksheetAvailability = "SCHEDULED" | "OPEN" | "CLOSED";

export interface ClassWorksheetInfo {
    worksheetId: string;
    name: string;
    description: string;
    /**
     * Set by the teacher's publish button. A newly assigned worksheet starts
     * false, so students never meet one that is still being prepared.
     */
    published?: boolean;
    /** ISO instant at which it opens by itself; null means only publishing opens it. */
    releaseAt?: string | null;
    /** ISO instant, or null for "open indefinitely". */
    deadlineAt?: string | null;
    /** Minutes allowed after the deadline; null or 0 means a sharp deadline. */
    graceMinutes?: number | null;
    /** Resolved server-side, so the client cannot disagree about the time. */
    availability?: WorksheetAvailability;
}

// ── AP19 Teil A: Library / Sequence Tags ───────────────

export type SequenceTagSource = "auto" | "llm" | "manual";

export interface SequenceTag {
    sequenceId: string;
    tagName: string;
    source: SequenceTagSource;
    /** 0..1 — only meaningful for source='llm'; null otherwise */
    confidence: number | null;
    createdAt: string;
}

export interface LibraryItem {
    sequenceId: string;
    /** The name the owner gave the sequence; absent or null when it has none. */
    title?: string | null;
    programmingLanguage: string;
    taskDescription: string | null;
    semanticSummary: string | null;
    createdAt: string | null;
    ownerId: string | null;
    tierCount: number;
    tags: SequenceTag[];
    concepts: string[];
    usageCount: number;
    /** ts_headline snippet with control-char markers; run through renderHighlight() before display. */
    highlight: string | null;
    /** A15: True if the current user already has this sequence in one of their own worksheets. */
    alreadyInMyWorksheet: boolean;
}

export interface LibraryFacetBucket {
    name: string;
    count: number;
}

export interface LibraryFacets {
    languages: LibraryFacetBucket[];
    /** Up to the requested `tagsLimit` (see LibraryPage's tagsLimit param), sorted by count. */
    topTags: LibraryFacetBucket[];
    /** How many distinct tags exist under the current filters, regardless of how many topTags carries. */
    totalTagCount: number;
    /** Concept-facet mirror of topTags, bounded by `conceptsLimit`. */
    topConcepts: LibraryFacetBucket[];
    /** Concept-facet mirror of totalTagCount. */
    totalConceptCount: number;
    ownership: { mine: number; others: number };
    /** Tier counts present under the other filters, ascending; `name` is the count as text. */
    tierCounts: LibraryFacetBucket[];
}

export interface LibrarySearchResponse {
    items: LibraryItem[];
    page: number;
    size: number;
    total: number;
    facets: LibraryFacets;
    /** Fuzzy tag suggestions when the FTS query matched nothing. */
    suggestions: string[];
}

// ── Auth Types ──────────────────────────────────────────

export type UserRole = "ADMIN" | "TEACHER" | "STUDENT";

export interface AuthUser {
    userId: string;
    email: string;
    firstName: string;
    lastName: string;
    role: UserRole;
    token: string;
    /** True when a teacher set a temporary password — user must change it before continuing. */
    mustChangePassword?: boolean;
    /** True for a FIM-Account user, whose password is managed by the FIM directory. */
    fimAccount?: boolean;
}

export interface LoginResponse {
    token: string;
    role: UserRole;
    email: string;
    firstName: string;
    lastName: string;
    userId: string;
    /** Class IDs the student belongs to (only for STUDENT role) */
    classIds?: string[];
    /** True when a teacher set a temporary password. */
    mustChangePassword?: boolean;
    /** True for a FIM-Account user. */
    fimAccount?: boolean;
}

