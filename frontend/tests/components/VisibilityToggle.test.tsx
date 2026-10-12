import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import VisibilityToggle from "../../src/components/VisibilityToggle.tsx";

// i18n is stubbed to echo the key, so the assertions read against stable identifiers rather
// than against German copy that a translator may reword at any time.
vi.mock("react-i18next", () => ({
    useTranslation: () => ({ t: (key: string) => key }),
}));

describe("VisibilityToggle", () => {
    it("shows all three states and marks the current one", () => {
        render(<VisibilityToggle value="UNLISTED" onChange={vi.fn()} />);

        const unlisted = screen.getByLabelText(/worksheet\.visibility\.UNLISTED\.label/);
        expect(unlisted).toBeChecked();
        expect(screen.getByLabelText(/worksheet\.visibility\.PRIVATE\.label/)).not.toBeChecked();
        expect(screen.getByLabelText(/worksheet\.visibility\.PUBLIC\.label/)).not.toBeChecked();
    });

    it("explains what each state means, so publishing is not a guess", () => {
        render(<VisibilityToggle value="PRIVATE" onChange={vi.fn()} />);

        expect(screen.getByText("worksheet.visibility.PRIVATE.hint")).toBeInTheDocument();
        expect(screen.getByText("worksheet.visibility.UNLISTED.hint")).toBeInTheDocument();
        expect(screen.getByText("worksheet.visibility.PUBLIC.hint")).toBeInTheDocument();
    });

    it("reports the chosen state", async () => {
        const onChange = vi.fn().mockResolvedValue(undefined);
        render(<VisibilityToggle value="UNLISTED" onChange={onChange} />);

        fireEvent.click(screen.getByLabelText(/worksheet\.visibility\.PUBLIC\.label/));

        await waitFor(() => expect(onChange).toHaveBeenCalledWith("PUBLIC"));
    });

    it("does not re-save the state that is already selected", async () => {
        const onChange = vi.fn().mockResolvedValue(undefined);
        render(<VisibilityToggle value="PUBLIC" onChange={onChange} />);

        fireEvent.click(screen.getByLabelText(/worksheet\.visibility\.PUBLIC\.label/));

        expect(onChange).not.toHaveBeenCalled();
    });

    it("falls back to the previous state and says so when saving fails", async () => {
        const onChange = vi.fn().mockRejectedValue(new Error("network"));
        render(<VisibilityToggle value="UNLISTED" onChange={onChange} />);

        fireEvent.click(screen.getByLabelText(/worksheet\.visibility\.PUBLIC\.label/));

        // The point of the revert: a failed request must never leave the control claiming a
        // worksheet is published when the server never accepted it.
        await waitFor(() => {
            expect(screen.getByText("worksheet.visibility.error")).toBeInTheDocument();
        });
        expect(screen.getByLabelText(/worksheet\.visibility\.UNLISTED\.label/)).toBeChecked();
        expect(screen.getByLabelText(/worksheet\.visibility\.PUBLIC\.label/)).not.toBeChecked();
    });

    it("is inert while disabled", async () => {
        const onChange = vi.fn().mockResolvedValue(undefined);
        render(<VisibilityToggle value="UNLISTED" onChange={onChange} disabled />);

        expect(screen.getByLabelText(/worksheet\.visibility\.PUBLIC\.label/)).toBeDisabled();
        fireEvent.click(screen.getByLabelText(/worksheet\.visibility\.PUBLIC\.label/));
        expect(onChange).not.toHaveBeenCalled();
    });
});
