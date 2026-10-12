import { useAuth } from "../context/AuthContext";
import { homePathFor } from "../lib/homePath.ts";

/**
 * The path the Home button should navigate to for the current user.
 * See homePathFor for the rule and why anonymous visitors are treated
 * differently.
 */
export function useHomePath(): string {
    const { isLoggedIn, isStudent } = useAuth();
    return homePathFor({ isLoggedIn, isStudent });
}
