import { useEffect, useState } from "react";
import { api } from "../lib/api.ts";

/**
 * What the current user may do with a sequence sitting in one of their worksheets.
 *
 * <p>Mirrors {@code WorksheetController.SequenceAccessResponse}. The server decides; this only
 * carries the answer. Duplicating the rule here would mean two implementations of a permission,
 * and the one on the client is the one that cannot be trusted anyway.
 */
export interface SequenceAccess {
    /** May see the teacher view: tiers, distractors, tags, generation constraints. */
    canInspect: boolean;
    /** May change it where it stands. Owners and admins. */
    canEditInPlace: boolean;
    /** May change it only after taking a copy. A curator of someone else's sequence. */
    mustForkToEdit: boolean;
}

export const NO_SEQUENCE_ACCESS: SequenceAccess = {
    canInspect: false,
    canEditInPlace: false,
    mustForkToEdit: false,
};

/**
 * Asks the server what the viewer may do here.
 *
 * <p>Worksheet-scoped, because the claim being made is "this is in a worksheet of mine". Opening
 * a sequence by bare link carries no such claim and asks nothing — hence the early return rather
 * than a request that would always answer no.
 *
 * <p>Failures fall back to no access. A permissions endpoint that cannot be reached must not
 * open anything: the UI shows the ordinary student-facing view, which is the safe direction to
 * fail in, and any write the user might have attempted would be refused by the server regardless.
 */
export function useSequenceAccess(
    sequenceId: string | undefined,
    worksheetId: string | null,
): SequenceAccess {
    const [access, setAccess] = useState<SequenceAccess>(NO_SEQUENCE_ACCESS);

    useEffect(() => {
        if (!sequenceId || !worksheetId) {
            setAccess(NO_SEQUENCE_ACCESS);
            return;
        }

        let cancelled = false;
        api.get<SequenceAccess>(`/worksheets/${worksheetId}/sequences/${sequenceId}/access`)
            .then(({ data }) => { if (!cancelled) setAccess(data); })
            .catch(() => { if (!cancelled) setAccess(NO_SEQUENCE_ACCESS); });

        // The teacher can navigate between sequences of a worksheet quickly; without this a
        // slow answer for the previous one could arrive after the next one and grant rights on
        // the wrong sequence.
        return () => { cancelled = true; };
    }, [sequenceId, worksheetId]);

    return access;
}
