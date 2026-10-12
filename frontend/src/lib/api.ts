import axios from "axios";

const baseURL = import.meta.env.VITE_API_BASE_URL || "/api";
const uiBaseURL = import.meta.env.VITE_BASE_PATH || "/";

/**
 * Turns an Axios error into a string that is safe to render.
 *
 * The backend's GlobalExceptionHandler replies with an ApiErrorResponse object
 * ({ error, message }). Putting that object straight into component state and
 * rendering it throws "Objects are not valid as a React child", and since the
 * app has no error boundary that unmounts the whole tree — a blank page instead
 * of an error message.
 */
export function apiErrorMessage(err: unknown, fallback = "Unexpected error"): string {
    const axiosError = err as { response?: { data?: unknown }; message?: string } | undefined;
    const data = axiosError?.response?.data;

    if (typeof data === "string" && data.trim()) return data;

    if (data !== null && typeof data === "object") {
        const { message, error } = data as { message?: string; error?: string };
        if (error && message) return `${error}: ${message}`;
        if (message) return message;
        if (error) return error;
    }

    return axiosError?.message || fallback;
}

/**
 * Whether a failed request came back as 404.
 *
 * <p>Worth asking about separately, because a 404 from this API carries no body: the worksheet
 * endpoints answer it both for "no such worksheet" and for "not yours to see", deliberately
 * identical so nobody can tell which. With nothing in the body, {@link apiErrorMessage} falls
 * through to axios's own "Request failed with status code 404" — an English string in front of
 * a German-speaking teacher whose only mistake was opening a link to something that has since
 * been made private.
 *
 * <p>So pages that can meet a 404 check for it and say something true instead.
 */
export function isNotFound(err: unknown): boolean {
    const axiosError = err as { response?: { status?: number } } | undefined;
    return axiosError?.response?.status === 404;
}

export const api = axios.create({
    baseURL,
    timeout: 120000,
    headers: { "Content-Type": "application/json" },
});

// Attach JWT token to every request if available
api.interceptors.request.use((config) => {
    const token = localStorage.getItem("jwt");
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

/** Pages where we should never force-redirect to /login */
const AUTH_PAGES = ["/login", "/register", "/verify-email", "/reset-password", "/forgot-password", "/student-onboarding"];

function isOnAuthPage(): boolean {
    return AUTH_PAGES.some((p) => window.location.pathname.includes(p));
}

function forceLogout(): void {
    localStorage.removeItem("jwt");
    localStorage.removeItem("authUser");
    window.location.replace(`${uiBaseURL}login`);
}

/**
 * Whether an auth failure means a session was lost, rather than there never having been one.
 *
 * <p>CodeOwl serves anonymous visitors on purpose: a student opens a worksheet by link, with no
 * account, and solves it. Those pages also make requests that an anonymous caller is not
 * entitled to make — the concept catalogue behind the picker's autocomplete, for one — and the
 * answer means nothing more than "not for you".
 *
 * <p>Treating that as an expired session logged out someone who had never logged in and sent
 * them to /login, which made the start page and every sequence unreachable without an account.
 * Where there is no token there is no session to lose, so the failure belongs to the caller.
 *
 * <p>Both codes are checked because the backend answers 403, not 401, when there is no
 * principal at all — an anonymous caller and a caller with a rejected token are indistinguishable
 * from the status line, which is precisely why the token has to be consulted separately.
 *
 * @param status   HTTP status of the failed response, or undefined for a transport error
 * @param hasToken whether a JWT is currently stored
 */
export function signalsSessionLoss(status: number | undefined, hasToken: boolean): boolean {
    if (!hasToken) {
        return false;
    }
    return status === 401 || status === 403;
}

/** Flag to prevent multiple concurrent /auth/me validation calls */
let isValidatingSession = false;

// Auto-logout on 401; on 403 verify session is still valid before logging out
api.interceptors.response.use(
    (response) => response,
    async (error) => {
        const status = error.response?.status;

        if (isOnAuthPage()) {
            return Promise.reject(error);
        }

        // Anonymous visitors have no session to lose. Without this the 403 branch below would
        // also evict them: it probes /auth/me, which cannot succeed without a token.
        if (!signalsSessionLoss(status, !!localStorage.getItem("jwt"))) {
            return Promise.reject(error);
        }

        // 401 → token invalid/expired → immediate logout
        if (status === 401) {
            forceLogout();
            return Promise.reject(error);
        }

        // 403 → could be role-based (legitimate) or stale session (user deleted / DB reset)
        // Verify by calling /auth/me; if that also fails → session is dead → logout
        if (status === 403 && !isValidatingSession) {
            isValidatingSession = true;
            try {
                await axios.get(`${baseURL}/auth/me`, {
                    headers: { Authorization: `Bearer ${localStorage.getItem("jwt")}` },
                    timeout: 5000,
                });
                // /auth/me succeeded → user exists, this is a genuine 403 (wrong role)
            } catch {
                // /auth/me also failed → session is invalid → logout
                forceLogout();
            } finally {
                isValidatingSession = false;
            }
        }

        return Promise.reject(error);
    }
);
