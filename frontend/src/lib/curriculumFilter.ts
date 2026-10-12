import type { CurriculumTopic } from "../types/models";

/**
 * The curriculum tree holds two kinds of top-level entry: school
 * Jahrgangsstufen from the Bavarian LehrplanPLUS, and university modules such
 * as "Programmieren 1". `education_level`
 * (backend/src/main/resources/db/migration/V12__curriculum_education_level.sql)
 * says which, and the overview filters on it.
 */
export const EDUCATION_LEVELS = ["SCHOOL", "UNIVERSITY"] as const;
export type EducationLevel = (typeof EDUCATION_LEVELS)[number];

/** What the filter is set to; "ALL" is the unfiltered default. */
export type EducationLevelFilter = EducationLevel | "ALL";

/**
 * Rows written before the column existed, or by a client that left it out,
 * read as school curriculum — that is what everything in the seed is, and it
 * keeps such a topic visible under the SCHOOL filter instead of nowhere.
 */
export function educationLevelOf(topic: CurriculumTopic): EducationLevel {
    const level = (topic.educationLevel ?? "").toUpperCase();
    return level === "UNIVERSITY" ? "UNIVERSITY" : "SCHOOL";
}

export function matchesEducationLevel(topic: CurriculumTopic, filter: EducationLevelFilter): boolean {
    return filter === "ALL" || educationLevelOf(topic) === filter;
}

export function filterByEducationLevel(
    topics: CurriculumTopic[],
    filter: EducationLevelFilter,
): CurriculumTopic[] {
    return topics.filter(topic => matchesEducationLevel(topic, filter));
}

/**
 * Whether the filter is worth showing at all. On a school-only installation —
 * which is every installation until someone adds a module — a control whose
 * every option shows the same list is just noise, so the overview only renders
 * it once the curriculum actually spans both.
 */
export function hasMixedEducationLevels(topics: CurriculumTopic[]): boolean {
    return topics.some(t => educationLevelOf(t) === "SCHOOL")
        && topics.some(t => educationLevelOf(t) === "UNIVERSITY");
}
