import { useTranslation } from "react-i18next";
import type { ClassWorksheetInfo } from "../types/models.ts";
import { formatDeadline, SOON_MS } from "../lib/classDeadlines.ts";

interface Props {
    worksheet: ClassWorksheetInfo;
}

/**
 * The deadline as a student sees it, under a worksheet in their class.
 *
 * The teacher's side of this is {@link WorksheetSchedule}, which can edit the
 * schedule; a student only needs to know when the worksheet is due and whether
 * that moment has passed. A worksheet with no deadline shows nothing, because
 * "no deadline" is not news.
 */
export default function WorksheetDeadline({ worksheet }: Props) {
    const { t, i18n } = useTranslation();

    if (worksheet.availability === "CLOSED") {
        return (
            <div className="small mt-1">
                <span className="badge text-bg-danger me-2">{t("deadline.closed")}</span>
                {worksheet.deadlineAt && (
                    <span className="text-muted">
                        {t("deadline.was", { when: formatDeadline(worksheet.deadlineAt, i18n.language) })}
                    </span>
                )}
            </div>
        );
    }

    if (!worksheet.deadlineAt) return null;

    const remaining = Date.parse(worksheet.deadlineAt) - Date.now();
    // Urgent below a day, so it stands out from the ones weeks away.
    const soon = Number.isFinite(remaining) && remaining <= SOON_MS;

    return (
        <div className={`small mt-1 ${soon ? "text-danger" : "text-muted"}`}>
            <i className="bi bi-clock me-1"></i>
            {t("deadline.due", { when: formatDeadline(worksheet.deadlineAt, i18n.language) })}
        </div>
    );
}
