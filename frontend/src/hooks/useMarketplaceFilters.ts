import { useCallback, useMemo } from "react";
import { useSearchParams } from "react-router-dom";
import {
    type MarketplaceFilters,
    parseMarketplaceParams,
    writeMarketplaceParams,
} from "../lib/marketplaceParams";

export interface MarketplaceFiltersApi extends MarketplaceFilters {
    setQ(value: string): void;
    setLang(value: string): void;
    setOwner(value: MarketplaceFilters["owner"]): void;
    setSort(value: MarketplaceFilters["sort"]): void;
    setPage(value: number): void;
    reset(): void;
}

/**
 * Binds the marketplace's filters to the URL.
 *
 * <p>Deliberately thin: the encoding lives in `marketplaceParams`, which is where it can be
 * tested. All this adds is the router wiring and the rule that changing a filter returns you to
 * the first page — staying on page four of a result set you just replaced shows an empty screen
 * and looks like a bug.
 */
export function useMarketplaceFilters(): MarketplaceFiltersApi {
    const [params, setParams] = useSearchParams();

    const filters = useMemo(() => parseMarketplaceParams(params), [params]);

    const apply = useCallback(
        (next: Partial<MarketplaceFilters>, keepPage = false) => {
            setParams(
                writeMarketplaceParams({
                    ...filters,
                    ...next,
                    page: keepPage ? (next.page ?? filters.page) : 0,
                }),
                { replace: false },
            );
        },
        [filters, setParams],
    );

    return {
        ...filters,
        setQ: (value) => apply({ q: value }),
        setLang: (value) => apply({ lang: value }),
        setOwner: (value) => apply({ owner: value }),
        setSort: (value) => apply({ sort: value }),
        setPage: (value) => apply({ page: value }, true),
        reset: () => setParams(new URLSearchParams(), { replace: false }),
    };
}
