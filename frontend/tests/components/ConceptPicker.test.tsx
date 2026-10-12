import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import ConceptPicker from "../../src/components/ConceptPicker.tsx";

vi.mock("react-i18next", () => ({
    useTranslation: () => ({
        t: (key: string, vars?: Record<string, unknown>) =>
            vars?.concept ? `${key}:${vars.concept}` : key,
    }),
}));

/**
 * The picker exists so an admin reaches the ~80 concepts that already exist
 * instead of typing near-duplicates of them. These cover the paths that make
 * that work: suggestions before typing, filtering while typing, and the two
 * ways to accept one.
 */
const CATALOGUE = ["2d-array", "array", "for-loop", "enhanced-for-loop", "recursion"];

function setup(value: string[] = []) {
    const onChange = vi.fn();
    render(<ConceptPicker value={value} catalogue={CATALOGUE} onChange={onChange} />);
    return { onChange, input: screen.getByRole("combobox") };
}

describe("ConceptPicker", () => {
    it("offers the catalogue on focus, before anything is typed", () => {
        const { input } = setup();
        expect(screen.queryByRole("listbox")).toBeNull();

        fireEvent.focus(input);

        expect(screen.getByRole("listbox")).toBeInTheDocument();
        expect(screen.getByRole("option", { name: "array" })).toBeInTheDocument();
    });

    it("lists every match rather than cutting the catalogue short", () => {
        // A cap used to stop the list at eight, alphabetically — the rest of the
        // catalogue simply looked absent. The box scrolls instead.
        const big = Array.from({ length: 40 }, (_, i) => `concept-${String(i).padStart(2, "0")}`);
        const onChange = vi.fn();
        render(<ConceptPicker value={[]} catalogue={big} onChange={onChange} />);

        fireEvent.focus(screen.getByRole("combobox"));

        expect(screen.getAllByRole("option")).toHaveLength(40);
        expect(screen.getByRole("option", { name: "concept-39" })).toBeInTheDocument();
        expect(screen.getByRole("listbox")).toHaveStyle({ overflowY: "auto" });
    });

    it("narrows the list as the query is typed", () => {
        const { input } = setup();
        fireEvent.focus(input);
        fireEvent.change(input, { target: { value: "for" } });

        const options = screen.getAllByRole("option").map(o => o.textContent);
        expect(options).toEqual(["for-loop", "enhanced-for-loop"]);
    });

    it("adds the concept that is clicked", () => {
        const { onChange, input } = setup();
        fireEvent.focus(input);
        fireEvent.change(input, { target: { value: "rec" } });
        // mousedown, not click: that is what the component listens for, so the
        // input's blur cannot close the list first.
        fireEvent.mouseDown(screen.getByRole("option", { name: "recursion" }));

        expect(onChange).toHaveBeenCalledWith(["recursion"]);
    });

    it("adds the highlighted suggestion on Enter", () => {
        const { onChange, input } = setup();
        fireEvent.focus(input);
        fireEvent.change(input, { target: { value: "for" } });
        fireEvent.keyDown(input, { key: "ArrowDown" });   // move to enhanced-for-loop
        fireEvent.keyDown(input, { key: "Enter" });

        expect(onChange).toHaveBeenCalledWith(["enhanced-for-loop"]);
    });

    it("takes the catalogue's spelling, not what was typed", () => {
        const { onChange, input } = setup();
        fireEvent.focus(input);
        fireEvent.change(input, { target: { value: "FOR-LOOP" } });
        fireEvent.keyDown(input, { key: "Enter" });

        expect(onChange).toHaveBeenCalledWith(["for-loop"]);
    });

    it("still accepts a name the catalogue does not have, and says it is new", () => {
        const { onChange, input } = setup();
        fireEvent.change(input, { target: { value: "Kellerautomaten" } });
        expect(screen.getByText("curriculum.conceptNew:Kellerautomaten")).toBeInTheDocument();

        fireEvent.keyDown(input, { key: "Enter" });
        // Stored lowercase, so that is what the chip shows.
        expect(onChange).toHaveBeenCalledWith(["kellerautomaten"]);
    });

    it("does not suggest what is already selected", () => {
        const { input } = setup(["array"]);
        fireEvent.focus(input);
        fireEvent.change(input, { target: { value: "arr" } });

        const options = screen.getAllByRole("option").map(o => o.textContent);
        expect(options).toEqual(["2d-array"]);
    });

    it("removes a chip through its close button", () => {
        const { onChange } = setup(["array", "recursion"]);
        fireEvent.click(screen.getByLabelText("curriculum.conceptRemove:array"));

        expect(onChange).toHaveBeenCalledWith(["recursion"]);
    });

    it("removes the last chip on backspace in an empty field", () => {
        const { onChange, input } = setup(["array", "recursion"]);
        fireEvent.keyDown(input, { key: "Backspace" });

        expect(onChange).toHaveBeenCalledWith(["array"]);
    });

    it("leaves the chips alone when backspace lands on typed text", () => {
        const { onChange, input } = setup(["array"]);
        fireEvent.change(input, { target: { value: "rec" } });
        fireEvent.keyDown(input, { key: "Backspace" });

        expect(onChange).not.toHaveBeenCalled();
    });

    it("closes the list on Escape", () => {
        const { input } = setup();
        fireEvent.focus(input);
        expect(screen.getByRole("listbox")).toBeInTheDocument();

        fireEvent.keyDown(input, { key: "Escape" });
        expect(screen.queryByRole("listbox")).toBeNull();
    });
});
