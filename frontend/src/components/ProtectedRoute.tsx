import { Navigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "../context/AuthContext";
import { homePathFor } from "../lib/homePath.ts";
import type { UserRole } from "../types/models";

interface ProtectedRouteProps {
    children: React.ReactNode;
    requiredRole?: UserRole;
    /** Teachers and admins only: students are sent to their own start page. */
    staffOnly?: boolean;
}

export default function ProtectedRoute({ children, requiredRole, staffOnly }: ProtectedRouteProps) {
    const { t } = useTranslation();
    const { user, loading, isLoggedIn, isStudent, mustChangePassword } = useAuth();

    if (loading) {
        return (
            <div className="d-flex justify-content-center align-items-center" style={{ minHeight: "60vh" }}>
                <div className="spinner-border text-primary" role="status">
                    <span className="visually-hidden">{t("common.loading")}</span>
                </div>
            </div>
        );
    }

    if (!isLoggedIn) {
        // Absolute: a relative "login" resolves against the current route, so
        // from /admin/users it aimed at a path that matches nothing.
        return <Navigate to="/login" replace />;
    }

    // If a teacher set a temp password the student must change it first
    if (mustChangePassword) {
        return <Navigate to="/change-password" replace />;
    }

    // Someone without the role — a teacher on an admin URL — is sent back to
    // their own classes page rather than to the generator.
    //
    // This used to render an "access denied" message alongside the redirect,
    // but <Navigate> acts on mount, so the message was replaced before it could
    // be read. Redirecting outright is what actually happened; now it says so.
    // Nothing links here for the wrong role either, so this only fires on a
    // typed or bookmarked URL.
    if ((requiredRole && user?.role !== requiredRole) || (staffOnly && isStudent)) {
        return <Navigate to={homePathFor({ isLoggedIn, isStudent })} replace />;
    }

    return <>{children}</>;
}
