import * as fs from 'fs';
const CsvReadableStream = require('csv-reader');
import {
  AssetIdentifier,
  EquityType,
  FundProductTypeEnum,
  Instrument,
  SecurityTypeEnum,
} from '../instruments/instrument.model';

export interface InstrumentLoadOptions {
  supportedTickers?: Set<string>;
  maxTickers?: number;
}

/** Seed columns added by state 016, appended after the baseline columns. */
const FIGI_COLUMN = 9;
const SECURITY_TYPE_COLUMN = 10;

/** Every instrument in this state is USD-denominated. */
const CURRENCY = 'USD';

type CdmClassification = {
  securityType: SecurityTypeEnum;
  equityType?: EquityType;
  fundType?: FundProductTypeEnum;
};

/**
 * OpenFIGI's securityType vocabulary mapped onto CDM. Keyed off securityType,
 * not securityType2: OpenFIGI reports securityType2 "Mutual Fund" for SPY,
 * which would classify every ETF as a mutual fund.
 *
 * The seed rows only exercise Common Stock, REIT and ETP. ADR and Preference
 * are mapped defensively so a future seed row gets classified rather than
 * silently defaulted.
 */
const CDM_CLASSIFICATION_BY_OPENFIGI_TYPE: Record<string, CdmClassification> = {
  'Common Stock': { securityType: 'Equity', equityType: { equityType: 'Ordinary' } },
  // A REIT's listed shares are ordinary shares; CDM has no REIT-specific member.
  REIT: { securityType: 'Equity', equityType: { equityType: 'Ordinary' } },
  ETP: { securityType: 'Fund', fundType: 'ExchangeTradedFund' },
  ADR: {
    securityType: 'Equity',
    equityType: { equityType: 'DepositaryReceipt', depositaryReceipt: 'ADR' },
  },
  Preference: {
    securityType: 'Equity',
    equityType: { equityType: 'NonConvertiblePreference' },
  },
};

const DEFAULT_CLASSIFICATION: CdmClassification = {
  securityType: 'Equity',
  equityType: { equityType: 'Ordinary' },
};

type SupplementalSeed = {
  ticker: string;
  companyName: string;
  figi: string;
  openFigiSecurityType: string;
};

/**
 * UBS, DB, FNMA and FNF are not S&P 500 constituents, so a FIGI column on the
 * CSV never reaches them, yet all four are in the default supported set. Their
 * identifiers are therefore baked in here. The other seven are also CSV rows
 * and get deduplicated away before this list is consulted; they carry their
 * identifiers anyway so the list stands on its own if the CSV ever drops them.
 */
const SUPPLEMENTAL_SAMPLE_INSTRUMENTS: SupplementalSeed[] = [
  { ticker: 'MS', companyName: 'Morgan Stanley', figi: 'BBG000BLZRJ2', openFigiSecurityType: 'Common Stock' },
  { ticker: 'UBS', companyName: 'UBS Group AG', figi: 'BBG007DJM539', openFigiSecurityType: 'Common Stock' },
  { ticker: 'C', companyName: 'Citigroup Inc.', figi: 'BBG000FY4S11', openFigiSecurityType: 'Common Stock' },
  { ticker: 'GS', companyName: 'Goldman Sachs Group, Inc.', figi: 'BBG000C6CFJ5', openFigiSecurityType: 'Common Stock' },
  { ticker: 'DB', companyName: 'Deutsche Bank AG', figi: 'BBG000BR1W32', openFigiSecurityType: 'Common Stock' },
  { ticker: 'JPM', companyName: 'JPMorgan Chase & Co.', figi: 'BBG000DMBXR2', openFigiSecurityType: 'Common Stock' },
  { ticker: 'COF', companyName: 'Capital One Financial Corporation', figi: 'BBG000BGKTF9', openFigiSecurityType: 'Common Stock' },
  { ticker: 'DFS', companyName: 'Discover Financial Services', figi: 'BBG000QBR5J5', openFigiSecurityType: 'Common Stock' },
  { ticker: 'FNMA', companyName: 'Fannie Mae', figi: 'BBG000BJQ328', openFigiSecurityType: 'Common Stock' },
  { ticker: 'FIS', companyName: 'Fidelity National Information Services, Inc.', figi: 'BBG000BK2F42', openFigiSecurityType: 'Common Stock' },
  { ticker: 'FNF', companyName: 'Fidelity National Financial, Inc.', figi: 'BBG006N7S6K9', openFigiSecurityType: 'Common Stock' }
];

const PREFERRED_COMPANY_NAME_BY_TICKER: Record<string, string> = {
  MS: 'Morgan Stanley',
  UBS: 'UBS Group AG',
  C: 'Citigroup Inc.',
  GS: 'Goldman Sachs Group, Inc.',
  DB: 'Deutsche Bank AG',
  JPM: 'JPMorgan Chase & Co.',
  COF: 'Capital One Financial Corporation',
  DFS: 'Discover Financial Services',
  FNMA: 'Fannie Mae',
  FIS: 'Fidelity National Information Services, Inc.',
  FNF: 'Fidelity National Financial, Inc.',
  META: 'Meta Platforms, Inc.'
};

function classify(ticker: string, openFigiSecurityType: string): CdmClassification {
  const normalized = String(openFigiSecurityType ?? '').trim();
  const classification = CDM_CLASSIFICATION_BY_OPENFIGI_TYPE[normalized];
  if (classification) {
    if (normalized !== 'Common Stock' && normalized !== 'ETP') {
      const subType = classification.equityType?.equityType ?? classification.fundType;
      console.warn(
        `[instruments] ${ticker}: security type "${normalized}" mapped to closest CDM type ` +
          `${classification.securityType}/${subType}`
      );
    }
    return classification;
  }
  console.warn(
    `[instruments] ${ticker}: unrecognized security type "${normalized}"; defaulting to CDM Equity/Ordinary`
  );
  return DEFAULT_CLASSIFICATION;
}

function buildIdentifiers(ticker: string, figi: string): AssetIdentifier[] {
  const identifiers: AssetIdentifier[] = [{ identifier: ticker, identifierType: 'BBGTICKER' }];
  const trimmedFigi = String(figi ?? '').trim();
  if (trimmedFigi) {
    identifiers.push({ identifier: trimmedFigi, identifierType: 'FIGI' });
  } else {
    // Not every seed row resolves against OpenFIGI. Emitting BBGTICKER only is
    // the documented policy; both identifiers are required of the supported
    // ticker set, which the state smoke test asserts against the live endpoint.
    console.warn(`[instruments] ${ticker}: no FIGI in seed data; emitting BBGTICKER only`);
  }
  return identifiers;
}

/**
 * CDM's Security conditions are structural and TypeScript cannot hold anyone to
 * them at runtime. A disagreement here means the mapping above is wrong, so it
 * fails loudly rather than serving an instrument that misrepresents itself.
 */
function assertCdmConditions(instrument: Instrument): void {
  const { ticker, securityType, equityType, fundType } = instrument;
  if (securityType !== 'Equity' && equityType) {
    throw new Error(
      `[instruments] ${ticker}: CDM EquitySubType violated - securityType=${securityType} with equityType present`
    );
  }
  if (securityType !== 'Fund' && fundType) {
    throw new Error(
      `[instruments] ${ticker}: CDM FundSubType violated - securityType=${securityType} with fundType present`
    );
  }
  if (securityType === 'Equity' && !equityType) {
    throw new Error(`[instruments] ${ticker}: securityType=Equity with no equityType`);
  }
  if (securityType === 'Fund' && !fundType) {
    throw new Error(`[instruments] ${ticker}: securityType=Fund with no fundType`);
  }

  const bbgTicker = instrument.identifiers.find((id) => id.identifierType === 'BBGTICKER');
  if (!bbgTicker || bbgTicker.identifier !== ticker) {
    throw new Error(
      `[instruments] ${ticker}: BBGTICKER identifier must equal the ticker, got ` +
        `"${bbgTicker?.identifier ?? '<absent>'}"`
    );
  }
}

function buildInstrument(
  ticker: string,
  companyName: string,
  figi: string,
  openFigiSecurityType: string
): Instrument {
  const classification = classify(ticker, openFigiSecurityType);
  const instrument: Instrument = {
    ticker,
    companyName,
    currency: CURRENCY,
    securityType: classification.securityType,
    identifiers: buildIdentifiers(ticker, figi)
  };
  if (classification.equityType) {
    instrument.equityType = classification.equityType;
  }
  if (classification.fundType) {
    instrument.fundType = classification.fundType;
  }
  assertCdmConditions(instrument);
  return instrument;
}

export async function loadCsvData(options: InstrumentLoadOptions = {}): Promise<Instrument[]> {
  const supportedTickers = options.supportedTickers;
  const maxTickers = Number(options.maxTickers ?? 0);
  const seenTickers = new Set<string>();

  return new Promise<Instrument[]>((resolve) => {
    const instruments: Instrument[] = [];
    let isHeaderRow = true;
    fs.createReadStream('./data/instruments.csv', 'utf8')
      .pipe(new CsvReadableStream({ trim: true }))
      .on('data', (row: string[]) => {
        if (isHeaderRow) {
          isHeaderRow = false;
          return;
        }
        const rawTicker = String(row[0] ?? '').trim().toUpperCase();
        const ticker = rawTicker === 'FB' ? 'META' : rawTicker;
        if (!ticker) {
          return;
        }
        if (supportedTickers && !supportedTickers.has(ticker)) {
          return;
        }
        if (!seenTickers.has(ticker)) {
          const companyName = PREFERRED_COMPANY_NAME_BY_TICKER[ticker] ?? row[1];
          instruments.push(
            buildInstrument(
              ticker,
              companyName,
              String(row[FIGI_COLUMN] ?? ''),
              String(row[SECURITY_TYPE_COLUMN] ?? '')
            )
          );
          seenTickers.add(ticker);
        }
      })
      .on('end', () => {
        for (const seed of SUPPLEMENTAL_SAMPLE_INSTRUMENTS) {
          if (supportedTickers && !supportedTickers.has(seed.ticker)) {
            continue;
          }
          if (seenTickers.has(seed.ticker)) {
            continue;
          }
          instruments.push(
            buildInstrument(seed.ticker, seed.companyName, seed.figi, seed.openFigiSecurityType)
          );
          seenTickers.add(seed.ticker);
        }
        if (maxTickers > 0) {
          resolve(instruments.slice(0, maxTickers));
          return;
        }
        resolve(instruments);
      });
  });
}
