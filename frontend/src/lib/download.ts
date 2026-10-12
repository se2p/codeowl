import { api } from "./api";

/**
 * Fetches a file from the API and hands it to the browser as a download.
 *
 * Lifted out of AdminDatabasePage when the research page needed the same thing. Downloads
 * here are streamed by the backend with a `Content-Disposition` naming the file — the name
 * carries a timestamp, and for a research export the class it covers — so the filename is
 * read back off the response rather than guessed at the call site. `filenameFallback` only
 * applies when the header is missing or unparseable.
 *
 * `timeout: 0` disables the client's default 120 s: a full database dump legitimately takes
 * longer than that, and a timeout mid-stream would leave a truncated file on disk that looks
 * like a complete one.
 */
export async function downloadFile(
    method: "get" | "post",
    url: string,
    filenameFallback: string,
    params?: Record<string, unknown>
): Promise<void> {
    const res =
        method === "post"
            ? await api.post(url, null, { responseType: "blob", timeout: 0, params })
            : await api.get(url, { responseType: "blob", timeout: 0, params });

    const cd = (res.headers["content-disposition"] as string) || "";
    const match = cd.match(/filename="?([^";]+)"?/i);
    const filename = match?.[1] || filenameFallback;

    const blobUrl = URL.createObjectURL(res.data as Blob);
    const a = document.createElement("a");
    a.href = blobUrl;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(blobUrl);
}

/** Filename-safe timestamp, for the fallback name when the response carries no header. */
export function downloadTimestamp(): string {
    return new Date().toISOString().replace(/[:.]/g, "-").slice(0, 19);
}
