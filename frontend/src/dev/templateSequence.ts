// Nur Beispielwerte – pass sie gerne an dein Projekt an
import type { TieredTaskSequence } from "../types/models";

export const TEMPLATE_SEQUENCE: TieredTaskSequence = {
    id: "00000000-0000-0000-0000-000000template",
    programmingLanguage: "python",
    concepts: ["Variables", "Loops", "Collections"],
    taskDescription: "Intro sequence to sums and iterations. Tier 1 is trivial, Tier 2 adds loops, Tier 3 adds collection processing.",
    codeSnippet: `def sum_evens(nums):
    total = 0
    for n in nums:
        if n % 2 == 0:
            total += n
    return total`,
    tasks: [
        {
            id: "t1",
            tierLevel: 1,
            statement: "Write a function add(a, b) that returns the sum of a and b.",
            solution: `def add(a, b):
    return a + b`,
            cyclomaticComplexity: 1,
            programmingLanguage: "Python"
        },
        {
            id: "t2",
            tierLevel: 2,
            statement: "Write a function sum_to_n(n) that returns 1 + 2 + ... + n.",
            solution: `def sum_to_n(n):
    s = 0
    for i in range(1, n+1):
        s += i
    return s`,
            cyclomaticComplexity: 2,
            programmingLanguage: "Python"
        },
        {
            id: "t3",
            tierLevel: 3,
            statement: "Given a list of integers, return the sum of all even numbers.",
            solution: `def sum_evens(nums):
    total = 0
    for n in nums:
        if n % 2 == 0:
            total += n
    return total`,
            cyclomaticComplexity: 3,
            programmingLanguage: "Python"
        }
    ]
};
