import { useTranslation } from "react-i18next";
import { usePageTitle } from "../hooks/usePageTitle.ts";

/**
 * German-law Impressum, linked from the footer. Publisher/representation
 * details are the University of Passau's own, the same institutional facts
 * a sibling Chair of Software Engineering II project (LitterBox) discloses.
 */
export default function ImprintPage() {
    const { t } = useTranslation();
    usePageTitle(t("imprintPage.pageTitle"));

    return (
        <div className="container py-4" style={{ maxWidth: 720 }}>
            <h1>{t("imprintPage.heading")}</h1>
            <p className="text-muted">{t("imprintPage.subheading")}</p>

            <h2 className="h4 mt-4">{t("imprintPage.publisherHeading")}</h2>
            <p className="mb-0">
                University of Passau<br />
                Innstraße 41<br />
                94032 Passau, Germany<br />
                Telephone: 0851/509-0<br />
                <a href="mailto:gordon.fraser@uni-passau.de">gordon.fraser@uni-passau.de</a>
            </p>

            <hr />

            <h2 className="h4 mt-4">{t("imprintPage.structureHeading")}</h2>
            <p>{t("imprintPage.structureBody")}</p>

            <h2 className="h4 mt-4">{t("imprintPage.representationHeading")}</h2>
            <p>{t("imprintPage.representationBody")}</p>
        </div>
    );
}
