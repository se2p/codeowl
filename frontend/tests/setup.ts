import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// Testing Library only auto-cleans when vitest globals are enabled; they are
// not, so unmount explicitly to keep tests isolated from one another.
afterEach(() => {
    cleanup();
});
