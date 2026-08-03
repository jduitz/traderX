import { Component, Input, Output, EventEmitter, OnChanges, OnInit, OnDestroy, SimpleChanges } from '@angular/core';
import { PriceTick, TradeTicket } from 'main/app/model/trade.model';
import { Stock } from 'main/app/model/symbol.model';
import { Account } from 'main/app/model/account.model';
import { TypeaheadMatch } from 'ngx-bootstrap/typeahead';
import { TradeFeedService } from 'main/app/service/trade-feed.service';
import { PriceSnapshotService } from 'main/app/service/price-snapshot.service';

@Component({
    standalone: false,
  selector: 'app-trade-ticket',
  templateUrl: './trade-ticket.component.html',
  styleUrls: ['./trade-ticket.component.scss']
})
export class TradeTicketComponent implements OnInit, OnChanges, OnDestroy {

  @Input() stocks: Stock[];
  @Input() account: Account | undefined;
  @Input() presetSecurity = '';
  @Input() serverError = '';

  @Output() create = new EventEmitter<TradeTicket>();
  @Output() cancel = new EventEmitter();

  selectedCompany?: string = undefined;
  ticket: TradeTicket;
  filteredStocks: Array<Stock & { matchLabel: string; selectorGroup: string }> = [];
  assetClassFilter: 'All' | 'Stock' | 'ETF' | 'US_TREASURY' = 'All';
  selectedInstrument?: Stock;
  selectedQuote?: PriceTick;
  selectedPrice: number | null = null;
  selectedPriceAsOf: string | null = null;
  validationError = '';
  private selectedPriceTicker: string | null = null;
  private selectedPriceAsOfEpoch = 0;
  private priceStreamUnsubscribeFn?: Function;

  constructor(
    private tradeFeed: TradeFeedService,
    private priceSnapshots: PriceSnapshotService
  ) {}

  ngOnInit() {
    this.ticket = {
      quantity: 0,
      accountId: this.account?.id || 0,
      side: 'Buy',
      security: ''
    };

    this.refreshFilteredStocks();

    this.applyPresetSecurity(this.presetSecurity);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.stocks) {
      this.refreshFilteredStocks();
    }
    if (changes.account && this.ticket) {
      this.ticket.accountId = this.account?.id || 0;
    }
    if (changes.presetSecurity && this.ticket) {
      this.applyPresetSecurity(changes.presetSecurity.currentValue);
    }
  }

  ngOnDestroy() {
    this.priceStreamUnsubscribeFn?.();
  }

  onSelect(e: TypeaheadMatch): void {
    this.validationError = '';
    console.log('Selected value: ', e.value);
    const selectedStock = e.item as Stock & { matchLabel?: string };
    this.selectedInstrument = selectedStock;
    this.ticket.security = selectedStock.instrumentKey;
    this.selectedCompany = this.toShortLabel(selectedStock);
    this.subscribeToTickerPrice(selectedStock.instrumentKey);
  }

  onBlur(): void {
    if (this.selectedCompany) return;
    this.ticket.security = '';
    this.selectedInstrument = undefined;
    this.selectedQuote = undefined;
    this.selectedPrice = null;
    this.selectedPriceAsOf = null;
    this.selectedPriceAsOfEpoch = 0;
    this.selectedPriceTicker = null;
    this.priceStreamUnsubscribeFn?.();
    this.priceStreamUnsubscribeFn = undefined;
  }

  onCreate() {
    this.validationError = '';
    if (!this.ticket.security || this.isMatured || (!this.isTreasury && !this.ticket.quantity)) {
      console.warn('Either security is not selected or quanity is not set!');
      return;
    }
    if (this.isTreasury && (!Number.isFinite(this.ticket.quantity) || this.ticket.quantity < 100)) {
      this.validationError = 'Treasury quantity must be at least 100.';
      return;
    }
    if (this.isTreasury && this.ticket.quantity % 100 !== 0) {
      this.validationError = 'Treasury quantity must be a multiple of 100.';
      return;
    }
    console.log('create tradeTicket', this.ticket);
    this.create.emit(this.ticket);
  }

  onCancel() {
    this.cancel.emit();
  }

  formatLivePrice(): string {
    if (this.selectedPrice == null) {
      return 'Streaming...';
    }
    if (this.isTreasury) {
      return `${this.selectedPrice.toFixed(3)}% of par`;
    }
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 3,
      maximumFractionDigits: 3
    }).format(this.selectedPrice);
  }

  onAssetClassFilterChange(): void {
    this.refreshFilteredStocks();
  }

  get isTreasury(): boolean {
    return this.selectedInstrument?.assetClass === 'US_TREASURY';
  }

  get isMatured(): boolean {
    return this.isTreasury && (this.selectedQuote?.matured || this.selectedInstrument?.matured) === true;
  }

  get estimatedCleanValue(): number | null {
    if (!this.isTreasury || this.selectedPrice == null) {
      return null;
    }
    return this.ticket.quantity * this.selectedPrice / 100;
  }

  get remainingMaturity(): string {
    const maturity = this.selectedInstrument?.debtEconomics?.maturityDate;
    const quoteTime = this.selectedQuote?.quoteTimestamp || this.selectedQuote?.asOf;
    if (!maturity || !quoteTime) {
      return '-';
    }
    const days = Math.max(0, Math.ceil(
      (new Date(`${maturity}T00:00:00Z`).getTime() - new Date(quoteTime).getTime()) / 86400000
    ));
    return `${days} days`;
  }

  formatAsOf(): string {
    if (!this.selectedPriceAsOf) {
      return '';
    }
    const ts = new Date(this.selectedPriceAsOf);
    if (Number.isNaN(ts.getTime())) {
      return '';
    }
    return ts.toLocaleTimeString();
  }

  private subscribeToTickerPrice(ticker: string): void {
    const normalizedTicker = String(ticker || '').trim().toUpperCase();
    if (!normalizedTicker) {
      return;
    }

    if (this.selectedPriceTicker === normalizedTicker && this.priceStreamUnsubscribeFn) {
      return;
    }

    this.priceStreamUnsubscribeFn?.();
    this.selectedPrice = null;
    this.selectedPriceAsOf = null;
    this.selectedPriceAsOfEpoch = 0;
    this.selectedPriceTicker = normalizedTicker;

    this.priceSnapshots.getPrice(normalizedTicker).subscribe((snapshot) => {
      if (!snapshot) {
        return;
      }
      this.selectedQuote = snapshot;
      this.applyPriceCandidate(normalizedTicker, snapshot.price, snapshot.asOf ?? null);
    });

    this.priceStreamUnsubscribeFn = this.tradeFeed.subscribe(`pricing.${normalizedTicker}`, (tick: PriceTick) => {
      if (!tick || String(tick.ticker || '').trim().toUpperCase() !== normalizedTicker) {
        return;
      }
      this.selectedQuote = tick;
      this.applyPriceCandidate(normalizedTicker, tick.price, tick.asOf ?? null);
    });
  }

  private applyPriceCandidate(ticker: string, price: number, asOf: string | null): void {
    if (this.selectedPriceTicker !== ticker) {
      return;
    }
    const numericPrice = Number(price);
    if (!Number.isFinite(numericPrice)) {
      return;
    }

    const asOfEpoch = this.toEpochMs(asOf);
    if (asOfEpoch != null) {
      if (asOfEpoch < this.selectedPriceAsOfEpoch) {
        return;
      }
      this.selectedPriceAsOfEpoch = asOfEpoch;
    } else if (this.selectedPriceAsOfEpoch > 0) {
      return;
    }

    this.selectedPrice = numericPrice;
    this.selectedPriceAsOf = asOf || null;
  }

  private toEpochMs(asOf: string | null | undefined): number | null {
    if (!asOf) {
      return null;
    }
    const ts = new Date(asOf).getTime();
    return Number.isFinite(ts) ? ts : null;
  }

  private toMatchLabel(stock: Stock): string {
    if (stock.assetClass === 'US_TREASURY') {
      const coupon = stock.debtEconomics?.fixedInterest?.couponRatePercent;
      const maturity = stock.debtEconomics?.maturityDate;
      if (coupon != null && maturity) {
        const maturityDate = new Date(`${maturity}T00:00:00Z`);
        const month = maturityDate.toLocaleString('en-US', { month: 'short', timeZone: 'UTC' });
        const year = String(maturityDate.getUTCFullYear()).slice(-2);
        return `${this.toShortLabel(stock)} — ${Number(coupon).toFixed(3)}% ${month}-${year}`;
      }
      return this.toShortLabel(stock);
    }
    return `${stock.instrumentKey} - ${stock.displayName}`;
  }

  private toShortLabel(stock: Stock): string {
    return stock.shortDisplayName || stock.instrumentKey;
  }

  private refreshFilteredStocks(): void {
    this.filteredStocks = (this.stocks || [])
      .filter((instrument) => this.assetClassFilter === 'All' || instrument.assetClass === this.assetClassFilter)
      .map((instrument) => ({
        ...instrument,
        selectorGroup: instrument.assetClass === 'US_TREASURY'
          ? 'U.S. Treasuries'
          : (instrument.assetClass === 'ETF' ? 'ETFs' : 'Stocks'),
        matchLabel: this.toMatchLabel(instrument)
      }));
  }

  private applyPresetSecurity(rawSecurity: string): void {
    const normalized = String(rawSecurity || '').trim().toUpperCase();
    if (!normalized || !this.ticket) {
      return;
    }
    const matched = (this.stocks || []).find((stock) => String(stock.instrumentKey || '').toUpperCase() === normalized);
    if (matched) {
      this.selectedInstrument = matched;
      this.ticket.security = matched.instrumentKey;
      this.selectedCompany = this.toShortLabel(matched);
      this.subscribeToTickerPrice(matched.instrumentKey);
      return;
    }
    this.ticket.security = normalized;
    this.selectedCompany = normalized;
    this.subscribeToTickerPrice(normalized);
  }
}
