import { BrowserRouter, Routes, Route, useLocation, useNavigate, Link } from "react-router-dom";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import HomePage from "./pages/HomePage.tsx";
import SolvePage from "./pages/SolvePage.tsx";
import SequencePage from "./pages/SequencePage.tsx";
import HelpPage from "./pages/HelpPage.tsx";
import { useMatch } from "react-router-dom";
import WorksheetPage from "./pages/WorksheetPage.tsx";
import { AuthProvider, useAuth } from "./context/AuthContext";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import VerifyEmailPage from "./pages/VerifyEmailPage";
import ForgotPasswordPage from "./pages/ForgotPasswordPage";
import ResetPasswordPage from "./pages/ResetPasswordPage";
import StudentOnboardingPage from "./pages/StudentOnboardingPage";
import ProfilePage from "./pages/ProfilePage";
import ClassesPage from "./pages/ClassesPage";
import ClassDetailPage from "./pages/ClassDetailPage";
import StudentClassesPage from "./pages/StudentClassesPage";
import AdminUsersPage from "./pages/AdminUsersPage";
import AdminLibraryPage from "./pages/AdminLibraryPage";
import AdminDatabasePage from "./pages/AdminDatabasePage";
import ProtectedRoute from "./components/ProtectedRoute";
import ManualTaskCreator from "./pages/ManualTaskCreator";
import CurriculumPage from "./pages/CurriculumPage";
import LibraryPage from "./pages/LibraryPage";
import ChangePasswordPage from "./pages/ChangePasswordPage";


function FloatingHelpButton() {
    const location = useLocation();
    const navigate = useNavigate();
    const [visible, setVisible] = useState(true);

    // Hooks müssen konstant aufgerufen werden (Rules of Hooks):
    const matchSolve      = useMatch("/solve/:taskId");
    const matchSeq        = useMatch("/sequence/:id");
    const matchLibrary    = useMatch("/library");
    const matchCurriculum = useMatch("/curriculum");
    const matchClasses    = useMatch("/classes");
    const matchClassDetail = useMatch("/classes/:id");
    const matchWorksheet  = useMatch("/worksheets/:sheetId");
    const matchManual     = useMatch("/manual-create");
    const matchMyClasses  = useMatch("/my-classes");
    const matchAdminUsers = useMatch("/admin/users");
    const matchAdminLib   = useMatch("/admin/library");
    const matchAdminDb    = useMatch("/admin/database");
    const matchProfile    = useMatch("/profile");
    const matchHelpRoot   = useMatch("/help");
    const matchHelpAny    = useMatch("/help/*");

    const onHelp = !!matchHelpRoot || !!matchHelpAny;

    const context =
        matchSolve                        ? "solve"         :
        matchSeq                          ? "sequence"      :
        matchLibrary                      ? "library"       :
        matchCurriculum                   ? "curriculum"    :
        (matchClasses || matchClassDetail)? "classes"       :
        matchWorksheet                    ? "worksheets"    :
        matchManual                       ? "manual-create" :
        matchMyClasses                    ? "my-classes"    :
        (matchAdminUsers || matchAdminLib || matchAdminDb)? "admin" :
        matchProfile                      ? "auth"          :
        "home";

    useEffect(() => setVisible(!onHelp), [onHelp]);

    if (!visible) return null;

    const relPath = location.pathname.replace(import.meta.env.BASE_URL, "/");
    const fromParam = encodeURIComponent(relPath + location.search);

    return (
        <button
            className="btn btn-outline-secondary position-fixed"
            style={{
                bottom: "clamp(1rem, 2vw, 2rem)",
                left: "clamp(1rem, 2vw, 2rem)",
                zIndex: 1050,
                borderRadius: "50%",
                width: "3rem",
                height: "3rem",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                boxShadow: "0 2px 6px rgba(0,0,0,0.15)",
                backgroundColor: "white",
            }}
            title="Help"
            onClick={() => navigate(`/help/${context}?from=${fromParam}`)}
        >
            <i className="bi bi-question-circle" style={{ fontSize: "1.3rem" }}></i>
        </button>
    );
}

function LanguageToggle() {
    const { i18n } = useTranslation();
    const current = i18n.language?.startsWith("en") ? "en" : "de";
    const toggle = () => i18n.changeLanguage(current === "de" ? "en" : "de");

    return (
        <button
            className="btn btn-sm btn-outline-secondary"
            onClick={toggle}
            title={current === "de" ? "Switch to English" : "Zu Deutsch wechseln"}
        >
            {current === "de" ? "EN" : "DE"}
        </button>
    );
}

function Navbar() {
    const { user, logout, isLoggedIn, isStudent, isAdmin } = useAuth();
    const navigate = useNavigate();
    const { t } = useTranslation();

    return (
        <nav className="navbar navbar-expand-sm navbar-light bg-white border-bottom px-3 py-2">
            <Link className="navbar-brand d-flex align-items-center gap-2" to="/">
                <img
                    src={`${import.meta.env.BASE_URL}codeowllogo.png`}
                    alt="CodeOwl"
                    width="32"
                    height="auto"
                />
                <span className="fw-semibold">CodeOwl</span>
            </Link>

            <div className="ms-auto d-flex align-items-center gap-2 flex-wrap justify-content-end">
                <LanguageToggle />
                {isLoggedIn ? (
                    <>
                        {isAdmin && (
                            <>
                                <Link to="/admin/users" className="btn btn-sm btn-outline-danger">
                                    <i className="bi bi-shield-lock me-1"></i>
                                    {t("nav.userManagement")}
                                </Link>
                                <Link to="/admin/library" className="btn btn-sm btn-outline-danger">
                                    <i className="bi bi-tags me-1"></i>
                                    {t("nav.libraryAdmin")}
                                </Link>
                                <Link to="/admin/database" className="btn btn-sm btn-outline-danger">
                                    <i className="bi bi-database me-1"></i>
                                    {t("nav.databaseTools")}
                                </Link>
                            </>
                        )}
                        {!isStudent && (
                            <>
                                <Link to="/library" className="btn btn-sm btn-outline-primary">
                                    <i className="bi bi-collection me-1"></i>
                                    {t("nav.library")}
                                </Link>
                                <Link to="/manual-create" className="btn btn-sm btn-outline-success">
                                    <i className="bi bi-pencil-square me-1"></i>
                                    {t("nav.manualCreator")}
                                </Link>
                                <Link to="/curriculum" className="btn btn-sm btn-outline-info">
                                    <i className="bi bi-book me-1"></i>
                                    {t("nav.curriculum")}
                                </Link>
                                <Link to="/classes" className="btn btn-sm btn-outline-secondary">
                                    <i className="bi bi-people me-1"></i>
                                    {t("nav.classes")}
                                </Link>
                            </>
                        )}
                        {isStudent && (
                            <Link to="/my-classes" className="btn btn-sm btn-outline-secondary">
                                <i className="bi bi-mortarboard me-1"></i>
                                {t("nav.myClasses")}
                            </Link>
                        )}
                        <Link to="/profile" className="btn btn-sm btn-outline-secondary">
                            <i className="bi bi-person-circle me-1"></i>
                            {user!.firstName}
                        </Link>
                        <button
                            className="btn btn-sm btn-outline-danger"
                            onClick={() => { logout(); navigate("/"); }}
                        >
                            {t("nav.logout")}
                        </button>
                    </>
                ) : (
                    <Link to="/login" className="btn btn-sm btn-outline-primary">
                        {t("nav.login")}
                    </Link>
                )}
            </div>
        </nav>
    );
}

function AppFooter() {
    const { t } = useTranslation();
    return (
        <footer
            className="text-center py-3 border-top mt-auto bg-light"
            style={{ fontSize: "0.85rem", color: "#6c757d" }}
        >
            <div className="container d-flex flex-column flex-sm-row align-items-center justify-content-center gap-3">
                <img
                    src={`${import.meta.env.BASE_URL}codeowllogo.png`}
                    alt="CodeOwl Logo"
                    width="60"
                    height="auto"
                    className="opacity-75"
                    style={{ filter: "drop-shadow(0 0 3px rgba(0,0,0,0.1))" }}
                />
                <span className="text-muted d-none d-sm-inline">|</span>
                <div className="dropdown">
                    <button
                        className="btn btn-sm btn-outline-secondary dropdown-toggle"
                        type="button"
                        id="surveyDropdown"
                        data-bs-toggle="dropdown"
                        aria-expanded="false"
                    >
                        {t("nav.feedback")}
                    </button>
                    <ul className="dropdown-menu" aria-labelledby="surveyDropdown">
                        <li>
                            <a className="dropdown-item" href="https://forms.gle/ZdRYMc8uphyuT6Bu5" target="_blank" rel="noopener noreferrer">
                                {t("nav.teacherSurvey")}
                            </a>
                        </li>
                        <li>
                            <a className="dropdown-item" href="https://forms.gle/5tJYYqiswLUm6fW86" target="_blank" rel="noopener noreferrer">
                                {t("nav.studentSurvey")}
                            </a>
                        </li>
                    </ul>
                </div>
            </div>
        </footer>
    );
}

export default function App() {
    return (
        <BrowserRouter basename={import.meta.env.BASE_URL}>
            <AuthProvider>
                <div className="d-flex flex-column min-vh-100">
                    <Navbar />

                    <main className="flex-fill">
                        <Routes>
                            {/* Public routes */}
                            <Route path="/" element={<HomePage />} />
                            <Route path="/sequence/:id" element={<SequencePage />} />
                            <Route path="/solve/:taskId" element={<SolvePage />} />
                            <Route path="/worksheets/:sheetId" element={<WorksheetPage />} />
                            <Route path="/help" element={<HelpPage />} />
                            <Route path="/help/:context" element={<HelpPage />} />

                            {/* Temp-password change (accessible while logged in) */}
                            <Route path="/change-password" element={<ChangePasswordPage />} />

                            {/* Auth routes */}
                            <Route path="/login" element={<LoginPage />} />
                            <Route path="/register" element={<RegisterPage />} />
                            <Route path="/verify-email" element={<VerifyEmailPage />} />
                            <Route path="/forgot-password" element={<ForgotPasswordPage />} />
                            <Route path="/reset-password" element={<ResetPasswordPage />} />
                            <Route path="/student-onboarding" element={<StudentOnboardingPage />} />

                            {/* Protected routes */}
                            <Route path="/profile" element={<ProtectedRoute><ProfilePage /></ProtectedRoute>} />
                            <Route path="/classes" element={<ProtectedRoute><ClassesPage /></ProtectedRoute>} />
                            <Route path="/classes/:id" element={<ProtectedRoute><ClassDetailPage /></ProtectedRoute>} />
                            <Route path="/my-classes" element={<ProtectedRoute><StudentClassesPage /></ProtectedRoute>} />

                            {/* Admin routes */}
                            <Route path="/admin/users" element={<ProtectedRoute requiredRole="ADMIN"><AdminUsersPage /></ProtectedRoute>} />
                            <Route path="/admin/library" element={<ProtectedRoute requiredRole="ADMIN"><AdminLibraryPage /></ProtectedRoute>} />
                            <Route path="/admin/database" element={<ProtectedRoute requiredRole="ADMIN"><AdminDatabasePage /></ProtectedRoute>} />

                            {/* Teacher tools */}
                            <Route path="/manual-create" element={<ProtectedRoute><ManualTaskCreator /></ProtectedRoute>} />
                            <Route path="/curriculum" element={<ProtectedRoute><CurriculumPage /></ProtectedRoute>} />
                            <Route path="/library" element={<ProtectedRoute><LibraryPage /></ProtectedRoute>} />
                        </Routes>
                    </main>

                    {/* Universal floating help button */}
                    <FloatingHelpButton />

                    <AppFooter />
                </div>
            </AuthProvider>
        </BrowserRouter>
    );
}
