import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";

const apiGet = vi.fn();

vi.mock("../../src/lib/api.ts", () => ({
    api: { get: (...args: unknown[]) => apiGet(...args), put: vi.fn() },
}));

vi.mock("react-i18next", () => ({
    useTranslation: () => ({ t: (key: string) => key }),
}));

type AuthUser = {
    userId: string; email: string; firstName: string; lastName: string;
    role: "TEACHER"; token: string; fimAccount?: boolean;
};
let user: AuthUser;
vi.mock("../../src/context/AuthContext.tsx", () => ({
    useAuth: () => ({ user, logout: vi.fn(), isTeacher: true, isAdmin: false }),
}));

const { default: ProfilePage } = await import("../../src/pages/ProfilePage.tsx");

function makeUser(overrides: Partial<AuthUser> = {}): AuthUser {
    return {
        userId: "u1", email: "t@example.org", firstName: "Tina", lastName: "Teacher",
        role: "TEACHER", token: "x", ...overrides,
    };
}

function renderPage() {
    return render(<MemoryRouter><ProfilePage /></MemoryRouter>);
}

/** The password card's button is the one carrying the key icon. */
function passwordChangeButton() {
    return document.querySelector("button i.bi-key")?.closest("button") ?? null;
}

describe("ProfilePage password card", () => {
    beforeEach(() => {
        apiGet.mockReset();
        // The worksheet/sequence lists and /auth/me all go through api.get.
        apiGet.mockImplementation((url: string) =>
            Promise.resolve({ data: url === "/auth/me" ? {} : [] }));
    });

    it("offers the password change to an ordinary account", async () => {
        user = makeUser();
        renderPage();
        await waitFor(() => expect(apiGet).toHaveBeenCalledWith("/auth/me"));
        expect(passwordChangeButton()).not.toBeNull();
        expect(screen.queryByText("profile.passwordManagedByFim")).toBeNull();
    });

    it("withholds it from a FIM account and says why", async () => {
        user = makeUser({ fimAccount: true });
        renderPage();
        expect(passwordChangeButton()).toBeNull();
        expect(screen.getByText("profile.passwordManagedByFim")).toBeTruthy();
    });

    it("asks the server, so a session cached before the flag existed is still recognised", async () => {
        user = makeUser(); // no fimAccount in the cached session
        apiGet.mockImplementation((url: string) =>
            Promise.resolve({ data: url === "/auth/me" ? { fimAccount: true } : [] }));
        renderPage();
        await waitFor(() => expect(screen.getByText("profile.passwordManagedByFim")).toBeTruthy());
        expect(passwordChangeButton()).toBeNull();
    });
});
