
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
    sequenceDTOS: { id: string }[];
    ownerId: string | null;
    hintsEnabled: boolean;
}

// ── Class Types ─────────────────────────────────────────

export interface ClassInfo {
    id: string;
    name: string;
    description: string;
    ownerId: string;
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

export interface StudentInfo {
    userId: string;
    firstName: string;
    lastName: string;
    email: string | null;
    inviteToken: string | null;
}

export interface ClassWorksheetInfo {
    worksheetId: string;
    name: string;
    description: string;
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
    programmingLanguage: string;
    taskDescription: string | null;
    semanticSummary: string | null;
    createdAt: string | null;
    ownerId: string | null;
    tierCount: number;
    tags: SequenceTag[];
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
    topTags: LibraryFacetBucket[];
    ownership: { mine: number; others: number };
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
}

