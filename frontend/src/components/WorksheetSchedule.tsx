import { useState } from "react";
import { useTranslation } from "react-i18next";
import { api, apiErrorMessage } from "../lib/api.ts";
import type { ClassWorksheetInfo } from "../types/models.ts";
import { toDateTimeParts, fromDateTimeParts } from "../lib/scheduleTime.ts";
import { toGraceParts, fromGraceParts } from "../lib/gracePeriod.ts";
import { splitHintClauses } from "../lib/hintLines.ts";

interface Props {
    classId: string;
    worksheet: ClassWorksheetInfo;
    onSaved: (patch: Partial<ClassWorksheetInfo>) => void;
}

/** Each clause of a hint on its own line; see {@link splitHintClauses}. */
function hintLines(text: string) {
    return splitHintClauses(text).map((line, i) => (
        <span key={i} className="d-block">{line}</span>
    ));
}

/**
 * Release date, deadline and grace period for one worksheet in one class.
 *
 * Every field is optional, and empty means unconstrained: no release makes it
 * available at once, no deadline leaves it open indefinitely, no grace makes the
 * deadline sharp. The badge shows the state the server resolved, so it cannot
 * disagree with the server about what time it is.
 */
export default function WorksheetSchedule({ classId, worksheet, onSaved }: Props) {
    const { t } = useTranslation();
    const [open, setOpen] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const initialRelease = toDateTimeParts(worksheet.releaseAt);
    const [releaseDate, setReleaseDate] = useState(initialRelease.date);
    const [releaseTime, setReleaseTime] = useState(initialRelease.time);

    const initialDeadline = toDateTimeParts(worksheet.deadlineAt);
    const [deadlineDate, setDeadlineDate] = useState(initialDeadline.date);
    const [deadlineTime, setDeadlineTime] = useState(initialDeadline.time);
    const initialGrace = toGraceParts(worksheet.graceMinutes);
    const [graceHours, setGraceHours] = useState(initialGrace.hours);
    const [graceMinutes, setGraceMinutes] = useState(initialGrace.minutes);

    const badge = () => {
        switch (worksheet.availability) {
            case "SCHEDULED":
                return <span className="badge text-bg-secondary">{t("schedule.scheduled")}</span>;
            case "CLOSED":
                return <span className="badge text-bg-danger">{t("schedule.closed")}</span>;
            case "OPEN":
            default:
                return <span className="badge text-bg-success">{t("schedule.open")}</span>;
        }
    };

    async function save() {
        setSaving(true);
        setError(null);

        // undefined means the pair could not be read as whole, non-negative
        // numbers; null means no grace at all, which is a sharp deadline.
        const graceValue = fromGraceParts({ hours: graceHours, minutes: graceMinutes });
        if (graceValue === undefined) {
            setError(t("schedule.graceInvalid"));
            setSaving(false);
            return;
        }

        const payload = {
            releaseAt: fromDateTimeParts({ date: releaseDate, time: releaseTime }),
            deadlineAt: fromDateTimeParts({ date: deadlineDate, time: deadlineTime }),
            graceMinutes: graceValue,
        };

        try {
            await api.put(`/classes/${classId}/worksheets/${worksheet.worksheetId}/schedule`, payload);
            onSaved(payload);
            setOpen(false);
        } catch (err) {
            setError(apiErrorMessage(err, t("schedule.saveFailed")));
        } finally {
            setSaving(false);
        }
    }

    /** Publish, unpublish or end the assignment straight away. */
    async function act(action: "publish" | "unpublish" | "close") {
        setSaving(true);
        setError(null);
        try {
            await api.post(`/classes/${classId}/worksheets/${worksheet.worksheetId}/${action}`);
            if (action === "close") {
                // Ending sets the deadline to this moment with no grace, so the
                // editor has to show that rather than the teacher's old dates.
                const now = toDateTimeParts(new Date().toISOString());
                setDeadlineDate(now.date);
                setDeadlineTime(now.time);
                setGraceHours("");
                setGraceMinutes("");
                onSaved({ published: true, deadlineAt: new Date().toISOString(),
                          graceMinutes: 0, availability: "CLOSED" });
            } else if (action === "publish") {
                // The server stamps the release date with this moment, the same
                // way ending stamps the deadline; show that rather than leaving
                // the field empty.
                const now = new Date().toISOString();
                const parts = toDateTimeParts(now);
                setReleaseDate(parts.date);
                setReleaseTime(parts.time);
                onSaved({ published: true, releaseAt: now, availability: "OPEN" });
            } else {
                // The server clears the release date as well, so that both routes
                // to visibility are closed; reflect that in the open editor.
                setReleaseDate("");
                setReleaseTime("");
                onSaved({ published: false, releaseAt: null, availability: "SCHEDULED" });
            }
        } catch (err) {
            setError(apiErrorMessage(err, t("schedule.actionFailed")));
        } finally {
            setSaving(false);
        }
    }

    // Gate on what students see rather than on the publish flag: an assignment
    // whose release date has passed is visible even with `published` false.
    const isHidden = worksheet.availability === "SCHEDULED";
    const isClosed = worksheet.availability === "CLOSED";
    // Running: released and not yet past its deadline. Either publishing or
    // a release date that has arrived gets an assignment here.
    const isRunning = worksheet.availability === "OPEN";

    return (
        <div className="mt-2">
            <div className="d-flex align-items-center gap-2 flex-wrap">
                {badge()}

                {/*
                  * These follow what students actually see, not the raw publish
                  * flag. An assignment released by its date is live even though
                  * `published` is still false, and offering "publish" for
                  * something already visible — while hiding "unpublish" — read as
                  * the two disagreeing.
                  *
                  * A new assignment starts hidden, so a half-finished worksheet
                  * is never seen by accident.
                  */}
                {isHidden && (
                    <button className="btn btn-sm btn-outline-success" disabled={saving}
                            onClick={() => act("publish")}>
                        {t("schedule.publishNow")}
                    </button>
                )}
                {!isHidden && !isClosed && (
                    <button className="btn btn-sm btn-outline-secondary" disabled={saving}
                            onClick={() => act("unpublish")}>
                        {t("schedule.unpublish")}
                    </button>
                )}
                {/*
                  * Ending only makes sense for something that is running, so this
                  * is keyed on the resolved state rather than on `published`: an
                  * assignment opened by its release date is running too, even
                  * though it was never published by hand.
                  *
                  * The title sits on the wrapper because a disabled control
                  * receives no pointer events, and pointer-events:none on the
                  * button lets the hover fall through to it.
                  */}
                {!isClosed && (
                    <span
                        className="d-inline-block"
                        title={isRunning ? undefined : t("schedule.endNotRunning")}
                    >
                        <button
                            className="btn btn-sm btn-outline-danger"
                            disabled={saving || !isRunning}
                            style={saving || !isRunning ? { pointerEvents: "none" } : undefined}
                            onClick={() => act("close")}
                        >
                            {t("schedule.endNow")}
                        </button>
                    </span>
                )}

                <button
                    className="btn btn-sm btn-link p-0"
                    onClick={() => setOpen(!open)}
                    aria-expanded={open}
                >
                    {open ? t("schedule.hide") : t("schedule.edit")}
                </button>
            </div>

            {open && (
                <div className="card card-body mt-2 py-2">
                    <div className="row g-2 align-items-start">
                        <div className="col-sm-4">
                            <label className="form-label mb-1 small" htmlFor={`rel-${worksheet.worksheetId}`}>
                                {t("schedule.releaseAt")}
                            </label>
                            <div className="input-group input-group-sm">
                                <input
                                    id={`rel-${worksheet.worksheetId}`}
                                    type="date"
                                    className="form-control"
                                    value={releaseDate}
                                    onChange={(e) => setReleaseDate(e.target.value)}
                                    aria-label={t("schedule.dateLabel")}
                                />
                                <input
                                    type="time"
                                    className="form-control"
                                    value={releaseTime}
                                    onChange={(e) => setReleaseTime(e.target.value)}
                                    // Optional: an empty time means the very start
                                    // of the chosen day.
                                    aria-label={t("schedule.timeLabel")}
                                />
                            </div>
                            <small className="text-muted">{hintLines(t("schedule.releaseHint"))}</small>
                        </div>
                        <div className="col-sm-4">
                            <label className="form-label mb-1 small" htmlFor={`dl-${worksheet.worksheetId}`}>
                                {t("schedule.deadlineAt")}
                            </label>
                            <div className="input-group input-group-sm">
                                <input
                                    id={`dl-${worksheet.worksheetId}`}
                                    type="date"
                                    className="form-control"
                                    value={deadlineDate}
                                    onChange={(e) => setDeadlineDate(e.target.value)}
                                    aria-label={t("schedule.dateLabel")}
                                />
                                <input
                                    type="time"
                                    className="form-control"
                                    value={deadlineTime}
                                    onChange={(e) => setDeadlineTime(e.target.value)}
                                    aria-label={t("schedule.timeLabel")}
                                />
                            </div>
                            <small className="text-muted">{hintLines(t("schedule.deadlineHint"))}</small>
                        </div>
                        <div className="col-sm-4">
                            <label className="form-label mb-1 small" htmlFor={`grh-${worksheet.worksheetId}`}>
                                {t("schedule.grace")}
                            </label>
                            <div className="input-group input-group-sm">
                                <input
                                    id={`grh-${worksheet.worksheetId}`}
                                    type="number"
                                    min={0}
                                    className="form-control"
                                    value={graceHours}
                                    onChange={(e) => setGraceHours(e.target.value)}
                                    placeholder="0"
                                    aria-label={t("schedule.graceHours")}
                                />
                                <span className="input-group-text">{t("schedule.hoursShort")}</span>
                                <input
                                    type="number"
                                    min={0}
                                    // Not capped at 59: a teacher typing 90 here
                                    // plainly means 90 minutes, and rejecting it
                                    // would be pedantry. It is stored as total
                                    // minutes and read back split properly.
                                    className="form-control"
                                    value={graceMinutes}
                                    onChange={(e) => setGraceMinutes(e.target.value)}
                                    placeholder="0"
                                    aria-label={t("schedule.graceMinutesLabel")}
                                />
                                <span className="input-group-text">{t("schedule.minutesShort")}</span>
                            </div>
                            <small className="text-muted">{hintLines(t("schedule.graceHint"))}</small>
                        </div>
                    </div>

                    {error && <div className="alert alert-danger py-1 px-2 mt-2 mb-0 small">{error}</div>}

                    <div className="d-flex gap-2 mt-2">
                        <button className="btn btn-sm btn-primary" onClick={save} disabled={saving}>
                            {saving ? t("schedule.saving") : t("schedule.save")}
                        </button>
                        <button
                            className="btn btn-sm btn-outline-secondary"
                            disabled={saving}
                            onClick={() => {
                                // Clearing every field restores the default:
                                // available at once, open forever, sharp deadline.
                                setReleaseDate("");
                                setReleaseTime("");
                                setDeadlineDate("");
                                setDeadlineTime("");
                                setGraceHours("");
                                setGraceMinutes("");
                            }}
                        >
                            {t("schedule.clear")}
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
}
