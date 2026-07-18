import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../lib/api";
import { useAuth } from "../context/AuthContext";
import { useTranslation } from "react-i18next";
import type { CurriculumTopic, CurriculumCodeExample } from "../types/models";

/**
 * Top-level topics (parentId === null) have their display name derived
 * from gradeLevel + track instead of the DB `name` column, because the
 * name field can be corrupted by CP850 mojibake during seeding on some hosts
 * (e.g. "Jgst. 9 ÔÇö NTG" instead of "Jgst. 9 — NTG").
 * Sub-topics keep their DB name as-is (ASCII only).
 */
function topicLabel(topic: CurriculumTopic): string {
    if (topic.parentId === null && topic.gradeLevel !== null) {
        return topic.track
            ? `Jgst. ${topic.gradeLevel} — ${topic.track}`
            : `Jgst. ${topic.gradeLevel}`;
    }
    return topic.name;
}

export default function CurriculumPage() {
    const { t } = useTranslation();
    const { isLoggedIn, isStudent, isAdmin } = useAuth();
    const navigate = useNavigate();

    // Topic tree state
    const [topLevelTopics, setTopLevelTopics] = useState<CurriculumTopic[]>([]);
    const [selectedGrade, setSelectedGrade] = useState<CurriculumTopic | null>(null);
    const [subTopics, setSubTopics] = useState<CurriculumTopic[]>([]);
    const [selectedTopic, setSelectedTopic] = useState<CurriculumTopic | null>(null);
    const [examples, setExamples] = useState<CurriculumCodeExample[]>([]);

    // Generation form
    const [language, setLanguage] = useState("java");
    const [targetLanguage, setTargetLanguage] = useState("de");
    const [tierCount, setTierCount] = useState(3);
    const [distractorsEnabled, setDistractorsEnabled] = useState(false);
    const [contextModeEnabled, setContextModeEnabled] = useState(false); // AP22
    const [splitEnabled, setSplitEnabled] = useState(false); // AP31
    const [showAdvanced, setShowAdvanced] = useState(false);
    const [blacklist, setBlacklist] = useState("");
    const [whitelist, setWhitelist] = useState("");
    const [difficultySlope, setDifficultySlope] = useState("");
    const [additionalContext, setAdditionalContext] = useState("");

    // UI state
    const [loading, setLoading] = useState(true);
    const [generating, setGenerating] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {
        loadTopLevelTopics();
    }, []);

    const loadTopLevelTopics = async () => {
        try {
            setLoading(true);
            const res = await api.get<CurriculumTopic[]>("/curriculum/topics");
            setTopLevelTopics(res.data);
        } catch {
            setError(t("curriculum.errorLoading"));
        } finally {
            setLoading(false);
        }
    };

    const selectGrade = async (topic: CurriculumTopic) => {
        setSelectedGrade(topic);
        setSelectedTopic(null);
        setExamples([]);
        try {
            const res = await api.get<CurriculumTopic[]>(`/curriculum/topics/${topic.id}/children`);
            setSubTopics(res.data);
        } catch {
            setError(t("curriculum.errorLoading"));
        }
    };

    const selectTopic = async (topic: CurriculumTopic) => {
        setSelectedTopic(topic);
        setExampleForm(null);
        try {
            const res = await api.get<CurriculumCodeExample[]>(`/curriculum/topics/${topic.id}/examples`);
            setExamples(res.data);
        } catch {
            setError(t("curriculum.errorLoading"));
        }
    };

    // ── AP32 admin: manage curated examples (admin-only) ────────────────
    interface ExampleForm {
        id?: string;
        title: string;
        language: string;
        code: string;
        problemStatement: string;
        difficulty: string;
        notes: string;
    }
    const [exampleForm, setExampleForm] = useState<ExampleForm | null>(null);
    const [savingExample, setSavingExample] = useState(false);
    const [exampleError, setExampleError] = useState("");

    const refreshExamples = async () => {
        if (!selectedTopic) return;
        try {
            const res = await api.get<CurriculumCodeExample[]>(`/curriculum/topics/${selectedTopic.id}/examples`);
            setExamples(res.data);
        } catch {
            setError(t("curriculum.errorLoading"));
        }
    };

    const openNewExample = () => {
        setExampleError("");
        setExampleForm({ title: "", language: language || "java", code: "", problemStatement: "", difficulty: "medium", notes: "" });
    };

    const openEditExample = (ex: CurriculumCodeExample) => {
        setExampleError("");
        setExampleForm({
            id: ex.id,
            title: ex.title,
            language: ex.language,
            code: ex.code,
            problemStatement: ex.problemStatement ?? "",
            difficulty: ex.difficulty ?? "medium",
            notes: ex.notes ?? "",
        });
    };

    const saveExample = async () => {
        if (!exampleForm || !selectedTopic) return;
        setSavingExample(true);
        setExampleError("");
        const payload = {
            title: exampleForm.title,
            language: exampleForm.language,
            code: exampleForm.code,
            problemStatement: exampleForm.problemStatement,
            difficulty: exampleForm.difficulty,
            notes: exampleForm.notes,
        };
        try {
            if (exampleForm.id) {
                await api.put(`/curriculum/examples/${exampleForm.id}`, payload);
            } else {
                await api.post(`/curriculum/topics/${selectedTopic.id}/examples`, payload);
            }
            setExampleForm(null);
            await refreshExamples();
        } catch (e: any) {
            setExampleError(e.response?.data?.message || t("curriculum.exampleSaveError"));
        } finally {
            setSavingExample(false);
        }
    };

    const deleteExample = async (ex: CurriculumCodeExample) => {
        if (!window.confirm(t("curriculum.deleteExampleConfirm"))) return;
        try {
            await api.delete(`/curriculum/examples/${ex.id}`);
            await refreshExamples();
        } catch {
            setError(t("curriculum.exampleDeleteError"));
        }
    };

    const handleGenerate = async () => {
        if (!selectedTopic) return;
        if (!isLoggedIn || isStudent) return;

        setGenerating(true);
        setError("");
        try {
            const payload: Record<string, unknown> = {
                language,
                targetLanguage,
                tierCount,
                distractorsEnabled,
                contextModeEnabled, // AP22
                splitEnabled,       // AP31
            };
            if (blacklist.trim()) payload.blacklist = blacklist.split(",").map(s => s.trim()).filter(Boolean);
            if (whitelist.trim()) payload.whitelist = whitelist.split(",").map(s => s.trim()).filter(Boolean);
            if (difficultySlope) payload.difficultySlope = difficultySlope;
            if (additionalContext.trim()) payload.additionalContext = additionalContext.trim();

            const res = await api.post(`/curriculum/topics/${selectedTopic.id}/generate`, payload);
            navigate(`/sequence/${res.data.id}`);
        } catch (e: any) {
            // AP32: a topic without curated seed examples returns 422 { error: "no_examples" }.
            // Show the internationalized message rather than the raw backend text.
            if (e.response?.data?.error === "no_examples") {
                setError(t("curriculum.topicNotAvailable", "Das wurde noch nicht eingepflegt und ist deshalb noch nicht verfügbar"));
            } else {
                setError(e.response?.data?.message || e.response?.data?.error || t("curriculum.errorGenerating"));
            }
        } finally {
            setGenerating(false);
        }
    };

    const goBack = () => {
        if (selectedTopic) {
            setSelectedTopic(null);
            setExamples([]);
        } else if (selectedGrade) {
            setSelectedGrade(null);
            setSubTopics([]);
        }
    };

    if (loading) {
        return (
            <div className="container py-5 text-center">
                <div className="spinner-border text-primary" />
                <p className="mt-2 text-muted">{t("common.loading")}</p>
            </div>
        );
    }

    return (
        <div className="container py-4" style={{ maxWidth: 900 }}>
            {/* Header */}
            <div className="text-center mb-4">
                <h2 className="fw-bold">
                    <i className="bi bi-book me-2"></i>
                    {t("curriculum.title")}
                    <span className="badge bg-warning text-dark ms-2" style={{ fontSize: "0.5em", verticalAlign: "middle" }}>
                        {t("curriculum.beta")}
                    </span>
                </h2>
                <p className="text-muted">{t("curriculum.subtitle")}</p>
            </div>

            {error && (
                <div className="alert alert-danger alert-dismissible">
                    {error}
                    <button className="btn-close" onClick={() => setError("")} />
                </div>
            )}

            {/* Breadcrumb navigation */}
            {(selectedGrade || selectedTopic) && (
                <nav className="mb-3">
                    <button className="btn btn-sm btn-outline-secondary" onClick={goBack}>
                        <i className="bi bi-arrow-left me-1"></i>
                        {t("curriculum.backToTopics")}
                    </button>
                    <span className="ms-3 text-muted">
                        {selectedGrade && topicLabel(selectedGrade)}
                        {selectedTopic && <> &rsaquo; {selectedTopic.name}</>}
                    </span>
                </nav>
            )}

            {/* Level 1: Grade selection */}
            {!selectedGrade && (
                <div>
                    <h5 className="mb-3">{t("curriculum.selectGrade")}</h5>
                    <div className="row g-3">
                        {topLevelTopics.map(topic => (
                            <div key={topic.id} className="col-md-6 col-lg-4">
                                <div
                                    className="card h-100 border-0 shadow-sm"
                                    style={{ cursor: "pointer", transition: "transform 0.15s" }}
                                    onClick={() => selectGrade(topic)}
                                    onMouseEnter={e => (e.currentTarget.style.transform = "translateY(-2px)")}
                                    onMouseLeave={e => (e.currentTarget.style.transform = "")}
                                >
                                    <div className="card-body">
                                        <h6 className="card-title fw-semibold">
                                            <i className="bi bi-mortarboard me-2 text-primary"></i>
                                            {topicLabel(topic)}
                                        </h6>
                                        {topic.description && (
                                            <p className="card-text text-muted small mb-0">{topic.description}</p>
                                        )}
                                        {topic.track && (
                                            <span className="badge bg-secondary mt-2">{topic.track}</span>
                                        )}
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {/* Level 2: Sub-topic selection */}
            {selectedGrade && !selectedTopic && (
                <div>
                    <h5 className="mb-3">{t("curriculum.selectTopic")}</h5>
                    {subTopics.length === 0 ? (
                        <p className="text-muted">{t("curriculum.noSubTopics")}</p>
                    ) : (
                        <div className="list-group">
                            {subTopics.map(topic => (
                                <button
                                    key={topic.id}
                                    className="list-group-item list-group-item-action d-flex justify-content-between align-items-start"
                                    onClick={() => selectTopic(topic)}
                                >
                                    <div>
                                        <h6 className="mb-1 fw-semibold">{topic.name}</h6>
                                        {topic.description && (
                                            <p className="mb-0 text-muted small">{topic.description}</p>
                                        )}
                                    </div>
                                    <i className="bi bi-chevron-right text-muted"></i>
                                </button>
                            ))}
                        </div>
                    )}
                </div>
            )}

            {/* Level 3: Topic detail + generation */}
            {selectedTopic && (
                <div>
                    {/* Code examples preview */}
                    <div className="card mb-4 border-0 shadow-sm">
                        <div className="card-header bg-light d-flex align-items-center justify-content-between">
                            <h6 className="mb-0">
                                <i className="bi bi-code-square me-2"></i>
                                {t("curriculum.examples", { count: examples.length })}
                            </h6>
                            {isAdmin && !exampleForm && (
                                <button className="btn btn-sm btn-outline-primary" onClick={openNewExample}>
                                    <i className="bi bi-plus-lg me-1"></i>{t("curriculum.addExample")}
                                </button>
                            )}
                        </div>
                        <div className="card-body">
                            {examples.length === 0 ? (
                                <p className="text-muted mb-0">{t("curriculum.noExamples")}</p>
                            ) : (
                                <div className="accordion" id="examplesAccordion">
                                    {examples.map((ex, idx) => (
                                        <div className="accordion-item" key={ex.id}>
                                            <h2 className="accordion-header">
                                                <button
                                                    className="accordion-button collapsed py-2"
                                                    type="button"
                                                    data-bs-toggle="collapse"
                                                    data-bs-target={`#ex-${idx}`}
                                                >
                                                    <span className="me-2">{ex.title}</span>
                                                    <span className="badge bg-info text-dark me-2">{ex.language}</span>
                                                    {ex.difficulty && (
                                                        <span className="badge bg-secondary">{ex.difficulty}</span>
                                                    )}
                                                </button>
                                            </h2>
                                            <div id={`ex-${idx}`} className="accordion-collapse collapse" data-bs-parent="#examplesAccordion">
                                                <div className="accordion-body">
                                                    {isAdmin && (
                                                        <div className="d-flex gap-2 mb-2">
                                                            <button className="btn btn-sm btn-outline-secondary" onClick={() => openEditExample(ex)}>
                                                                <i className="bi bi-pencil me-1"></i>{t("curriculum.editExample")}
                                                            </button>
                                                            <button className="btn btn-sm btn-outline-danger" onClick={() => deleteExample(ex)}>
                                                                <i className="bi bi-trash me-1"></i>{t("curriculum.deleteExample")}
                                                            </button>
                                                        </div>
                                                    )}
                                                    {ex.problemStatement && (
                                                        <p className="text-muted small mb-2">{ex.problemStatement}</p>
                                                    )}
                                                    <pre className="bg-dark text-light p-3 rounded" style={{ fontSize: "0.85rem", maxHeight: 300, overflow: "auto" }}>
                                                        <code>{ex.code}</code>
                                                    </pre>
                                                </div>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}

                            {isAdmin && exampleForm && (
                                <div className="border rounded p-3 mt-3 bg-light">
                                    <h6 className="mb-3">
                                        <i className="bi bi-pencil-square me-2"></i>
                                        {exampleForm.id ? t("curriculum.editExample") : t("curriculum.addExample")}
                                    </h6>
                                    {exampleError && <div className="alert alert-danger py-2">{exampleError}</div>}
                                    <div className="row g-2">
                                        <div className="col-md-6">
                                            <label className="form-label small fw-semibold">{t("curriculum.exampleTitle")}</label>
                                            <input className="form-control form-control-sm" value={exampleForm.title}
                                                onChange={e => setExampleForm({ ...exampleForm, title: e.target.value })} />
                                        </div>
                                        <div className="col-md-3">
                                            <label className="form-label small fw-semibold">{t("curriculum.exampleLanguage")}</label>
                                            <input className="form-control form-control-sm" value={exampleForm.language}
                                                onChange={e => setExampleForm({ ...exampleForm, language: e.target.value })} />
                                        </div>
                                        <div className="col-md-3">
                                            <label className="form-label small fw-semibold">{t("curriculum.exampleDifficulty")}</label>
                                            <select className="form-select form-select-sm" value={exampleForm.difficulty}
                                                onChange={e => setExampleForm({ ...exampleForm, difficulty: e.target.value })}>
                                                <option value="easy">{t("curriculum.diffEasy")}</option>
                                                <option value="medium">{t("curriculum.diffMedium")}</option>
                                                <option value="hard">{t("curriculum.diffHard")}</option>
                                            </select>
                                        </div>
                                        <div className="col-12">
                                            <label className="form-label small fw-semibold">{t("curriculum.exampleProblem")}</label>
                                            <textarea className="form-control form-control-sm" rows={2} value={exampleForm.problemStatement}
                                                onChange={e => setExampleForm({ ...exampleForm, problemStatement: e.target.value })} />
                                        </div>
                                        <div className="col-12">
                                            <label className="form-label small fw-semibold">{t("curriculum.exampleCode")}</label>
                                            <textarea className="form-control form-control-sm font-monospace" rows={10} spellCheck={false}
                                                style={{ fontSize: "0.85rem" }} value={exampleForm.code}
                                                onChange={e => setExampleForm({ ...exampleForm, code: e.target.value })} />
                                        </div>
                                        <div className="col-12">
                                            <label className="form-label small fw-semibold">{t("curriculum.exampleNotes")}</label>
                                            <textarea className="form-control form-control-sm" rows={2} value={exampleForm.notes}
                                                onChange={e => setExampleForm({ ...exampleForm, notes: e.target.value })} />
                                        </div>
                                    </div>
                                    <div className="d-flex gap-2 mt-3">
                                        <button className="btn btn-sm btn-primary" onClick={saveExample} disabled={savingExample}>
                                            {savingExample && <span className="spinner-border spinner-border-sm me-1" role="status" />}
                                            {t("curriculum.exampleSave")}
                                        </button>
                                        <button className="btn btn-sm btn-outline-secondary" onClick={() => setExampleForm(null)} disabled={savingExample}>
                                            {t("curriculum.exampleCancel")}
                                        </button>
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>

                    {/* Generation form — only for logged-in teachers */}
                    {isLoggedIn && !isStudent && (
                        <div className="card border-0 shadow-sm">
                            <div className="card-header bg-primary text-white">
                                <h6 className="mb-0">
                                    <i className="bi bi-lightning-charge me-2"></i>
                                    {t("curriculum.generate")}
                                    <span className="badge bg-warning text-dark ms-2" style={{ fontSize: "0.75em" }}>
                                        {t("curriculum.beta")}
                                    </span>
                                </h6>
                            </div>
                            <div className="card-body">
                                <div className="row g-3 mb-3">
                                    <div className="col-md-4">
                                        <label className="form-label fw-semibold">{t("curriculum.language")}</label>
                                        <select className="form-select" value={language} onChange={e => setLanguage(e.target.value)}>
                                            <option value="java">Java</option>
                                            <option value="python">Python</option>
                                        </select>
                                    </div>
                                    <div className="col-md-4">
                                        <label className="form-label fw-semibold">{t("curriculum.targetLanguage")}</label>
                                        <select className="form-select" value={targetLanguage} onChange={e => setTargetLanguage(e.target.value)}>
                                            <option value="de">{t("home.targetLanguageDe")}</option>
                                            <option value="en">{t("home.targetLanguageEn")}</option>
                                        </select>
                                    </div>
                                    <div className="col-md-4">
                                        <label className="form-label fw-semibold">{t("curriculum.tierCount")}</label>
                                        <select className="form-select" value={tierCount} onChange={e => setTierCount(Number(e.target.value))}>
                                            <option value={3}>{t("home.tierCount3")}</option>
                                            <option value={5}>{t("home.tierCount5")}</option>
                                        </select>
                                    </div>
                                </div>

                                <div className="form-check mb-3">
                                    <input
                                        className="form-check-input"
                                        type="checkbox"
                                        id="distractorsCheck"
                                        checked={distractorsEnabled}
                                        onChange={e => setDistractorsEnabled(e.target.checked)}
                                    />
                                    <label className="form-check-label" htmlFor="distractorsCheck">
                                        {t("curriculum.distractors")}
                                    </label>
                                </div>

                                {/* AP22: Context mode toggle (Beta) */}
                                <div className="form-check mb-3">
                                    <input
                                        className="form-check-input"
                                        type="checkbox"
                                        id="curriculumContextModeCheck"
                                        checked={contextModeEnabled}
                                        onChange={e => setContextModeEnabled(e.target.checked)}
                                    />
                                    <label className="form-check-label" htmlFor="curriculumContextModeCheck">
                                        {t("curriculum.contextMode", "Context mode (fuller code with scaffolding)")}
                                        <span className="badge text-bg-warning ms-2" style={{ fontSize: "0.65em" }}>Beta</span>
                                    </label>
                                </div>

                                {/* AP31: Inline-split toggle (Beta) */}
                                <div className="form-check mb-3">
                                    <input
                                        className="form-check-input"
                                        type="checkbox"
                                        id="curriculumSplitCheck"
                                        checked={splitEnabled}
                                        onChange={e => setSplitEnabled(e.target.checked)}
                                    />
                                    <label className="form-check-label" htmlFor="curriculumSplitCheck">
                                        {t("curriculum.split", "Generate inline-split blocks")}
                                        <span className="badge text-bg-warning ms-2" style={{ fontSize: "0.65em" }}>Beta</span>
                                    </label>
                                </div>

                                {/* Free-text field — write any info about the desired task.
                                    Flows to the backend as additionalContext → prompt "TEACHER INSTRUCTIONS". */}
                                <div className="mb-3">
                                    <label className="form-label fw-semibold" htmlFor="curriculumFreeText">
                                        <i className="bi bi-chat-left-text me-2"></i>
                                        {t("curriculum.freeText")}
                                    </label>
                                    <textarea
                                        id="curriculumFreeText"
                                        className="form-control"
                                        rows={3}
                                        value={additionalContext}
                                        onChange={e => setAdditionalContext(e.target.value)}
                                        placeholder={t("curriculum.freeTextPlaceholder")}
                                    />
                                    <div className="form-text">{t("curriculum.freeTextHint")}</div>
                                </div>

                                {/* Advanced options toggle */}
                                <div className="mb-3">
                                    <button
                                        className="btn btn-sm btn-outline-secondary"
                                        onClick={() => setShowAdvanced(!showAdvanced)}
                                    >
                                        <i className={`bi bi-chevron-${showAdvanced ? "up" : "down"} me-1`}></i>
                                        {t("curriculum.advancedOptions")}
                                    </button>
                                </div>

                                {showAdvanced && (
                                    <div className="border rounded p-3 mb-3 bg-light">
                                        <div className="mb-2">
                                            <label className="form-label small fw-semibold">{t("home.blacklist")}</label>
                                            <input
                                                className="form-control form-control-sm"
                                                value={blacklist}
                                                onChange={e => setBlacklist(e.target.value)}
                                                placeholder={t("home.blacklistPlaceholder")}
                                            />
                                        </div>
                                        <div className="mb-2">
                                            <label className="form-label small fw-semibold">{t("home.whitelist")}</label>
                                            <input
                                                className="form-control form-control-sm"
                                                value={whitelist}
                                                onChange={e => setWhitelist(e.target.value)}
                                                placeholder={t("home.whitelistPlaceholder")}
                                            />
                                        </div>
                                        <div className="mb-2">
                                            <label className="form-label small fw-semibold">{t("home.difficultySlope")}</label>
                                            <select className="form-select form-select-sm" value={difficultySlope} onChange={e => setDifficultySlope(e.target.value)}>
                                                <option value="">—</option>
                                                <option value="gentle">{t("home.slopeGentle")}</option>
                                                <option value="moderate">{t("home.slopeModerate")}</option>
                                                <option value="steep">{t("home.slopeSteep")}</option>
                                            </select>
                                        </div>
                                    </div>
                                )}

                                <button
                                    className="btn btn-primary w-100"
                                    onClick={handleGenerate}
                                    disabled={generating}
                                >
                                    {generating ? (
                                        <>
                                            <span className="spinner-border spinner-border-sm me-2" />
                                            {t("curriculum.generating")}
                                        </>
                                    ) : (
                                        <>
                                            <i className="bi bi-lightning-charge me-2"></i>
                                            {t("curriculum.generate")}
                                        </>
                                    )}
                                </button>
                            </div>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
