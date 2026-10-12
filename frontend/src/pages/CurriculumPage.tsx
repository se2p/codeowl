import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, apiErrorMessage } from "../lib/api";
import { useAuth } from "../context/AuthContext";
import { useTranslation } from "react-i18next";
import type { CurriculumTopic, CurriculumCodeExample } from "../types/models";
import { topicName, topicDescription, topicTrack } from "../lib/curriculumLabels";
import ConceptPicker from "../components/ConceptPicker";
import { useConceptCatalogue } from "../hooks/useConceptCatalogue";
import {
    educationLevelOf,
    filterByEducationLevel,
    hasMixedEducationLevels,
    type EducationLevel,
    type EducationLevelFilter,
} from "../lib/curriculumFilter";

export default function CurriculumPage() {
    const { t, i18n } = useTranslation();
    const { isLoggedIn, isStudent, isAdmin } = useAuth();
    const navigate = useNavigate();

    /**
     * Topic text is translated in the database (German columns + `*_en`);
     * see src/lib/curriculumLabels.ts. Top-level topics (parentId === null)
     * are the exception: their label is built from gradeLevel + the track,
     * so a newly seeded Jahrgangsstufe reads correctly in both languages
     * without anyone writing a label for it.
     */
    const lang = i18n.language;
    const trackLabel = (topic: CurriculumTopic): string => topicTrack(topic, lang);

    const topicLabel = (topic: CurriculumTopic): string => {
        if (topic.parentId === null && topic.gradeLevel !== null) {
            const grade = t("curriculum.grade", { level: topic.gradeLevel });
            return topic.track ? `${grade} — ${trackLabel(topic)}` : grade;
        }
        return topicName(topic, lang);
    };

    const description = (topic: CurriculumTopic): string => topicDescription(topic, lang);

    // Topic tree state
    const [topLevelTopics, setTopLevelTopics] = useState<CurriculumTopic[]>([]);
    const [selectedGrade, setSelectedGrade] = useState<CurriculumTopic | null>(null);
    const [subTopics, setSubTopics] = useState<CurriculumTopic[]>([]);
    const [selectedTopic, setSelectedTopic] = useState<CurriculumTopic | null>(null);
    const [examples, setExamples] = useState<CurriculumCodeExample[]>([]);
    // The concepts mapped to the selected learning area. Nobody picks these on
    // this page — they come from the curriculum mapping and go into the
    // generation prompt — so they are shown read-only, next to the form.
    const [topicConcepts, setTopicConcepts] = useState<string[]>([]);

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

    const loadSubTopics = async (parentId: string) => {
        try {
            const res = await api.get<CurriculumTopic[]>(`/curriculum/topics/${parentId}/children`);
            setSubTopics(res.data);
        } catch {
            setError(t("curriculum.errorLoading"));
        }
    };

    const selectGrade = async (topic: CurriculumTopic) => {
        setSelectedGrade(topic);
        setSelectedTopic(null);
        setExamples([]);
        await loadSubTopics(topic.id);
    };

    const selectTopic = async (topic: CurriculumTopic) => {
        setSelectedTopic(topic);
        setExampleForm(null);
        setTopicConcepts([]);
        try {
            const [exampleRes, conceptRes] = await Promise.all([
                api.get<CurriculumCodeExample[]>(`/curriculum/topics/${topic.id}/examples`),
                api.get<string[]>(`/curriculum/topics/${topic.id}/concepts`),
            ]);
            setExamples(exampleRes.data);
            setTopicConcepts(conceptRes.data);
        } catch {
            setError(t("curriculum.errorLoading"));
        }
    };

    // Which education level the overview is showing. Kept in component state
    // rather than persisted: a filter that silently survives a reload is how a
    // teacher ends up convinced their curriculum has disappeared.
    const [levelFilter, setLevelFilter] = useState<EducationLevelFilter>("ALL");
    const visibleTopics = filterByEducationLevel(topLevelTopics, levelFilter);

    // ── Admin: extend the curriculum tree (admin-only) ──────────────────
    // A top-level entry is either a Jahrgangsstufe — described by grade level
    // and track, from which the backend derives its name, which is also what
    // the cards below display — or a module that carries its own name.
    interface TopLevelForm {
        /** Set when editing an existing entry; absent when creating one. */
        id?: string;
        kind: "GRADE" | "MODULE";
        educationLevel: EducationLevel;
        gradeLevel: string;
        track: string;
        trackEn: string;
        name: string;
        nameEn: string;
        description: string;
        descriptionEn: string;
    }
    interface AreaForm {
        /** Set when editing an existing learning area; absent when creating one. */
        id?: string;
        /** Selected concept names, in the catalogue's spelling. */
        concepts: string[];
        name: string;
        nameEn: string;
        description: string;
        descriptionEn: string;
        learningArea: string;
    }
    const [topLevelForm, setTopLevelForm] = useState<TopLevelForm | null>(null);
    const [areaForm, setAreaForm] = useState<AreaForm | null>(null);
    const [savingTopic, setSavingTopic] = useState(false);
    const [topicError, setTopicError] = useState("");
    // The whole concept catalogue, to suggest from while typing.
    const conceptCatalogue = useConceptCatalogue();

    const openNewTopLevel = () => {
        setTopicError("");
        setTopLevelForm({
            kind: "GRADE",
            educationLevel: "SCHOOL",
            gradeLevel: "",
            track: "",
            trackEn: "",
            name: "",
            nameEn: "",
            description: "",
            descriptionEn: "",
        });
    };

    const openEditTopLevel = (topic: CurriculumTopic) => {
        setTopicError("");
        // Which shape the entry has follows from the stored grade level, the
        // same rule the backend applies.
        setTopLevelForm({
            id: topic.id,
            kind: topic.gradeLevel !== null ? "GRADE" : "MODULE",
            educationLevel: educationLevelOf(topic),
            gradeLevel: topic.gradeLevel !== null ? String(topic.gradeLevel) : "",
            track: topic.track ?? "",
            trackEn: topic.trackEn ?? "",
            name: topic.name ?? "",
            nameEn: topic.nameEn ?? "",
            description: topic.description ?? "",
            descriptionEn: topic.descriptionEn ?? "",
        });
    };

    const openEditArea = async (topic: CurriculumTopic) => {
        setTopicError("");
        setAreaForm({
            id: topic.id,
            name: topic.name ?? "",
            nameEn: topic.nameEn ?? "",
            description: topic.description ?? "",
            descriptionEn: topic.descriptionEn ?? "",
            learningArea: topic.learningArea ?? "",
            concepts: [],
        });
        // The mapping is a relation, so it does not come with the topic.
        try {
            const res = await api.get<string[]>(`/curriculum/topics/${topic.id}/concepts`);
            setAreaForm(form => (form && form.id === topic.id ? { ...form, concepts: res.data } : form));
        } catch {
            setTopicError(t("curriculum.errorLoading"));
        }
    };

    /**
     * Switching the type moves the education level with it, since that is the
     * pairing in practice — Jahrgangsstufen are school, modules are university.
     * The select stays editable for the cases where it is not.
     */
    const setTopLevelKind = (kind: TopLevelForm["kind"]) => {
        if (!topLevelForm) return;
        setTopLevelForm({
            ...topLevelForm,
            kind,
            educationLevel: kind === "MODULE" ? "UNIVERSITY" : "SCHOOL",
        });
    };

    /** A Jahrgangsstufe needs its number, a module its name. */
    const topLevelFormIsComplete = (form: TopLevelForm): boolean =>
        form.kind === "GRADE" ? Number(form.gradeLevel) >= 1 : form.name.trim() !== "";

    const openNewArea = () => {
        setTopicError("");
        setAreaForm({ name: "", nameEn: "", description: "", descriptionEn: "", learningArea: "", concepts: [] });
    };

    const saveTopLevel = async () => {
        if (!topLevelForm) return;
        setSavingTopic(true);
        setTopicError("");
        try {
            // A grade level and a name are mutually exclusive server-side: the
            // presence of gradeLevel is what makes the entry a Jahrgangsstufe.
            const isGrade = topLevelForm.kind === "GRADE";
            const payload = {
                educationLevel: topLevelForm.educationLevel,
                gradeLevel: isGrade ? Number(topLevelForm.gradeLevel) : null,
                track: isGrade ? topLevelForm.track : "",
                trackEn: isGrade ? topLevelForm.trackEn : "",
                name: isGrade ? "" : topLevelForm.name,
                nameEn: isGrade ? "" : topLevelForm.nameEn,
                description: topLevelForm.description,
                descriptionEn: topLevelForm.descriptionEn,
            };
            if (topLevelForm.id) {
                await api.put(`/curriculum/topics/${topLevelForm.id}`, payload);
            } else {
                await api.post("/curriculum/topics", payload);
            }
            setTopLevelForm(null);
            await loadTopLevelTopics();
        } catch (e) {
            setTopicError(apiErrorMessage(e, t("curriculum.topicSaveError")));
        } finally {
            setSavingTopic(false);
        }
    };

    const saveArea = async () => {
        if (!areaForm || !selectedGrade) return;
        setSavingTopic(true);
        setTopicError("");
        try {
            // Grade level, track and education level are inherited from the
            // parent server-side, on create and on edit alike.
            const payload = {
                name: areaForm.name,
                nameEn: areaForm.nameEn,
                description: areaForm.description,
                descriptionEn: areaForm.descriptionEn,
                learningArea: areaForm.learningArea,
                // Always sent, so clearing the field clears the mapping.
                concepts: areaForm.concepts,
            };
            if (areaForm.id) {
                await api.put(`/curriculum/topics/${areaForm.id}`, payload);
            } else {
                await api.post(`/curriculum/topics/${selectedGrade.id}/children`, payload);
            }
            setAreaForm(null);
            await loadSubTopics(selectedGrade.id);
        } catch (e) {
            setTopicError(apiErrorMessage(e, t("curriculum.topicSaveError")));
        } finally {
            setSavingTopic(false);
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
            setTopicConcepts([]);
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
                        {selectedTopic && <> &rsaquo; {topicLabel(selectedTopic)}</>}
                    </span>
                </nav>
            )}

            {/* Level 1: Grade selection */}
            {!selectedGrade && (
                <div>
                    <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 mb-3">
                        <h5 className="mb-0">{t("curriculum.selectGrade")}</h5>
                        <div className="d-flex align-items-center gap-2">
                            {hasMixedEducationLevels(topLevelTopics) && (
                                <div className="btn-group btn-group-sm" role="group" aria-label={t("curriculum.filterLevel")}>
                                    {(["ALL", "SCHOOL", "UNIVERSITY"] as const).map(level => (
                                        <button
                                            key={level}
                                            type="button"
                                            className={`btn ${levelFilter === level ? "btn-secondary" : "btn-outline-secondary"}`}
                                            onClick={() => setLevelFilter(level)}
                                        >
                                            {level === "ALL" && t("curriculum.levelAll")}
                                            {level === "SCHOOL" && t("curriculum.levelSchool")}
                                            {level === "UNIVERSITY" && t("curriculum.levelUniversity")}
                                        </button>
                                    ))}
                                </div>
                            )}
                            {isAdmin && !topLevelForm && (
                                <button className="btn btn-sm btn-outline-primary" onClick={openNewTopLevel}>
                                    <i className="bi bi-plus-lg me-1"></i>{t("curriculum.addTopLevel")}
                                </button>
                            )}
                        </div>
                    </div>

                    {isAdmin && topLevelForm && (
                        <div className="border rounded p-3 mb-3 bg-light">
                            <h6 className="mb-3">
                                <i className={`bi ${topLevelForm.id ? "bi-pencil-square" : "bi-mortarboard"} me-2`}></i>
                                {topLevelForm.id ? t("curriculum.editTopLevel") : t("curriculum.newTopLevel")}
                            </h6>
                            {topicError && <div className="alert alert-danger py-2">{topicError}</div>}
                            <div className="row g-2">
                                <div className="col-md-6">
                                    <label className="form-label small fw-semibold">{t("curriculum.topLevelKind")}</label>
                                    <select className="form-select form-select-sm" value={topLevelForm.kind}
                                        onChange={e => setTopLevelKind(e.target.value as TopLevelForm["kind"])}>
                                        <option value="GRADE">{t("curriculum.kindGrade")}</option>
                                        <option value="MODULE">{t("curriculum.kindModule")}</option>
                                    </select>
                                </div>
                                <div className="col-md-6">
                                    <label className="form-label small fw-semibold">{t("curriculum.fieldEducationLevel")}</label>
                                    <select className="form-select form-select-sm" value={topLevelForm.educationLevel}
                                        onChange={e => setTopLevelForm({ ...topLevelForm, educationLevel: e.target.value as EducationLevel })}>
                                        <option value="SCHOOL">{t("curriculum.levelSchool")}</option>
                                        <option value="UNIVERSITY">{t("curriculum.levelUniversity")}</option>
                                    </select>
                                </div>

                                {topLevelForm.kind === "GRADE" ? (
                                    <>
                                        <div className="col-md-3">
                                            <label className="form-label small fw-semibold">{t("curriculum.fieldGradeLevel")}</label>
                                            <input className="form-control form-control-sm" type="number" min={1} max={99}
                                                value={topLevelForm.gradeLevel}
                                                onChange={e => setTopLevelForm({ ...topLevelForm, gradeLevel: e.target.value })} />
                                        </div>
                                        <div className="col-md-4">
                                            <label className="form-label small fw-semibold">{t("curriculum.fieldTrack")}</label>
                                            <input className="form-control form-control-sm" maxLength={30} value={topLevelForm.track}
                                                onChange={e => setTopLevelForm({ ...topLevelForm, track: e.target.value })} />
                                        </div>
                                        <div className="col-md-5">
                                            <label className="form-label small fw-semibold">{t("curriculum.fieldTrackEn")}</label>
                                            <input className="form-control form-control-sm" maxLength={30} value={topLevelForm.trackEn}
                                                onChange={e => setTopLevelForm({ ...topLevelForm, trackEn: e.target.value })} />
                                        </div>
                                        <div className="col-12">
                                            <div className="form-text mt-0">{t("curriculum.gradeLabelHint")}</div>
                                        </div>
                                    </>
                                ) : (
                                    <>
                                        <div className="col-md-6">
                                            <label className="form-label small fw-semibold">{t("curriculum.fieldName")}</label>
                                            <input className="form-control form-control-sm" value={topLevelForm.name}
                                                placeholder="Programmieren 1"
                                                onChange={e => setTopLevelForm({ ...topLevelForm, name: e.target.value })} />
                                        </div>
                                        <div className="col-md-6">
                                            <label className="form-label small fw-semibold">{t("curriculum.fieldNameEn")}</label>
                                            <input className="form-control form-control-sm" value={topLevelForm.nameEn}
                                                onChange={e => setTopLevelForm({ ...topLevelForm, nameEn: e.target.value })} />
                                        </div>
                                        <div className="col-12">
                                            <div className="form-text mt-0">{t("curriculum.kindModuleHint")}</div>
                                        </div>
                                    </>
                                )}

                                <div className="col-md-6">
                                    <label className="form-label small fw-semibold">{t("curriculum.fieldDescription")}</label>
                                    <textarea className="form-control form-control-sm" rows={2} value={topLevelForm.description}
                                        onChange={e => setTopLevelForm({ ...topLevelForm, description: e.target.value })} />
                                </div>
                                <div className="col-md-6">
                                    <label className="form-label small fw-semibold">{t("curriculum.fieldDescriptionEn")}</label>
                                    <textarea className="form-control form-control-sm" rows={2} value={topLevelForm.descriptionEn}
                                        onChange={e => setTopLevelForm({ ...topLevelForm, descriptionEn: e.target.value })} />
                                </div>
                                <div className="col-12">
                                    <div className="form-text mt-0">{t("curriculum.englishHint")}</div>
                                    {topLevelForm.id && (
                                        <div className="form-text">
                                            {t("curriculum.kindSwitchHint")} {t("curriculum.cascadeHint")}
                                        </div>
                                    )}
                                </div>
                            </div>
                            <div className="d-flex gap-2 mt-3">
                                <button className="btn btn-sm btn-primary" onClick={saveTopLevel}
                                    disabled={savingTopic || !topLevelFormIsComplete(topLevelForm)}>
                                    {savingTopic && <span className="spinner-border spinner-border-sm me-1" role="status" />}
                                    {t("curriculum.saveTopic")}
                                </button>
                                <button className="btn btn-sm btn-outline-secondary" onClick={() => setTopLevelForm(null)} disabled={savingTopic}>
                                    {t("curriculum.exampleCancel")}
                                </button>
                            </div>
                        </div>
                    )}

                    <div className="row g-3">
                        {visibleTopics.map(topic => (
                            <div key={topic.id} className="col-md-6 col-lg-4">
                                <div
                                    className="card h-100 border-0 shadow-sm"
                                    style={{ cursor: "pointer", transition: "transform 0.15s" }}
                                    onClick={() => selectGrade(topic)}
                                    onMouseEnter={e => (e.currentTarget.style.transform = "translateY(-2px)")}
                                    onMouseLeave={e => (e.currentTarget.style.transform = "")}
                                >
                                    <div className="card-body">
                                        <h6 className="card-title fw-semibold d-flex align-items-start justify-content-between gap-2">
                                            <span>
                                                <i className="bi bi-mortarboard me-2 text-primary"></i>
                                                {topicLabel(topic)}
                                            </span>
                                            {isAdmin && (
                                                <button
                                                    className="btn btn-sm btn-link text-secondary p-0"
                                                    title={t("curriculum.editEntry")}
                                                    aria-label={t("curriculum.editEntry")}
                                                    onClick={e => { e.stopPropagation(); openEditTopLevel(topic); }}
                                                >
                                                    <i className="bi bi-pencil"></i>
                                                </button>
                                            )}
                                        </h6>
                                        {description(topic) && (
                                            <p className="card-text text-muted small mb-0">{description(topic)}</p>
                                        )}
                                        {topic.track && (
                                            <span className="badge bg-secondary mt-2">{trackLabel(topic)}</span>
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
                    <div className="d-flex align-items-center justify-content-between mb-3">
                        <h5 className="mb-0">{t("curriculum.selectTopic")}</h5>
                        {isAdmin && !areaForm && (
                            <button className="btn btn-sm btn-outline-primary" onClick={openNewArea}>
                                <i className="bi bi-plus-lg me-1"></i>{t("curriculum.addArea")}
                            </button>
                        )}
                    </div>

                    {isAdmin && areaForm && (
                        <div className="border rounded p-3 mb-3 bg-light">
                            <h6 className="mb-3">
                                <i className={`bi ${areaForm.id ? "bi-pencil-square" : "bi-list-check"} me-2`}></i>
                                {areaForm.id ? t("curriculum.editArea") : t("curriculum.newArea")}
                            </h6>
                            {topicError && <div className="alert alert-danger py-2">{topicError}</div>}
                            <div className="row g-2">
                                <div className="col-md-6">
                                    <label className="form-label small fw-semibold">{t("curriculum.fieldName")}</label>
                                    <input className="form-control form-control-sm" value={areaForm.name}
                                        onChange={e => setAreaForm({ ...areaForm, name: e.target.value })} />
                                </div>
                                <div className="col-md-6">
                                    <label className="form-label small fw-semibold">{t("curriculum.fieldNameEn")}</label>
                                    <input className="form-control form-control-sm" value={areaForm.nameEn}
                                        onChange={e => setAreaForm({ ...areaForm, nameEn: e.target.value })} />
                                </div>
                                <div className="col-md-6">
                                    <label className="form-label small fw-semibold">{t("curriculum.fieldDescription")}</label>
                                    <textarea className="form-control form-control-sm" rows={2} value={areaForm.description}
                                        onChange={e => setAreaForm({ ...areaForm, description: e.target.value })} />
                                </div>
                                <div className="col-md-6">
                                    <label className="form-label small fw-semibold">{t("curriculum.fieldDescriptionEn")}</label>
                                    <textarea className="form-control form-control-sm" rows={2} value={areaForm.descriptionEn}
                                        onChange={e => setAreaForm({ ...areaForm, descriptionEn: e.target.value })} />
                                </div>
                                <div className="col-12">
                                    <div className="form-text mt-0">{t("curriculum.englishHint")}</div>
                                </div>
                                <div className="col-12">
                                    <label className="form-label small fw-semibold">{t("curriculum.fieldLearningArea")}</label>
                                    <input className="form-control form-control-sm" maxLength={255} value={areaForm.learningArea}
                                        onChange={e => setAreaForm({ ...areaForm, learningArea: e.target.value })} />
                                    <div className="form-text">{t("curriculum.fieldLearningAreaHint")}</div>
                                </div>
                                <div className="col-12">
                                    <label className="form-label small fw-semibold" htmlFor="areaConcepts">
                                        {t("curriculum.fieldConcepts")}
                                    </label>
                                    <ConceptPicker
                                        inputId="areaConcepts"
                                        value={areaForm.concepts}
                                        catalogue={conceptCatalogue}
                                        onChange={concepts => setAreaForm({ ...areaForm, concepts })}
                                    />
                                    <div className="form-text">{t("curriculum.conceptsHint")}</div>
                                    {areaForm.concepts.length === 0 && (
                                        <div className="form-text fst-italic">{t("curriculum.conceptsEmpty")}</div>
                                    )}
                                </div>
                            </div>
                            <div className="d-flex gap-2 mt-3">
                                <button className="btn btn-sm btn-primary" onClick={saveArea}
                                    disabled={savingTopic || !areaForm.name.trim()}>
                                    {savingTopic && <span className="spinner-border spinner-border-sm me-1" role="status" />}
                                    {t("curriculum.saveTopic")}
                                </button>
                                <button className="btn btn-sm btn-outline-secondary" onClick={() => setAreaForm(null)} disabled={savingTopic}>
                                    {t("curriculum.exampleCancel")}
                                </button>
                            </div>
                        </div>
                    )}

                    {subTopics.length === 0 ? (
                        <p className="text-muted">{t("curriculum.noSubTopics")}</p>
                    ) : (
                        <div className="list-group">
                            {subTopics.map(topic => (
                                <div key={topic.id} className="list-group-item d-flex align-items-start gap-2">
                                    <button
                                        className="btn btn-link text-body text-decoration-none text-start flex-grow-1 p-0 d-flex justify-content-between align-items-start"
                                        onClick={() => selectTopic(topic)}
                                    >
                                        <div>
                                            <h6 className="mb-1 fw-semibold">
                                                {topicLabel(topic)}
                                                <span
                                                    className="badge bg-secondary-subtle text-secondary-emphasis ms-2"
                                                    title={t("curriculum.sequenceCountTooltip", { count: topic.sequenceCount }) as string}
                                                >
                                                    {topic.sequenceCount}
                                                </span>
                                            </h6>
                                            {description(topic) && (
                                                <p className="mb-0 text-muted small">{description(topic)}</p>
                                            )}
                                        </div>
                                        <i className="bi bi-chevron-right text-muted ms-2"></i>
                                    </button>
                                    {isAdmin && (
                                        <button
                                            className="btn btn-sm btn-link text-secondary p-0"
                                            title={t("curriculum.editEntry")}
                                            aria-label={t("curriculum.editEntry")}
                                            onClick={() => openEditArea(topic)}
                                        >
                                            <i className="bi bi-pencil"></i>
                                        </button>
                                    )}
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            )}

            {/* Level 3: Topic detail + generation */}
            {selectedTopic && (
                <div>
                    {/* Jump to the Library, pre-filtered by this topic's mapped concepts — so it
                        surfaces both sequences generated from this topic and any others manually
                        tagged with the same concepts, not just this topic's own generations.
                        Open to everyone, including students/anonymous — it's a read-only link,
                        not a privileged action, so it sits outside the teacher-only generate card. */}
                    <div className="d-flex justify-content-end mb-3">
                        <span
                            className="d-inline-block"
                            title={topicConcepts.length === 0 ? (t("curriculum.viewSequencesDisabled") as string) : undefined}
                        >
                            <button
                                className="btn btn-sm btn-outline-primary"
                                disabled={topicConcepts.length === 0}
                                onClick={() => {
                                    const params = new URLSearchParams();
                                    params.set("concepts", topicConcepts.join(","));
                                    navigate(`/library?${params.toString()}`);
                                }}
                            >
                                <i className="bi bi-collection me-1"></i>
                                {t("curriculum.viewSequences")}
                            </button>
                        </span>
                    </div>

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
                                <div className="mb-3">
                                    <label className="form-label fw-semibold">
                                        <i className="bi bi-diagram-3 me-2"></i>
                                        {t("curriculum.fieldConcepts")}
                                    </label>
                                    {topicConcepts.length > 0 ? (
                                        <div className="d-flex flex-wrap gap-1">
                                            {topicConcepts.map(c => (
                                                <span key={c} className="badge bg-secondary-subtle text-secondary-emphasis">
                                                    {c}
                                                </span>
                                            ))}
                                        </div>
                                    ) : (
                                        <div className="text-muted small fst-italic">{t("curriculum.conceptsEmpty")}</div>
                                    )}
                                    <div className="form-text">
                                        {t("curriculum.conceptsAuto")}
                                        {isAdmin && ` ${t("curriculum.conceptsAdminHint")}`}
                                    </div>
                                </div>

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

                                {/* Generation needs curated seed examples: without them the
                                    backend answers 422 no_examples. Saying so on the button beats
                                    letting a teacher press it and read an error. The title sits on
                                    the wrapper because a disabled button fires no hover events. */}
                                <span
                                    className="d-inline-block w-100"
                                    title={examples.length === 0
                                        ? (isAdmin
                                            ? t("curriculum.generateDisabledNoExamplesAdmin")
                                            : t("curriculum.generateDisabledNoExamples"))
                                        : undefined}
                                >
                                    <button
                                        className="btn btn-primary w-100"
                                        onClick={handleGenerate}
                                        disabled={generating || examples.length === 0}
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
                                </span>
                                {examples.length === 0 && (
                                    <div className="form-text text-center mt-2">
                                        {isAdmin
                                            ? t("curriculum.generateDisabledNoExamplesAdmin")
                                            : t("curriculum.generateDisabledNoExamples")}
                                    </div>
                                )}
                            </div>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
