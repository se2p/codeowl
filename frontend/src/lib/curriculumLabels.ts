import type { CurriculumTopic } from "../types/models";

/**
 * Curriculum topics carry their own translations in the database: German in
 * `name`/`description`/`track`, English in the `*_en` columns added by
 * backend/src/main/resources/db/migration/V11__curriculum_i18n.sql. Picking
 * the column here rather than keeping a table in the i18n bundles means a
 * topic added to the curriculum needs no frontend change at all.
 *
 * German is the source language, so an English column that is missing — a
 * topic added to the curriculum without a translation — falls back to German
 * rather than rendering an empty row. (The columns themselves are not
 * optional: the backend selects them, so the migration has to have run.)
 */

/** i18next hands out "en", "en-US", "en-GB", … — match the language, not the region. */
export function prefersEnglish(language: string | undefined): boolean {
    return (language ?? "").toLowerCase().startsWith("en");
}

function pick(german: string | null | undefined, english: string | null | undefined, language: string | undefined): string {
    if (prefersEnglish(language) && english != null && english.trim() !== "") {
        return english;
    }
    return german ?? "";
}

export function topicName(topic: CurriculumTopic, language: string | undefined): string {
    return pick(topic.name, topic.nameEn, language);
}

export function topicDescription(topic: CurriculumTopic, language: string | undefined): string {
    return pick(topic.description, topic.descriptionEn, language);
}

export function topicTrack(topic: CurriculumTopic, language: string | undefined): string {
    return pick(topic.track, topic.trackEn, language);
}
