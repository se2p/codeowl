/*
 * Copyright (C) 2019-2026 CodeOwl contributors
 *
 * This file is part of CodeOwl.
 *
 * CodeOwl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * CodeOwl is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with CodeOwl. If not, see <http://www.gnu.org/licenses/>.
 */
package de.uni_passau.fim.se2.application.service.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AP12 — Change-Analysis-Engine: Syntactic analysis utilities.
 * Provides Levenshtein distance, Jaccard similarity, word diff, line diff,
 * and token change ratio calculations.
 */
@Service
public class SyntacticAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(SyntacticAnalyzer.class);

    // ── Inner result records ──

    public record WordDiffResult(int wordsAdded, int wordsRemoved, int wordsModified, int lengthDelta) {}

    public record LineDiffResult(int linesAdded, int linesRemoved, int linesModified, int linesReordered, int locDelta) {}

    // ── Public API ──

    /**
     * Normalized Levenshtein distance: editDistance / max(|a|, |b|).
     * Returns 0.0 when both strings are identical, 1.0 when completely different.
     */
    public double levenshteinRatio(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        if (a.equals(b)) return 0.0;

        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 0.0;

        int dist = levenshteinDistance(a, b);
        return (double) dist / maxLen;
    }

    /**
     * Word-level Jaccard index: |intersection| / |union|.
     * Returns 1.0 when identical, 0.0 when no overlap.
     */
    public double jaccardSimilarity(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";

        Set<String> setA = tokenize(a);
        Set<String> setB = tokenize(b);

        if (setA.isEmpty() && setB.isEmpty()) return 1.0;
        if (setA.isEmpty() || setB.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);

        return (double) intersection.size() / union.size();
    }

    /**
     * Computes word-level diff statistics between two texts.
     */
    public WordDiffResult wordDiff(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";

        String[] wordsA = splitWords(a);
        String[] wordsB = splitWords(b);

        int[][] lcs = lcsTable(wordsA, wordsB);
        List<String> common = extractLcs(wordsA, wordsB, lcs);
        Set<String> commonSet = new HashSet<>(common);

        // Count words in B not in common => added
        // Count words in A not in common => removed
        // For modified: words that appear in both arrays but differ in surrounding context
        // Simplified: use multiset approach
        Map<String, Integer> freqA = frequencyMap(wordsA);
        Map<String, Integer> freqB = frequencyMap(wordsB);

        int added = 0;
        int removed = 0;
        int modified = 0;

        Set<String> allWords = new HashSet<>();
        allWords.addAll(freqA.keySet());
        allWords.addAll(freqB.keySet());

        for (String word : allWords) {
            int countA = freqA.getOrDefault(word, 0);
            int countB = freqB.getOrDefault(word, 0);
            if (countA == 0) {
                added += countB;
            } else if (countB == 0) {
                removed += countA;
            }
            // Words present in both but with different frequencies
            // count the difference as modifications
            else if (countA != countB) {
                int diff = Math.abs(countA - countB);
                if (countB > countA) {
                    added += diff;
                } else {
                    removed += diff;
                }
            }
        }

        // Detect modifications via LCS: words not in LCS but present in both texts
        int lcsLen = common.size();
        int matchedA = lcsLen;
        int matchedB = lcsLen;
        int unmatchedA = wordsA.length - matchedA;
        int unmatchedB = wordsB.length - matchedB;

        // Words that are purely added or removed are already counted.
        // Modified = min of unmatched on both sides (paired changes)
        int pureAdded = Math.max(0, unmatchedB - unmatchedA);
        int pureRemoved = Math.max(0, unmatchedA - unmatchedB);
        modified = Math.min(unmatchedA, unmatchedB);
        added = pureAdded;
        removed = pureRemoved;

        int lengthDelta = wordsB.length - wordsA.length;

        return new WordDiffResult(added, removed, modified, lengthDelta);
    }

    /**
     * Computes line-level diff statistics between two texts.
     */
    public LineDiffResult lineDiff(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";

        String[] linesA = splitLines(a);
        String[] linesB = splitLines(b);

        int[][] lcs = lcsTable(linesA, linesB);
        List<String> common = extractLcs(linesA, linesB, lcs);

        int lcsLen = common.size();
        int unmatchedA = linesA.length - lcsLen;
        int unmatchedB = linesB.length - lcsLen;

        int modified = Math.min(unmatchedA, unmatchedB);
        int linesRemoved = Math.max(0, unmatchedA - unmatchedB);
        int linesAdded = Math.max(0, unmatchedB - unmatchedA);

        // Detect reordered lines: lines present in both but in different order
        int linesReordered = countReorderedLines(linesA, linesB);

        int locDelta = linesB.length - linesA.length;

        return new LineDiffResult(linesAdded, linesRemoved, modified, linesReordered, locDelta);
    }

    /**
     * Whitespace-normalized token-level change ratio.
     * Returns 0.0 when identical, 1.0 when completely different.
     */
    public double tokenChangeRatio(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";

        String[] tokensA = normalizeAndTokenize(a);
        String[] tokensB = normalizeAndTokenize(b);

        if (tokensA.length == 0 && tokensB.length == 0) return 0.0;

        int[][] lcs = lcsTable(tokensA, tokensB);
        int lcsLen = lcs[tokensA.length][tokensB.length];

        int totalTokens = Math.max(tokensA.length, tokensB.length);
        if (totalTokens == 0) return 0.0;

        int changedTokens = totalTokens - lcsLen;
        return (double) changedTokens / totalTokens;
    }

    // ── Levenshtein distance (character-level, from scratch) ──

    private int levenshteinDistance(String a, String b) {
        int lenA = a.length();
        int lenB = b.length();

        // Use two rows to save memory
        int[] prev = new int[lenB + 1];
        int[] curr = new int[lenB + 1];

        for (int j = 0; j <= lenB; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= lenA; i++) {
            curr[0] = i;
            for (int j = 1; j <= lenB; j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }

        return prev[lenB];
    }

    // ── LCS helpers ──

    private int[][] lcsTable(String[] a, String[] b) {
        int m = a.length;
        int n = b.length;
        int[][] dp = new int[m + 1][n + 1];

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (a[i - 1].equals(b[j - 1])) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        return dp;
    }

    private List<String> extractLcs(String[] a, String[] b, int[][] dp) {
        List<String> result = new ArrayList<>();
        int i = a.length;
        int j = b.length;

        while (i > 0 && j > 0) {
            if (a[i - 1].equals(b[j - 1])) {
                result.add(a[i - 1]);
                i--;
                j--;
            } else if (dp[i - 1][j] >= dp[i][j - 1]) {
                i--;
            } else {
                j--;
            }
        }
        Collections.reverse(result);
        return result;
    }

    // ── Reorder detection ──

    private int countReorderedLines(String[] linesA, String[] linesB) {
        // Build position map for lines in B
        Map<String, List<Integer>> positionsInB = new LinkedHashMap<>();
        for (int i = 0; i < linesB.length; i++) {
            positionsInB.computeIfAbsent(linesB[i], k -> new ArrayList<>()).add(i);
        }

        // For each line in A that also exists in B, track its first unused position in B
        Map<String, Integer> usageIndex = new HashMap<>();
        List<Integer> mappedPositions = new ArrayList<>();

        for (String line : linesA) {
            List<Integer> positions = positionsInB.get(line);
            if (positions != null) {
                int idx = usageIndex.getOrDefault(line, 0);
                if (idx < positions.size()) {
                    mappedPositions.add(positions.get(idx));
                    usageIndex.put(line, idx + 1);
                }
            }
        }

        // Count inversions in mappedPositions (lines that moved)
        int reordered = 0;
        for (int i = 0; i < mappedPositions.size(); i++) {
            for (int j = i + 1; j < mappedPositions.size(); j++) {
                if (mappedPositions.get(i) > mappedPositions.get(j)) {
                    reordered++;
                    break; // count each displaced line once
                }
            }
        }
        return reordered;
    }

    // ── Tokenization helpers ──

    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        for (String word : text.split("\\s+")) {
            String trimmed = word.trim();
            if (!trimmed.isEmpty()) {
                tokens.add(trimmed);
            }
        }
        return tokens;
    }

    private String[] splitWords(String text) {
        if (text.isBlank()) return new String[0];
        return text.split("\\s+");
    }

    private String[] splitLines(String text) {
        if (text.isBlank()) return new String[0];
        return text.split("\\r?\\n", -1);
    }

    private String[] normalizeAndTokenize(String text) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) return new String[0];
        return normalized.split(" ");
    }

    private Map<String, Integer> frequencyMap(String[] words) {
        Map<String, Integer> freq = new HashMap<>();
        for (String w : words) {
            freq.merge(w, 1, Integer::sum);
        }
        return freq;
    }
}
