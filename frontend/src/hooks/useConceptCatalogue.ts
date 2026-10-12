import { useEffect, useState } from "react";
import { api } from "../lib/api";

/**
 * The concept catalogue, for every screen that lets someone pick concepts:
 * the curriculum learning-area form, the generator on the home page, and the
 * manual task creator.
 *
 * A failed fetch is swallowed on purpose. The catalogue only powers
 * autocomplete — every picker still accepts a name typed in full — so an error
 * banner would be louder than the problem, and the pages that use this all
 * have their own errors to show for things that actually block the user.
 */
export function useConceptCatalogue(): string[] {
    const [catalogue, setCatalogue] = useState<string[]>([]);

    useEffect(() => {
        let cancelled = false;
        api.get<string[]>("/concepts")
            .then(res => {
                if (!cancelled) setCatalogue(res.data);
            })
            .catch(() => {
                /* suggestions are a convenience; typing still works */
            });
        return () => { cancelled = true; };
    }, []);

    return catalogue;
}
