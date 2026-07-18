import { Navigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import type { UserRole } from "../types/models";

interface ProtectedRouteProps {
    children: React.ReactNode;
    requiredRole?: UserRole;
}

export default function ProtectedRoute({ children, requiredRole }: ProtectedRouteProps) {
    const { user, loading, isLoggedIn, mustChangePassword } = useAuth();

    if (loading) {
        return (
            <div className="d-flex justify-content-center align-items-center" style={{ minHeight: "60vh" }}>
                <div className="spinner-border text-primary" role="status">
                    <span className="visually-hidden">Laden...</span>
                </div>
            </div>
        );
    }

    if (!isLoggedIn) {
        return <Navigate to="login" replace />;
    }

    // If a teacher set a temp password the student must change it first
    if (mustChangePassword) {
        return <Navigate to="/change-password" replace />;
    }

    if (requiredRole && user?.role !== requiredRole) {
        return (
            <div className="container py-5 text-center">
                <h3 className="text-danger mb-3">Zugriff verweigert</h3>
                <p className="text-muted">Du hast nicht die erforderliche Berechtigung, um diese Seite anzuzeigen.</p>
                <Navigate to="/" replace />
            </div>
        );
    }

    return <>{children}</>;
}
