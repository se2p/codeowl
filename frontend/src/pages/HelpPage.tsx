import type { ReactNode } from "react";
import { useParams, useLocation, useNavigate, Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { usePageTitle } from "../hooks/usePageTitle.ts";
import { useAuth } from "../context/AuthContext.tsx";
import { useHomePath } from "../hooks/useHomePath.ts";

export default function HelpPage() {
    const { t } = useTranslation();
    usePageTitle(t("help.common.pageTitle"));
    const { context } = useParams<{ context?: string }>();
    const location = useLocation();
    const navigate = useNavigate();
    const { isTeacher, isAdmin } = useAuth();
    const homePath = useHomePath();

    const searchParams = new URLSearchParams(location.search);
    const fromParam = searchParams.get("from");
    const isTeacherMode = isTeacher || isAdmin;

    const goBack = () => {
        if (fromParam) {
            const decoded = decodeURIComponent(fromParam);
            navigate(decoded);
        } else navigate(homePath);
    };

    // =====================================================================
    // i18n RENDER HELPERS
    // Body text lives in the `help` namespace and may contain inline markup
    // (<strong>, <em>, <code>, <kbd>), so it is injected as HTML — the same
    // convention WorksheetPage uses for worksheet.tipN. List and table
    // content is stored as arrays via i18next's returnObjects.
    // =====================================================================
    const html = (s: string) => ({ __html: s });

    const strs = (key: string): string[] => {
        const value = t(key, { returnObjects: true }) as unknown;
        return Array.isArray(value) ? (value as string[]) : [];
    };

    const rows = (key: string): string[][] => {
        const value = t(key, { returnObjects: true }) as unknown;
        return Array.isArray(value) ? (value as string[][]) : [];
    };

    const p = (key: string) => <p dangerouslySetInnerHTML={html(t(key))} />;

    const ul = (key: string) => (
        <ul>
            {strs(key).map((item, i) => (
                <li key={i} dangerouslySetInnerHTML={html(item)} />
            ))}
        </ul>
    );

    const ol = (key: string) => (
        <ol>
            {strs(key).map((item, i) => (
                <li key={i} dangerouslySetInnerHTML={html(item)} />
            ))}
        </ol>
    );

    const table = (headKey: string, rowsKey: string, className = "table table-bordered table-sm") => (
        <table className={className}>
            <thead className="table-light">
                <tr>
                    {strs(headKey).map((h, i) => (
                        <th key={i}>{h}</th>
                    ))}
                </tr>
            </thead>
            <tbody>
                {rows(rowsKey).map((row, i) => (
                    <tr key={i}>
                        {row.map((cell, j) => (
                            <td key={j} dangerouslySetInnerHTML={html(cell)} />
                        ))}
                    </tr>
                ))}
            </tbody>
        </table>
    );

    const betaBadge = (
        <span className="badge bg-warning text-dark" style={{ fontSize: "0.7rem" }}>
            {t("help.common.beta")}
        </span>
    );

    // =====================================================================
    // FULL OVERVIEW PAGE (no context param)
    // =====================================================================
    if (!context) {
        const category = (key: string) => (
            <h5 className="text-uppercase text-muted mb-3" style={{ letterSpacing: 1, fontSize: "0.75rem" }}>
                {t(`help.overview.${key}`)}
            </h5>
        );

        const card = (
            id: string,
            emoji: string,
            to: string,
            opts: { spacing?: string; variant?: string } = {},
        ) => (
            <section className={`mb-${opts.spacing ?? "4"}`}>
                <h2>
                    {emoji} {t(`help.overview.${id}Title`)}
                </h2>
                {p(`help.overview.${id}Body`)}
                {ul(`help.overview.${id}Items`)}
                <Link to={to} className={`btn btn-sm btn-outline-${opts.variant ?? "secondary"} mt-1`}>
                    {t("help.common.learnMore")} →
                </Link>
            </section>
        );

        return (
            <div className="container py-4" style={{ maxWidth: 960 }}>
                <h1 className="mb-2">🦉 {t("help.overview.title")}</h1>
                <p className="lead text-muted mb-4">{t("help.overview.lead")}</p>

                <hr className="mb-4" />

                {/* ---- GETTING STARTED ---- */}
                {category("catGettingStarted")}
                {card("auth", "🔐", "/help/auth")}
                {card("home", "🏠", "/help/home")}
                {card("manual", "✏️", "/help/manual-create")}

                <hr className="my-4" />
                {/* ---- CLASSROOM MANAGEMENT ---- */}
                {category("catClassroom")}
                {card("classes", "🏫", "/help/classes")}
                {card("myClasses", "🎓", "/help/my-classes")}
                {card("worksheets", "📝", "/help/worksheets")}

                <hr className="my-4" />
                {/* ---- FINDING & REUSING CONTENT ---- */}
                {category("catContent")}
                {card("library", "📚", "/help/library")}
                {card("curriculum", "🎓", "/help/curriculum")}
                {card("marketplace", "🛒", "/help/marketplace")}

                <hr className="my-4" />
                {/* ---- SEQUENCES & TASKS ---- */}
                {category("catSequences")}
                {card("sequence", "📋", "/help/sequence")}
                {card("solve", "🧩", "/help/solve")}

                <hr className="my-4" />
                {/* ---- ADVANCED FEATURES ---- */}
                {category("catAdvanced")}
                {card("hints", "💡", "/help/hints")}
                {card("distractors", "🎭", "/help/distractors")}
                {card("teacher", "👩‍🏫", "/help/teacher", { spacing: "5" })}

                {isAdmin && (
                    <>
                        <hr className="my-4" />
                        {category("catAdmin")}
                        {card("admin", "🔒", "/help/admin", { spacing: "5", variant: "danger" })}
                    </>
                )}

                <hr className="my-4" />
                <button onClick={goBack} className="btn btn-outline-secondary">
                    ← {t("help.common.back")}
                </button>
            </div>
        );
    }

    // =====================================================================
    // CONTEXT-SPECIFIC HELP
    // =====================================================================
    const renderContent = (): ReactNode => {
        switch (context) {

            // ------------------------------------------------------------------
            case "auth":
                return (
                    <>
                        <h2>🔐 {t("help.auth.title")}</h2>
                        {p("help.auth.intro")}

                        <h5>👥 {t("help.auth.rolesTitle")}</h5>
                        {ul("help.auth.roles")}

                        <h5>📝 {t("help.auth.registrationTitle")}</h5>
                        {ul("help.auth.registration")}

                        <h5>🎓 {t("help.auth.onboardingTitle")}</h5>
                        {p("help.auth.onboarding")}

                        <h5>🔑 {t("help.auth.passwordTitle")}</h5>
                        {ul("help.auth.password")}

                        <h5>🔒 {t("help.auth.securityTitle")}</h5>
                        {ul("help.auth.security")}
                    </>
                );

            // ------------------------------------------------------------------
            case "home":
                return (
                    <>
                        <h2>🏠 {t("help.home.title")}</h2>
                        {p("help.home.intro")}

                        <h5>💡 {t("help.home.inputsTitle")}</h5>
                        {ul("help.home.inputs")}

                        <h5>🎛️ {t("help.home.optionsTitle")}</h5>
                        <ul>
                            {strs("help.home.options").map((item, i) => (
                                <li key={i} dangerouslySetInnerHTML={html(item)} />
                            ))}
                            <li>
                                <strong>{t("help.home.contextModeLabel")}</strong>{" "}
                                {betaBadge}
                                {" — "}
                                <span dangerouslySetInnerHTML={html(t("help.home.contextModeBody"))} />
                            </li>
                            <li>
                                <strong>{t("help.home.inlineSplitLabel")}</strong>{" "}
                                {betaBadge}
                                {" — "}
                                <span dangerouslySetInnerHTML={html(t("help.home.inlineSplitBody"))} />
                            </li>
                        </ul>

                        <h5>⚙️ {t("help.home.advancedTitle")}</h5>
                        {ul("help.home.advanced")}

                        <h5>🧰 {t("help.home.controlsTitle")}</h5>
                        {ul("help.home.controls")}

                        <h5>👩‍🏫 {t("help.home.tipsTitle")}</h5>
                        {ul("help.home.tips")}
                    </>
                );

            // ------------------------------------------------------------------
            case "classes":
                return (
                    <>
                        <h2>🏫 {t("help.classes.title")}</h2>
                        {p("help.classes.intro")}

                        <h5>📋 {t("help.classes.overviewTitle")}</h5>
                        {ul("help.classes.overview")}

                        <h5>➕ {t("help.classes.createTitle")}</h5>
                        {p("help.classes.createBody")}
                        {ul("help.classes.create")}

                        <h5>📝 {t("help.classes.assignTitle")}</h5>
                        {ul("help.classes.assign")}

                        <h5>📊 {t("help.classes.trackingTitle")}</h5>
                        {p("help.classes.trackingBody")}
                        {ul("help.classes.tracking")}

                        <h5>📈 {t("help.classes.progressTitle")}</h5>
                        {ul("help.classes.progress")}

                        <h5>🔑 {t("help.classes.permissionsTitle")}</h5>
                        {ul("help.classes.permissions")}
                    </>
                );

            // ------------------------------------------------------------------
            case "worksheets":
                return (
                    <>
                        <h2>📝 {t("help.worksheets.title")}</h2>
                        {p("help.worksheets.intro")}

                        <h5>📋 {t("help.worksheets.overviewTitle")}</h5>
                        {ul("help.worksheets.overview")}

                        <h5>👁️ {t("help.worksheets.visibilityTitle")}</h5>
                        {ul("help.worksheets.visibility")}

                        <h5>📅 {t("help.worksheets.scheduleTitle")}</h5>
                        {p("help.worksheets.scheduleBody")}
                        {ul("help.worksheets.schedule")}

                        <h5>🔧 {t("help.worksheets.hintsTitle")}</h5>
                        {p("help.worksheets.hintsBody")}
                        {ul("help.worksheets.hints")}

                        <h5>📤 {t("help.worksheets.exportTitle")}</h5>
                        {ul("help.worksheets.export")}

                        <h5>📋 {t("help.worksheets.copyTitle")}</h5>
                        {ul("help.worksheets.copy")}
                    </>
                );

            // ------------------------------------------------------------------
            case "library":
                return (
                    <>
                        <h2>📚 {t("help.library.title")}</h2>
                        {p("help.library.intro")}

                        <h5>🔍 {t("help.library.searchTitle")}</h5>
                        {ul("help.library.search")}

                        <h5>🏷️ {t("help.library.tagsTitle")}</h5>
                        {p("help.library.tagsBody")}
                        {ul("help.library.tags")}
                        {p("help.library.filtersBody")}
                        {p("help.library.filtersUrl")}

                        <h5>🧩 {t("help.library.conceptsTitle")}</h5>
                        {p("help.library.conceptsBody")}
                        {ul("help.library.concepts")}

                        <h5>📋 {t("help.library.copyTitle")}</h5>
                        {ul("help.library.copy")}

                        <h5>🎯 {t("help.library.addToWorksheetTitle")}</h5>
                        {ul("help.library.addToWorksheet")}

                        <h5>☑️ {t("help.library.bulkTitle")}</h5>
                        {ul("help.library.bulk")}

                        <h5>✏️ {t("help.library.editTitle")}</h5>
                        {ul("help.library.edit")}

                        <h5>🔗 {t("help.library.curriculumTitle")}</h5>
                        {p("help.library.curriculumBody")}
                    </>
                );

            // ------------------------------------------------------------------
            case "marketplace":
                return (
                    <>
                        <h2>🛒 {t("help.marketplace.title")}</h2>
                        {p("help.marketplace.intro")}

                        <h5>🔍 {t("help.marketplace.searchTitle")}</h5>
                        {ul("help.marketplace.search")}

                        <h5>📋 {t("help.marketplace.copyTitle")}</h5>
                        {ul("help.marketplace.copy")}

                        <h5>📤 {t("help.marketplace.publishingTitle")}</h5>
                        {ul("help.marketplace.publishing")}
                    </>
                );

            // ------------------------------------------------------------------
            case "curriculum":
                return (
                    <>
                        <h2>🎓 {t("help.curriculum.title")}</h2>
                        {p("help.curriculum.intro")}
                        <div
                            className="alert alert-info d-inline-block py-1 px-3 mb-3"
                            style={{ fontSize: "0.875rem" }}
                            dangerouslySetInnerHTML={html(t("help.curriculum.betaNote"))}
                        />

                        <h5>🌳 {t("help.curriculum.treeTitle")}</h5>
                        {ul("help.curriculum.tree")}

                        <h5>🚀 {t("help.curriculum.generateTitle")}</h5>
                        {p("help.curriculum.generateBody")}
                        {ul("help.curriculum.generate")}
                        {p("help.curriculum.generateAfter")}

                        <h5>🔍 {t("help.curriculum.viewTitle")}</h5>
                        {p("help.curriculum.viewBody")}

                        <h5>👩‍🏫 {t("help.curriculum.tipsTitle")}</h5>
                        {ul("help.curriculum.tips")}

                        {isAdmin && (
                            <>
                                <h5>🔒 {t("help.curriculum.adminTitle")}</h5>
                                {ul("help.curriculum.admin")}
                            </>
                        )}
                    </>
                );

            // ------------------------------------------------------------------
            case "sequence":
                return (
                    <>
                        <h2>📋 {t("help.sequence.title")}</h2>
                        {p("help.sequence.intro")}

                        <h5>🔍 {t("help.sequence.overviewTitle")}</h5>
                        {ul("help.sequence.overview")}

                        <h5>🎯 {t("help.sequence.actionsTitle")}</h5>
                        {ul("help.sequence.actions")}

                        <h5>👩‍🏫 {t("help.sequence.teacherTitle")}</h5>
                        {ul("help.sequence.teacher")}

                        <h5>🧑‍🎓 {t("help.sequence.studentTitle")}</h5>
                        {ul("help.sequence.student")}

                        <h5>✏️ {t("help.sequence.editTitle")}</h5>
                        {p("help.sequence.editBody")}
                        {ul("help.sequence.edit")}

                        <h5>🔎 {t("help.sequence.similarTitle")}</h5>
                        {p("help.sequence.similarBody")}
                    </>
                );

            // ------------------------------------------------------------------
            case "solve":
                return (
                    <>
                        <h2>🧩 {t("help.solve.title")}</h2>
                        {p("help.solve.intro")}

                        <h5>⚙️ {t("help.solve.howTitle")}</h5>
                        {ul("help.solve.how")}

                        <h5>🖱️ {t("help.solve.modesTitle")}</h5>
                        {ul("help.solve.modes")}

                        <h5>🎨 {t("help.solve.colorsTitle")}</h5>
                        {ul("help.solve.colors")}

                        <h5>🎭 {t("help.solve.distractorTitle")}</h5>
                        {p("help.solve.distractorBody")}
                        {ul("help.solve.distractor")}

                        <h5>🔀 {t("help.solve.interchangeableTitle")}</h5>
                        {p("help.solve.interchangeableBody")}

                        <h5>✍️ {t("help.solve.gapTitle")}</h5>
                        {p("help.solve.gapBody")}

                        <h5>
                            🧩 {t("help.solve.inlineSplitTitle")}{" "}
                            {betaBadge}
                        </h5>
                        {ul("help.solve.inlineSplit")}

                        <h5>
                            📐 {t("help.solve.contextTitle")}{" "}
                            {betaBadge}
                        </h5>
                        {p("help.solve.contextBody")}

                        <h5>💡 {t("help.solve.hintsTitle")}</h5>
                        {p("help.solve.hintsBody")}
                        {ul("help.solve.hints")}

                        <h5>✅ {t("help.solve.completionTitle")}</h5>
                        {p("help.solve.completionBody")}
                        {ul("help.solve.completion")}

                        <h5>💾 {t("help.solve.resumeTitle")}</h5>
                        {ul("help.solve.resume")}

                        <h5>🧭 {t("help.solve.navTitle")}</h5>
                        {ul("help.solve.nav")}

                        {isTeacherMode ? (
                            <>
                                <h5>👩‍🏫 {t("help.solve.teacherToolsTitle")}</h5>
                                {ul("help.solve.teacherTools")}
                            </>
                        ) : (
                            <>
                                <h5>🧑‍🎓 {t("help.solve.studentTipsTitle")}</h5>
                                {ul("help.solve.studentTips")}
                            </>
                        )}
                    </>
                );

            // ------------------------------------------------------------------
            case "hints":
                return (
                    <>
                        <h2>💡 {t("help.hints.title")}</h2>
                        {p("help.hints.intro")}
                        <div
                            className="alert alert-secondary d-inline-block py-1 px-3 mb-3"
                            style={{ fontSize: "0.875rem" }}
                            dangerouslySetInnerHTML={html(t("help.hints.principle"))}
                        />

                        <h5>🔁 {t("help.hints.howTitle")}</h5>
                        {ol("help.hints.how")}

                        <h5>🛠️ {t("help.hints.toolsTitle")}</h5>
                        {ul("help.hints.tools")}

                        <h5>📊 {t("help.hints.levelsTitle")}</h5>
                        {table("help.hints.levelsHead", "help.hints.levelsRows", "table table-bordered table-sm mt-2")}
                        <p
                            className="text-muted"
                            style={{ fontSize: "0.875rem" }}
                            dangerouslySetInnerHTML={html(t("help.hints.levelsNote"))}
                        />

                        <h5>🎭 {t("help.hints.distractorTitle")}</h5>
                        {p("help.hints.distractorBody")}
                        {ul("help.hints.distractor")}

                        <h5>⚠️ {t("help.hints.edgeTitle")}</h5>
                        {ul("help.hints.edge")}

                        <h5>👩‍🏫 {t("help.hints.configTitle")}</h5>
                        {p("help.hints.configBody")}
                        {ul("help.hints.config")}
                    </>
                );

            // ------------------------------------------------------------------
            case "distractors":
                return (
                    <>
                        <h2>🎭 {t("help.distractors.title")}</h2>
                        {p("help.distractors.intro")}

                        <h5>🏷️ {t("help.distractors.taxonomyTitle")}</h5>
                        {table("help.distractors.taxonomyHead", "help.distractors.taxonomyRows")}

                        <h5>🤖 {t("help.distractors.generationTitle")}</h5>
                        {ol("help.distractors.generation")}
                        {p("help.distractors.generationNote")}

                        <h5>👩‍🏫 {t("help.distractors.controlsTitle")}</h5>
                        {ul("help.distractors.controls")}

                        <h5>🧑‍🎓 {t("help.distractors.tipsTitle")}</h5>
                        {ul("help.distractors.tips")}
                    </>
                );

            // ------------------------------------------------------------------
            case "teacher":
                return (
                    <>
                        <h2>👩‍🏫 {t("help.teacher.title")}</h2>
                        {p("help.teacher.intro")}

                        <h5>🔄 {t("help.teacher.toggleTitle")}</h5>
                        {ul("help.teacher.toggle")}

                        <h5>📋 {t("help.teacher.sequenceTitle")}</h5>
                        {ul("help.teacher.sequence")}

                        <h5>🧩 {t("help.teacher.solveTitle")}</h5>
                        {ul("help.teacher.solve")}

                        <h5>✏️ {t("help.teacher.editTitle")}</h5>
                        {ul("help.teacher.edit")}

                        <h5>📊 {t("help.teacher.dataTitle")}</h5>
                        {p("help.teacher.dataBody")}
                        {ul("help.teacher.data")}
                        <p
                            className="text-muted"
                            style={{ fontSize: "0.875rem" }}
                            dangerouslySetInnerHTML={html(t("help.teacher.dataNote"))}
                        />

                        {isAdmin && (
                            <>
                                <h5>🔒 {t("help.teacher.adminTitle")}</h5>
                                {ul("help.teacher.admin")}
                                <p>
                                    <Link to="/help/admin">→ {t("help.teacher.adminLink")}</Link>
                                </p>
                            </>
                        )}
                    </>
                );

            // ------------------------------------------------------------------
            case "manual-create":
                return (
                    <>
                        <h2>✏️ {t("help.manualCreate.title")}</h2>
                        {p("help.manualCreate.intro")}

                        <h5>📋 {t("help.manualCreate.workflowTitle")}</h5>
                        {ol("help.manualCreate.workflow")}

                        <h5>🧩 {t("help.manualCreate.blocksTitle")}</h5>
                        {p("help.manualCreate.blocksBody")}
                        {ul("help.manualCreate.blocks")}

                        <h5>🔢 {t("help.manualCreate.tiersTitle")}</h5>
                        {ul("help.manualCreate.tiers")}

                        <h5>🆚 {t("help.manualCreate.compareTitle")}</h5>
                        {table("help.manualCreate.compareHead", "help.manualCreate.compareRows", "table table-bordered table-sm mt-2")}

                        <h5>💡 {t("help.manualCreate.tipsTitle")}</h5>
                        {ul("help.manualCreate.tips")}
                    </>
                );

            // ------------------------------------------------------------------
            case "my-classes":
                return (
                    <>
                        <h2>🎓 {t("help.myClasses.title")}</h2>
                        {p("help.myClasses.intro")}

                        <h5>🗂️ {t("help.myClasses.navTitle")}</h5>
                        {ol("help.myClasses.nav")}

                        <h5>❓ {t("help.myClasses.faqTitle")}</h5>
                        {ul("help.myClasses.faq")}

                        <h5>🔐 {t("help.myClasses.accountTitle")}</h5>
                        {ul("help.myClasses.account")}
                    </>
                );

            // ------------------------------------------------------------------
            case "admin":
                if (!isAdmin) {
                    return <p>{t("help.common.adminOnly")}</p>;
                }
                return (
                    <>
                        <h2>🔒 {t("help.admin.title")}</h2>
                        {p("help.admin.intro")}

                        <h5>👥 {t("help.admin.usersTitle")} <code>/admin/users</code></h5>
                        {ul("help.admin.users")}

                        <h5>🏷️ {t("help.admin.libraryTitle")} <code>/admin/library</code></h5>
                        {ul("help.admin.library")}

                        <h5>🗄️ {t("help.admin.databaseTitle")} <code>/admin/database</code></h5>
                        {ul("help.admin.database")}

                        <h5>📊 {t("help.admin.researchTitle")} <code>/admin/research</code></h5>
                        {ul("help.admin.research")}

                        <h5>⚠️ {t("help.admin.cautionTitle")}</h5>
                        {ul("help.admin.caution")}
                    </>
                );

            // ------------------------------------------------------------------
            default:
                return <p>{t("help.common.notFound")}</p>;
        }
    };

    return (
        <div className="container py-4" style={{ maxWidth: 840 }}>
            {renderContent()}
            <hr className="my-4" />
            <div className="d-flex flex-wrap gap-2">
                <button onClick={goBack} className="btn btn-outline-secondary">
                    ← {t("help.common.back")}
                </button>
                <Link to="/help" className="btn btn-link ms-auto">
                    {t("help.common.fullOverview")} →
                </Link>
            </div>
        </div>
    );
}
