import { useTranslation } from "react-i18next";
import { usePageTitle } from "../hooks/usePageTitle.ts";

/**
 * Static "who builds this and how to reach them" page, linked from the
 * footer. Mirrors the Contact page of a sibling Chair of Software
 * Engineering II project (LitterBox), which the same disclosure applies to.
 */
export default function ContactPage() {
    const { t } = useTranslation();
    usePageTitle(t("contactPage.pageTitle"));

    return (
        <div className="container py-4" style={{ maxWidth: 720 }}>
            <h1>{t("contactPage.heading")}</h1>
            <p className="text-muted">{t("contactPage.subheading")}</p>

            <h2 className="h4 mt-4">{t("contactPage.teamHeading")}</h2>
            <p>{t("contactPage.teamBody")}</p>
            <p>
                <a href="https://www.fim.uni-passau.de/lehrstuhl-fuer-software-engineering-ii"
                   target="_blank" rel="noopener noreferrer">
                    {t("contactPage.chairLink")}
                </a>
            </p>

            <hr />

            <h2 className="h4 mt-4">{t("contactPage.questionsHeading")}</h2>
            <p>{t("contactPage.questionsBody")}</p>
            <p>
                <a href="https://github.com/se2p/codeowl/issues" target="_blank" rel="noopener noreferrer">
                    {t("contactPage.issueLink")}
                </a>
                <br />
                <a href="https://github.com/se2p/codeowl" target="_blank" rel="noopener noreferrer">
                    {t("contactPage.githubLink")}
                </a>
            </p>

            <hr />

            <h2 className="h4 mt-4">{t("contactPage.institutionalHeading")}</h2>
            <p className="mb-0">
                University of Passau<br />
                Innstraße 41<br />
                94032 Passau, Germany<br />
                Telephone: 0851/509-0<br />
                <a href="mailto:gordon.fraser@uni-passau.de">gordon.fraser@uni-passau.de</a>
            </p>
        </div>
    );
}
