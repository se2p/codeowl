import { jsPDF } from "jspdf";
import type { Task } from "../types/models";

// ── Types ────────────────────────────────────────────────

export interface ExportableSequence {
    id: string;
    programmingLanguage: string;
    concepts: string[];
    taskDescription?: string;
    tasks: Task[];
}

export interface ExportableWorksheet {
    id: string;
    name: string;
    description: string;
    sequences: ExportableSequence[];
}

// ── JSON Export ──────────────────────────────────────────

export function downloadJSON(data: unknown, filename: string) {
    const json = JSON.stringify(data, null, 2);
    downloadBlob(json, filename, "application/json");
}

export function exportSequenceJSON(seq: ExportableSequence) {
    downloadJSON(seq, `sequence_${seq.id}.json`);
}

export function exportWorksheetJSON(ws: ExportableWorksheet) {
    downloadJSON(ws, `worksheet_${ws.id}.json`);
}

// ── CSV Export ───────────────────────────────────────────

function escapeCSV(value: string | undefined | null): string {
    if (value == null) return "";
    const s = String(value);
    if (s.includes(",") || s.includes('"') || s.includes("\n")) {
        return '"' + s.replace(/"/g, '""') + '"';
    }
    return s;
}

export function exportWorksheetCSV(ws: ExportableWorksheet) {
    const header = [
        "worksheet_id", "worksheet_name", "sequence_id", "language",
        "concepts", "task_description", "task_id", "tier_level",
        "statement", "solution"
    ];

    const rows: string[][] = [];
    for (const seq of ws.sequences) {
        for (const task of seq.tasks) {
            rows.push([
                ws.id,
                ws.name,
                seq.id,
                seq.programmingLanguage,
                (seq.concepts ?? []).join("; "),
                seq.taskDescription ?? "",
                task.id,
                String(task.tierLevel),
                task.statement,
                task.solution,
            ]);
        }
    }

    const csv = [header, ...rows].map(r => r.map(escapeCSV).join(",")).join("\n");
    downloadBlob(csv, `worksheet_${ws.id}.csv`, "text/csv");
}

export function exportSequenceCSV(seq: ExportableSequence) {
    const header = [
        "sequence_id", "language", "concepts", "task_description",
        "task_id", "tier_level", "statement", "solution"
    ];

    const rows: string[][] = [];
    for (const task of seq.tasks) {
        rows.push([
            seq.id,
            seq.programmingLanguage,
            (seq.concepts ?? []).join("; "),
            seq.taskDescription ?? "",
            task.id,
            String(task.tierLevel),
            task.statement,
            task.solution,
        ]);
    }

    const csv = [header, ...rows].map(r => r.map(escapeCSV).join(",")).join("\n");
    downloadBlob(csv, `sequence_${seq.id}.csv`, "text/csv");
}

// ── PDF Export ───────────────────────────────────────────

const PAGE_WIDTH = 210;
const MARGIN = 15;
const CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;

function addPageHeader(doc: jsPDF, title: string) {
    doc.setFontSize(8);
    doc.setTextColor(150);
    doc.text(`CodeOwl — ${title}`, MARGIN, 8);
    doc.setTextColor(0);
}

/**
 * PDF with task statements + code (for exams / printed exercises).
 * @param includeSolution  true = teacher version with solutions, false = student version without
 */
export function exportWorksheetPDF(
    ws: ExportableWorksheet,
    mode: "freeform" | "puzzle" = "freeform",
    includeSolution = true,
) {
    const doc = new jsPDF({ unit: "mm", format: "a4" });
    const variant = includeSolution ? "teacher" : "student";
    const pageTitle = ws.name;
    let y = MARGIN;

    addPageHeader(doc, pageTitle);

    // Title
    doc.setFontSize(18);
    doc.text(ws.name, MARGIN, y + 6);
    y += 12;

    if (ws.description) {
        doc.setFontSize(10);
        doc.setTextColor(80);
        const descLines = doc.splitTextToSize(ws.description, CONTENT_WIDTH);
        doc.text(descLines, MARGIN, y);
        y += descLines.length * 4 + 4;
        doc.setTextColor(0);
    }

    for (let si = 0; si < ws.sequences.length; si++) {
        const seq = ws.sequences[si];
        const sortedTasks = [...seq.tasks].sort((a, b) => a.tierLevel - b.tierLevel);

        for (const task of sortedTasks) {
            // Check if we need a new page
            if (y > 260) {
                doc.addPage();
                y = MARGIN;
                addPageHeader(doc, pageTitle);
            }

            y = renderTask(doc, y, seq, task, si + 1, mode, includeSolution);
            y += 6;
        }
    }

    doc.save(`${ws.name.replace(/[^a-zA-Z0-9]/g, "_")}_${mode}_${variant}.pdf`);
}

export function exportSequencePDF(
    seq: ExportableSequence,
    mode: "freeform" | "puzzle" = "freeform",
    includeSolution = true,
) {
    const doc = new jsPDF({ unit: "mm", format: "a4" });
    const variant = includeSolution ? "teacher" : "student";
    const title = seq.taskDescription ?? `Sequence ${seq.id.slice(0, 8)}`;
    let y = MARGIN;

    addPageHeader(doc, title);

    doc.setFontSize(14);
    doc.text(title, MARGIN, y + 5);
    y += 10;

    doc.setFontSize(9);
    doc.setTextColor(80);
    doc.text(`Language: ${seq.programmingLanguage}    Concepts: ${(seq.concepts ?? []).join(", ")}`, MARGIN, y);
    y += 6;
    doc.setTextColor(0);

    const sortedTasks = [...seq.tasks].sort((a, b) => a.tierLevel - b.tierLevel);
    for (const task of sortedTasks) {
        if (y > 260) {
            doc.addPage();
            y = MARGIN;
            addPageHeader(doc, title);
        }
        y = renderTask(doc, y, seq, task, 1, mode, includeSolution);
        y += 6;
    }

    doc.save(`sequence_${seq.id.slice(0, 8)}_${mode}_${variant}.pdf`);
}

function renderTask(
    doc: jsPDF, y: number,
    seq: ExportableSequence, task: Task,
    _seqNum: number, mode: "freeform" | "puzzle",
    includeSolution = true,
): number {
    // Task header
    doc.setFontSize(11);
    doc.setFont("helvetica", "bold");
    doc.text(`Task — Tier ${task.tierLevel} (${seq.programmingLanguage})`, MARGIN, y);
    y += 5;

    // Statement
    doc.setFont("helvetica", "normal");
    doc.setFontSize(10);
    const stmtLines = doc.splitTextToSize(task.statement, CONTENT_WIDTH);
    doc.text(stmtLines, MARGIN, y);
    y += stmtLines.length * 4 + 3;

    if (!includeSolution) {
        // Student version: empty answer area with dashed border
        const boxHeight = 30;
        if (y + boxHeight > 280) {
            doc.addPage();
            y = MARGIN;
        }
        doc.setDrawColor(180);
        doc.setLineDashPattern([2, 2], 0);
        doc.rect(MARGIN, y, CONTENT_WIDTH, boxHeight);
        doc.setLineDashPattern([], 0);
        doc.setFontSize(8);
        doc.setTextColor(160);
        doc.text("Platz fuer deine Loesung", MARGIN + 3, y + 5);
        doc.setTextColor(0);
        doc.setDrawColor(0);
        y += boxHeight + 2;
    } else if (mode === "freeform") {
        y = renderCodeFreeform(doc, y, task.solution);
    } else {
        y = renderCodePuzzle(doc, y, task.solution);
    }

    return y;
}

/**
 * Free-form: renders code in a gray box with monospace font.
 */
function renderCodeFreeform(doc: jsPDF, y: number, code: string): number {
    const lines = code.split("\n");
    const lineHeight = 3.8;
    const blockHeight = lines.length * lineHeight + 6;

    // Check for page break
    if (y + blockHeight > 280) {
        doc.addPage();
        y = MARGIN;
    }

    // Gray background
    doc.setFillColor(245, 245, 245);
    doc.roundedRect(MARGIN, y - 1, CONTENT_WIDTH, blockHeight, 1, 1, "F");

    // Border
    doc.setDrawColor(200);
    doc.roundedRect(MARGIN, y - 1, CONTENT_WIDTH, blockHeight, 1, 1, "S");

    // Code text
    doc.setFont("courier", "normal");
    doc.setFontSize(8);
    doc.setTextColor(30);

    let cy = y + 3;
    for (const line of lines) {
        // Truncate very long lines
        const text = line.length > 100 ? line.slice(0, 97) + "..." : line;
        doc.text(text, MARGIN + 3, cy);
        cy += lineHeight;
    }

    doc.setFont("helvetica", "normal");
    doc.setTextColor(0);
    doc.setDrawColor(0);

    return y + blockHeight + 2;
}

/**
 * Puzzle mode: renders each code line as a dashed-border block for cutting.
 */
function renderCodePuzzle(doc: jsPDF, y: number, code: string): number {
    const lines = code.split("\n").filter(l => l.trim().length > 0);

    // Shuffle deterministically by sorting alphabetically (predictable for print)
    const shuffled = [...lines].sort(() => Math.random() - 0.5);

    const blockHeight = 7;
    const gap = 1.5;

    // Check for page break
    const totalHeight = shuffled.length * (blockHeight + gap);
    if (y + totalHeight > 280) {
        doc.addPage();
        y = MARGIN;
    }

    doc.setFontSize(8);
    doc.setFont("courier", "normal");

    // Scissors icon hint
    doc.setFontSize(7);
    doc.setFont("helvetica", "italic");
    doc.setTextColor(150);
    doc.text("Cut out the blocks and arrange them in the correct order:", MARGIN, y);
    y += 4;
    doc.setTextColor(0);

    for (const line of shuffled) {
        // Dashed border block
        doc.setDrawColor(150);
        doc.setLineDashPattern([2, 1.5], 0);
        doc.rect(MARGIN, y, CONTENT_WIDTH, blockHeight);
        doc.setLineDashPattern([], 0);

        // Code text inside block
        doc.setFont("courier", "normal");
        doc.setFontSize(8);
        doc.setTextColor(30);
        const text = line.length > 100 ? line.slice(0, 97) + "..." : line;
        doc.text(text, MARGIN + 3, y + 4.5);
        doc.setTextColor(0);

        y += blockHeight + gap;
    }

    doc.setFont("helvetica", "normal");
    doc.setDrawColor(0);

    return y;
}

// ── Helper ───────────────────────────────────────────────

function downloadBlob(content: string, filename: string, mimeType: string) {
    const blob = new Blob([content], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}
