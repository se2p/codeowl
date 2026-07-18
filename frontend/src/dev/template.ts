import type { TieredTaskSequence } from "../types/models";

/**
 * Python mit 4 Leerzeichen pro Indent, damit es zu deinem TAB_SIZE=4 passt.
 * Jede Aufgabe hat mehrere Ebenen, damit das Einrücken testbar ist.
 */
export const TEMPLATE_SEQUENCE = {
    id: "dev-seq-001",
    tasks: [
        {
            id: "t1",
            statement: "Implementiere add(a, b), das die Summe zurückgibt.",
            solution: `def add(a, b):
    return a + b`,
        },
        {
            id: "t2",
            statement:
                "Schreibe sum_positive(nums), das nur positive Zahlen aufsummiert.",
            solution: `def sum_positive(nums):
    total = 0
    for n in nums:
        if n > 0:
            total += n
    return total`,
        },
        {
            id: "t3",
            statement:
                "Gib für 1..15 Fizz/Buzz/FizzBuzz aus (klassischer FizzBuzz).",
            solution: `for i in range(1, 16):
    if i % 3 == 0 and i % 5 == 0:
        print("FizzBuzz")
    elif i % 3 == 0:
        print("Fizz")
    elif i % 5 == 0:
        print("Buzz")
    else:
        print(i)`,
        },
    ],
} as unknown as TieredTaskSequence;

export function loadTemplateSequence(): TieredTaskSequence {
    localStorage.setItem("lastSequence", JSON.stringify(TEMPLATE_SEQUENCE));
    return TEMPLATE_SEQUENCE;
}
