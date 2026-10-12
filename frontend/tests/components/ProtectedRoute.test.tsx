import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";

vi.mock("react-i18next", () => ({
    useTranslation: () => ({ t: (key: string) => key }),
}));

type Auth = {
    user: { role: string } | null;
    loading: boolean;
    isLoggedIn: boolean;
    isStudent: boolean;
    mustChangePassword: boolean;
};
let auth: Auth;
vi.mock("../../src/context/AuthContext", () => ({ useAuth: () => auth }));

const { default: ProtectedRoute } = await import("../../src/components/ProtectedRoute.tsx");

function as(role: "ANONYMOUS" | "STUDENT" | "TEACHER" | "ADMIN"): Auth {
    const isLoggedIn = role !== "ANONYMOUS";
    return {
        user: isLoggedIn ? { role } : null,
        loading: false,
        isLoggedIn,
        isStudent: role === "STUDENT",
        mustChangePassword: false,
    };
}

/** The generator is mounted at "/" behind staffOnly, as in App.tsx. */
function renderGenerator() {
    return render(
        <MemoryRouter initialEntries={["/"]}>
            <Routes>
                <Route path="/" element={<ProtectedRoute staffOnly><div>generator</div></ProtectedRoute>} />
                <Route path="/login" element={<div>login page</div>} />
                <Route path="/my-classes" element={<div>student classes</div>} />
            </Routes>
        </MemoryRouter>,
    );
}

describe("ProtectedRoute staffOnly (generator at /)", () => {
    it("sends an anonymous visitor to the login page", () => {
        auth = as("ANONYMOUS");
        renderGenerator();
        expect(screen.getByText("login page")).toBeTruthy();
        expect(screen.queryByText("generator")).toBeNull();
    });

    it("sends a student to their own classes page", () => {
        auth = as("STUDENT");
        renderGenerator();
        expect(screen.getByText("student classes")).toBeTruthy();
        expect(screen.queryByText("generator")).toBeNull();
    });

    it.each(["TEACHER", "ADMIN"] as const)("shows the generator to a %s", (role) => {
        auth = as(role);
        renderGenerator();
        expect(screen.getByText("generator")).toBeTruthy();
    });

    it("waits for the session to load instead of redirecting", () => {
        auth = { ...as("ANONYMOUS"), loading: true };
        renderGenerator();
        expect(screen.queryByText("login page")).toBeNull();
        expect(screen.queryByText("generator")).toBeNull();
    });
});
