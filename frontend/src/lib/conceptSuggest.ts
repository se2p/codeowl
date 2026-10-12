/**
 * Matching rules for the concept picker on the curriculum admin form.
 *
 * The catalogue is the ~80 concepts already in the database. A learning area
 * may also be given a name that is not in it — the backend creates one, which
 * is what a new module's vocabulary needs — so nothing here rejects an unknown
 * name; the picker only makes the known ones easy to reach.
 *
 * Comparison is case-insensitive throughout, matching the backend, and the
 * catalogue's spelling always wins: typing "FOR-LOOP" selects the existing
 * `for-loop` rather than creating a second concept that differs only in case.
 *
 * A name the catalogue does not have is lowercased, because that is what the
 * database will store it as — PostgreConceptDAO normalises on insert and
 * concepts_name_lowercase_check enforces it — so the chip shows the spelling
 * the concept will actually have rather than the one that was typed.
 */

const key = (value: string): string => value.trim().toLowerCase();

/** The catalogue's spelling of `raw` when it knows the name, else `raw` lowercased. */
export function canonicalConcept(catalogue: string[], raw: string): string {
    const wanted = key(raw);
    return catalogue.find(c => key(c) === wanted) ?? wanted;
}

/** Whether the name would be created rather than reused, for the "new" hint. */
export function isNewConcept(catalogue: string[], raw: string): boolean {
    const wanted = key(raw);
    return wanted.length > 0 && !catalogue.some(c => key(c) === wanted);
}

/**
 * Catalogue entries to offer for the current input. Already-selected concepts
 * drop out — suggesting them would only produce a no-op — and an empty query
 * lists the start of the catalogue, so the picker is browsable before typing.
 *
 * Names that *start with* the query rank above ones that merely contain it:
 * typing "arr" should reach `array` before `2d-array`.
 */
export function suggestConcepts(
    catalogue: string[],
    query: string,
    selected: string[],
    limit = 8,
): string[] {
    const taken = new Set(selected.map(key));
    const q = key(query);
    const available = catalogue.filter(c => !taken.has(key(c)));

    if (q.length === 0) {
        return available.slice(0, limit);
    }

    const prefix = available.filter(c => key(c).startsWith(q));
    const infix = available.filter(c => !key(c).startsWith(q) && key(c).includes(q));
    return [...prefix, ...infix].slice(0, limit);
}

/**
 * Adds a concept, in the catalogue's spelling where it has one. A blank name or
 * one already selected leaves the list untouched, so the caller can call this
 * on every Enter without checking first.
 */
export function addConcept(selected: string[], catalogue: string[], raw: string): string[] {
    const name = canonicalConcept(catalogue, raw);
    if (name.length === 0 || selected.some(c => key(c) === key(name))) {
        return selected;
    }
    return [...selected, name];
}

export function removeConcept(selected: string[], name: string): string[] {
    return selected.filter(c => key(c) !== key(name));
}
