/**
 * Where "Home" should take someone.
 *
 * For a registered user the classes page is the start of the normal workflow —
 * teachers manage their classes from there, students reach their assigned
 * worksheets through them — so Home on a worksheet, sequence or task returns
 * there rather than to the sequence generator.
 *
 * The two roles have separate pages, matching the navbar: teachers and admins
 * get /classes, students /my-classes.
 *
 * Anonymous visitors go to the login page: the generator at / is teacher/admin
 * only and both class pages are behind ProtectedRoute. Sequences, worksheets and
 * tasks are still publicly viewable via a shared link, so this case is real.
 */
export function homePathFor(auth: { isLoggedIn: boolean; isStudent: boolean }): string {
    if (!auth.isLoggedIn) return "/login";
    return auth.isStudent ? "/my-classes" : "/classes";
}

/** Landing page straight after login, where only the role is known yet. */
export function loginLandingPath(role: string | null | undefined): string {
    return role === "STUDENT" ? "/my-classes" : "/classes";
}
