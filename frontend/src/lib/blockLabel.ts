/**
 * How a block is named to the student.
 *
 * The badge on a highlighted block and the hint text that talks about it have
 * to agree, so this mirrors BlockLabels.of on the backend — the two must stay
 * in step or a hint sends the student to the wrong block.
 *
 * Letters rather than numbers on purpose: a block's position is not its line
 * number, and a bare number invites reading it as one. Beyond Z it continues
 * like spreadsheet columns (AA, AB) so it never runs out.
 *
 * @param index zero-based position of the block in the solution area
 */
export function blockLabel(index: number): string {
    if (!Number.isInteger(index) || index < 0) return "?";

    let label = "";
    let n = index;
    while (n >= 0) {
        label = String.fromCharCode(65 + (n % 26)) + label;
        n = Math.floor(n / 26) - 1;
    }
    return label;
}
