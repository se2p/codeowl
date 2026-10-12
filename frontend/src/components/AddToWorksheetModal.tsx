import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { api, apiErrorMessage } from "../lib/api.ts";
import type { Worksheet } from "../types/models.ts";

interface AddToWorksheetModalProps {
    /** The sequence to link into whichever worksheet the caller picks. */
    sequenceId: string;
    onClose: () => void;
}

/**
 * Lets the caller link one sequence into any of their own worksheets.
 *
 * <p>Self-contained: it loads the caller's worksheets itself and owns its own loading/error/
 * success state, so both the sequence page (one sequence, "add it somewhere") and the library
 * (many cards, each opening the same dialog for whichever one was clicked) can mount it with
 * nothing but a sequence id. The list/pick shape mirrors WorksheetPage's "assign to class" modal.
 */
export default function AddToWorksheetModal({ sequenceId, onClose }: AddToWorksheetModalProps) {
    const { t } = useTranslation();
    const navigate = useNavigate();

    const [myWorksheets, setMyWorksheets] = useState<Worksheet[]>([]);
    const [loadingWorksheets, setLoadingWorksheets] = useState(true);
    const [addingWorksheetId, setAddingWorksheetId] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [success, setSuccess] = useState<string | null>(null);

    useEffect(() => {
        let cancelled = false;
        setLoadingWorksheets(true);
        setError(null);
        api.get<Worksheet[]>("/worksheets/my")
            .then(({ data }) => { if (!cancelled) setMyWorksheets(data); })
            .catch(() => { if (!cancelled) setError(t("sequence.errorLoadingWorksheets")); })
            .finally(() => { if (!cancelled) setLoadingWorksheets(false); });
        return () => { cancelled = true; };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [sequenceId]);

    async function handleAdd(worksheet: Worksheet) {
        setAddingWorksheetId(worksheet.id);
        setError(null);
        try {
            await api.post(`/worksheets/${worksheet.id}/sequences/${sequenceId}`);
            setMyWorksheets((prev) =>
                prev.map((w) =>
                    w.id === worksheet.id
                        ? { ...w, sequenceDTOS: [...w.sequenceDTOS, { id: sequenceId }] }
                        : w
                )
            );
            setSuccess(t("sequence.addToWorksheetSuccess", { name: worksheet.name }));
            setTimeout(() => setSuccess(null), 3000);
        } catch (err) {
            setError(apiErrorMessage(err, t("sequence.addToWorksheetError")));
        } finally {
            setAddingWorksheetId(null);
        }
    }

    return (
        <>
            <div className="modal fade show" style={{ display: "block" }} tabIndex={-1}>
                <div className="modal-dialog modal-dialog-centered">
                    <div className="modal-content rounded-4 shadow">
                        <div className="modal-header">
                            <h5 className="modal-title">{t("sequence.addToWorksheetTitle")}</h5>
                            <button type="button" className="btn-close" onClick={onClose} />
                        </div>
                        <div className="modal-body">
                            {error && <div className="alert alert-danger py-2">{error}</div>}
                            {success && <div className="alert alert-success py-2">{success}</div>}

                            {loadingWorksheets ? (
                                <div className="text-center py-3">
                                    <span className="spinner-border spinner-border-sm me-2" />
                                    {t("sequence.loadingWorksheets")}
                                </div>
                            ) : myWorksheets.length === 0 ? (
                                <div className="alert alert-info mb-0">
                                    {t("sequence.noWorksheets")}
                                    <br />
                                    <button
                                        className="btn btn-sm btn-outline-primary mt-2"
                                        onClick={() => {
                                            onClose();
                                            navigate("/worksheets");
                                        }}
                                    >
                                        {t("sequence.manageWorksheets")}
                                    </button>
                                </div>
                            ) : (
                                <div className="list-group">
                                    {myWorksheets.map((w) => {
                                        const alreadyAdded = w.sequenceDTOS.some((s) => s.id === sequenceId);
                                        return (
                                            <div
                                                key={w.id}
                                                className="list-group-item d-flex justify-content-between align-items-center"
                                            >
                                                <div>
                                                    <strong>{w.name}</strong>
                                                    {w.description && (
                                                        <small className="text-muted d-block">{w.description}</small>
                                                    )}
                                                </div>
                                                {alreadyAdded ? (
                                                    <span className="badge bg-secondary-subtle text-secondary-emphasis">
                                                        {t("sequence.alreadyInWorksheet")}
                                                    </span>
                                                ) : (
                                                    <button
                                                        className="btn btn-sm btn-outline-success"
                                                        onClick={() => handleAdd(w)}
                                                        disabled={addingWorksheetId === w.id}
                                                    >
                                                        {addingWorksheetId === w.id ? (
                                                            <span className="spinner-border spinner-border-sm" />
                                                        ) : (
                                                            t("sequence.add")
                                                        )}
                                                    </button>
                                                )}
                                            </div>
                                        );
                                    })}
                                </div>
                            )}
                        </div>
                        <div className="modal-footer">
                            <button type="button" className="btn btn-secondary" onClick={onClose}>
                                {t("common.close")}
                            </button>
                        </div>
                    </div>
                </div>
            </div>
            <div className="modal-backdrop fade show"></div>
        </>
    );
}
