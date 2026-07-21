import { Injectable } from '@nestjs/common';
import { loadCsvData } from '../data-loader/load-csv-data';
import { Instrument } from './instrument.model';

@Injectable()
export class InstrumentsService {
  private readonly instruments: Promise<Instrument[]>;

  constructor() {
    const supportedTickers = this.parseSupportedTickers(
      process.env.REFERENCE_DATA_SUPPORTED_TICKERS
    );
    const maxTickers = this.parsePositiveInt(process.env.REFERENCE_DATA_MAX_TICKERS);
    this.instruments = loadCsvData({ supportedTickers, maxTickers });
  }

  async findAll(): Promise<Instrument[]> {
    return this.instruments;
  }

  async findByTicker(ticker: string): Promise<Instrument | undefined> {
    return (await this.instruments).find((instrument) => instrument.ticker === ticker);
  }

  private parseSupportedTickers(input?: string): Set<string> | undefined {
    const raw = String(input ?? '').trim();
    if (!raw) {
      return undefined;
    }
    const tickers = raw
      .split(',')
      .map((ticker) => ticker.trim().toUpperCase())
      .filter(Boolean);
    if (tickers.length === 0) {
      return undefined;
    }
    return new Set(tickers);
  }

  private parsePositiveInt(input?: string): number | undefined {
    const parsed = Number(input);
    if (!Number.isInteger(parsed) || parsed <= 0) {
      return undefined;
    }
    return parsed;
  }
}
