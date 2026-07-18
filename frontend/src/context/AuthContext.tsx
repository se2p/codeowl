import { createContext, useContext, useState, useEffect, type ReactNode } from "react";
import { api } from "../lib/api";
import type { AuthUser, LoginResponse } from "../types/models";

interface LoginResult {
    role: string;
    classIds?: string[];
    mustChangePassword?: boolean;
}

interface AuthContextType {
    user: AuthUser | null;
    loading: boolean;
    login: (email: string, password: string) => Promise<LoginResult>;
    logout: () => void;
    clearMustChangePassword: () => void;
    isLoggedIn: boolean;
    isTeacher: boolean;
    isAdmin: boolean;
    isStudent: boolean;
    mustChangePassword: boolean;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
    const [user, setUser] = useState<AuthUser | null>(null);
    const [loading, setLoading] = useState(true);

    // Restore user from localStorage on mount
    useEffect(() => {
        const stored = localStorage.getItem("authUser");
        const token = localStorage.getItem("jwt");
        if (stored && token) {
            try {
                const parsed = JSON.parse(stored) as AuthUser;
                setUser(parsed);
            } catch {
                localStorage.removeItem("authUser");
                localStorage.removeItem("jwt");
            }
        }
        setLoading(false);
    }, []);

    const login = async (email: string, password: string): Promise<LoginResult> => {
        const response = await api.post<LoginResponse>("/auth/login", { email, password });
        const data = response.data;

        const authUser: AuthUser = {
            userId: data.userId,
            email: data.email,
            firstName: data.firstName,
            lastName: data.lastName,
            role: data.role,
            token: data.token,
            mustChangePassword: data.mustChangePassword ?? false,
        };

        localStorage.setItem("jwt", data.token);
        localStorage.setItem("authUser", JSON.stringify(authUser));
        setUser(authUser);

        return { role: data.role, classIds: data.classIds, mustChangePassword: data.mustChangePassword };
    };

    const logout = () => {
        localStorage.removeItem("jwt");
        localStorage.removeItem("authUser");
        setUser(null);
    };

    /** Called after a successful temp-password change to clear the flag locally. */
    const clearMustChangePassword = () => {
        if (!user) return;
        const updated = { ...user, mustChangePassword: false };
        localStorage.setItem("authUser", JSON.stringify(updated));
        setUser(updated);
    };

    const value: AuthContextType = {
        user,
        loading,
        login,
        logout,
        clearMustChangePassword,
        isLoggedIn: !!user,
        isTeacher: user?.role === "TEACHER",
        isAdmin: user?.role === "ADMIN",
        isStudent: user?.role === "STUDENT",
        mustChangePassword: user?.mustChangePassword === true,
    };

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextType {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error("useAuth must be used within an AuthProvider");
    }
    return context;
}
