import type { TFunction } from "i18next";
import type { Task } from "../types/models";

/** Mirrors TaskPersistenceService.MAX_TITLE_LENGTH on the backend. */
export const MAX_TASK_TITLE_LENGTH = 120;

/**
 * What a task is called: the name its owner gave it, or else "Aufgabe 2" / "Task 2".
 *
 * The default is derived here rather than stored, so it follows the viewer's language and a
 * task the owner never renamed needs nothing in the database.
 */
export function taskName(task: Pick<Task, "title" | "tierLevel">, t: TFunction): string {
    const title = task.title?.trim();
    return title ? title : t("sequence.tier", { level: task.tierLevel });
}
