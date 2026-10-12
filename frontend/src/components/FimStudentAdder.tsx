import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { api, apiErrorMessage } from "../lib/api";
import { readCsvFile } from "../lib/csv.ts";
import { isValidFimUsername, normalizeFimUsername, parseFimUsernameCsv } from "../lib/fimStudentImport.ts";
import type { StudentInfo } from "../types/models";

/** How many usernames of an import to show before summarising the rest. */
const PREVIEW_LIMIT = 10;

interface Props {
    classId: string;
    /** Called with the students the server added, after every successful add. */
    onAdded: (students: StudentInfo[]) => void;
}

/**
 * Adds students to a class by FIM username: one typed in, or a CSV of them.
 *
 * Works whether or not the students have logged in to CodeOwl yet — the
 * backend creates the accounts up front — so a teacher can add a whole course
 * before its first session.
 */
export default function FimStudentAdder({ classId, onAdded }: Props) {
    const { t } = useTranslation();

    const [username, setUsername] = useState("");
    const [adding, setAdding] = useState(false);
    const [singleError, setSingleError] = useState("");

    const [csvUsernames, setCsvUsernames] = useState<string[]>([]);
    const [csvError, setCsvError] = useState("");
    const [importing, setImporting] = useState(false);
    const [importedCount, setImportedCount] = useState(0);
    const csvInputRef = useRef<HTMLInputElement>(null);

    const submit = (usernames: string[]) =>
        api.post<StudentInfo[]>(`/classes/${classId}/students/fim`, { usernames });

    const handleAddSingle = async (e: React.FormEvent) => {
        e.preventDefault();
        const normalized = normalizeFimUsername(username);
        setSingleError("");
        if (!isValidFimUsername(normalized)) {
            setSingleError(t("fimStudents.errorInvalid", { value: username.trim() }));
            return;
        }
        try {
            setAdding(true);
            const res = await submit([normalized]);
            setUsername("");
            onAdded(res.data);
        } catch (err) {
            setSingleError(apiErrorMessage(err, t("fimStudents.errorAdding")));
        } finally {
            setAdding(false);
        }
    };

    const clearCsv = () => {
        setCsvUsernames([]);
        setCsvError("");
        // Otherwise choosing the same file again fires no change event.
        if (csvInputRef.current) csvInputRef.current.value = "";
    };

    const handleCsvChosen = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        setCsvUsernames([]);
        setCsvError("");
        setImportedCount(0);
        if (!file) return;
        try {
            const result = parseFimUsernameCsv(await readCsvFile(file));
            if (result.error) setCsvError(t(result.error.key, result.error.params));
            else setCsvUsernames(result.usernames);
        } catch {
            setCsvError(t("fimStudents.errorRead"));
        }
    };

    const handleImport = async () => {
        if (csvUsernames.length === 0) return;
        try {
            setImporting(true);
            setCsvError("");
            const res = await submit(csvUsernames);
            clearCsv();
            setImportedCount(res.data.length);
            onAdded(res.data);
        } catch (err) {
            setCsvError(apiErrorMessage(err, t("fimStudents.errorAdding")));
        } finally {
            setImporting(false);
        }
    };

    return (
        <div className="mb-3 p-3 bg-light rounded">
            <h6 className="mb-1">{t("fimStudents.title")}</h6>
            <p className="small text-muted">{t("fimStudents.hint")}</p>

            {/* One at a time */}
            <form onSubmit={handleAddSingle}>
                <label className="form-label small mb-1" htmlFor="fim-username">
                    {t("fimStudents.usernameLabel")}
                </label>
                <div className="row g-2">
                    <div className="col">
                        <input
                            id="fim-username"
                            type="text"
                            className="form-control"
                            placeholder={t("fimStudents.usernamePlaceholder")}
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            autoCapitalize="none"
                            autoComplete="off"
                            spellCheck={false}
                            required
                        />
                    </div>
                    <div className="col-auto">
                        <button type="submit" className="btn btn-success" disabled={adding || importing}>
                            {adding ? "..." : t("fimStudents.add")}
                        </button>
                    </div>
                </div>
                {singleError && (
                    <div className="alert alert-danger py-1 px-2 mt-2 mb-0 small">{singleError}</div>
                )}
            </form>

            <hr />

            {/* A whole list from a CSV */}
            <label className="form-label small mb-1" htmlFor="fim-csv">
                {t("fimStudents.csvLabel")}
            </label>
            <p className="small text-muted mb-1">{t("fimStudents.csvHint")}</p>
            <input
                id="fim-csv"
                ref={csvInputRef}
                type="file"
                accept=".csv,.txt,text/csv,text/plain"
                className="form-control form-control-sm"
                disabled={importing || adding}
                onChange={handleCsvChosen}
            />

            {csvError && <div className="alert alert-danger py-1 px-2 mt-2 mb-0 small">{csvError}</div>}

            {/* Nothing is added until the teacher has seen the usernames. */}
            {csvUsernames.length > 0 && (
                <div className="mt-2">
                    <p className="small mb-1">{t("fimStudents.preview", { count: csvUsernames.length })}</p>
                    <ul className="small text-muted mb-2">
                        {csvUsernames.slice(0, PREVIEW_LIMIT).map((u) => (
                            <li key={u}>{u}</li>
                        ))}
                        {csvUsernames.length > PREVIEW_LIMIT && (
                            <li>{t("fimStudents.previewMore", { count: csvUsernames.length - PREVIEW_LIMIT })}</li>
                        )}
                    </ul>
                    <div className="d-flex gap-2">
                        <button className="btn btn-success btn-sm" onClick={handleImport} disabled={importing}>
                            {importing
                                ? t("fimStudents.importing")
                                : t("fimStudents.importBtn", { count: csvUsernames.length })}
                        </button>
                        <button className="btn btn-outline-secondary btn-sm" onClick={clearCsv} disabled={importing}>
                            {t("fimStudents.cancel")}
                        </button>
                    </div>
                </div>
            )}

            {importedCount > 0 && (
                <div className="alert alert-success py-1 px-2 mt-2 mb-0 small">
                    {t("fimStudents.imported", { count: importedCount })}
                </div>
            )}
        </div>
    );
}
