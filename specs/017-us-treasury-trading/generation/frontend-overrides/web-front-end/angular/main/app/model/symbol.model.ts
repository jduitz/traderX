export interface Symbol {
    name: string;
    sector: string;
    symbol: string;
}

export type AssetClass = 'Stock' | 'ETF' | 'US_TREASURY';

export interface DebtEconomics {
    debtType: 'US_TREASURY_NOTE' | 'US_TREASURY_BOND';
    issuer: string;
    fixedInterest: {
        rateType: 'Fixed';
        couponRatePercent: number;
        couponFrequency: 'Semiannual';
    };
    principalRepayment: {
        style: 'Bullet';
        parAmount: number;
    };
    issueDate: string;
    maturityDate: string;
    originalTermYears: number;
    priceProvenance: {
        sourceType: 'US_TREASURY_AUCTION_RESULT';
        sourceUrl: string;
        officialCleanPrice: number;
        runtimeSeedCleanPrice: number;
        simulated: boolean;
    };
}

export interface Stock {
    instrumentKey: string;
    displayName: string;
    assetClass: AssetClass;
    currency: string;
    securityType: 'Debt' | 'Equity' | 'Fund';
    matured: boolean;
    observedAt: string;
    debtEconomics?: DebtEconomics;
}
