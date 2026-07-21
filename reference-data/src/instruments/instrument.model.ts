/**
 * Instrument model shaped after the FINOS Common Domain Model (CDM).
 *
 * Enum members are the literal values from CDM's
 * rosetta-source/src/main/rosetta/base-staticdata-asset-common-enum.rosetta.
 *
 * CDM models Asset as a choice type (Cash | Commodity | DigitalAsset |
 * Instrument), and Instrument as a further choice (ListedDerivative | Loan |
 * Security). That tree is documentation for what this record maps onto; it is
 * not built here. The runtime shape below is flat: a Security-shaped record
 * plus its identifiers. See specs/016-cdm-generic-instruments/data-model.md.
 */

/** CDM AssetIdTypeEnum: ProductIdTypeEnum plus three Asset-only sources. */
export type AssetIdTypeEnum =
  | 'BBGID'
  | 'BBGTICKER'
  | 'CUSIP'
  | 'FIGI'
  | 'ISDACRP'
  | 'ISIN'
  | 'Name'
  | 'REDID'
  | 'RIC'
  | 'Other'
  | 'Sicovam'
  | 'SEDOL'
  | 'UPI'
  | 'Valoren'
  | 'Wertpapier'
  | 'CurrencyCode'
  | 'ExchangeCode'
  | 'ClearingCode';

/** CDM SecurityTypeEnum. State 016 only emits Equity and Fund. */
export type SecurityTypeEnum = 'Debt' | 'Equity' | 'Fund' | 'Warrant' | 'Certificate';

/** CDM EquityTypeEnum. */
export type EquityTypeEnum =
  | 'Ordinary'
  | 'NonConvertiblePreference'
  | 'DepositaryReceipt'
  | 'ConvertiblePreference';

/** CDM DepositaryReceiptTypeEnum. */
export type DepositaryReceiptTypeEnum = 'ADR' | 'GDR' | 'IDR' | 'EDR';

/** CDM FundProductTypeEnum. */
export type FundProductTypeEnum =
  | 'MoneyMarketFund'
  | 'ExchangeTradedFund'
  | 'MutualFund'
  | 'OtherFund';

/** CDM AssetIdentifier. */
export interface AssetIdentifier {
  identifier: string;
  identifierType: AssetIdTypeEnum;
}

/**
 * CDM EquityType. Note the asymmetry with fundType: CDM makes equityType a
 * nested type so a depositary receipt can name its flavour, while fundType is
 * the FundProductTypeEnum directly.
 */
export interface EquityType {
  equityType: EquityTypeEnum;
  depositaryReceipt?: DepositaryReceiptTypeEnum;
}

/**
 * CDM Security, minus the layers this state skips (EconomicTerms, Payout,
 * TradableProduct, product qualification).
 *
 * CDM conditions carried over, enforced at load time by load-csv-data.ts:
 *  - EquitySubType: if securityType <> Equity then equityType is absent
 *  - FundSubType:   if securityType <> Fund   then fundType is absent
 */
export interface Instrument {
  ticker: string;
  companyName: string;
  currency: string;
  securityType: SecurityTypeEnum;
  equityType?: EquityType;
  fundType?: FundProductTypeEnum;
  identifiers: AssetIdentifier[];
}
