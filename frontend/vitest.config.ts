import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

/**
 * Kept separate from vite.config.ts so the dev server and production build
 * stay untouched by test configuration.
 *
 * Tests live in ./tests, mirroring the src tree, rather than beside the code
 * they cover. tsconfig.test.json keeps them in the `tsc -b` program so they are
 * still typechecked by `npm run build`.
 */
export default defineConfig({
    plugins: [react()],
    test: {
        environment: "jsdom",
        setupFiles: ["./tests/setup.ts"],
        include: ["tests/**/*.{test,spec}.{ts,tsx}"],
        restoreMocks: true,
    },
});
