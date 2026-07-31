import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TradeTicketComponent } from './trade-ticket.component';
import { By } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { stocks as dummyStocks, accounts as dummyAccounts } from 'main/app/test-utils/mocks.service';
import { TypeaheadModule } from 'ngx-bootstrap/typeahead';
import { of } from 'rxjs';
import { TradeFeedService } from 'main/app/service/trade-feed.service';
import { PriceSnapshotService } from 'main/app/service/price-snapshot.service';
import { Stock } from 'main/app/model/symbol.model';

describe('TradeTicketComponent', () => {
  let component: TradeTicketComponent;
  let fixture: ComponentFixture<TradeTicketComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [TradeTicketComponent],
      imports: [
        FormsModule,
        TypeaheadModule.forRoot()
      ],
      providers: [
        {
          provide: TradeFeedService,
          useValue: { subscribe: () => () => {} }
        },
        {
          provide: PriceSnapshotService,
          useValue: {
            getPrice: () => of({
              ticker: 'UST-20360515',
              instrumentKey: 'UST-20360515',
              assetClass: 'US_TREASURY',
              price: 99.257,
              openPrice: 99.257,
              closePrice: 99.257,
              asOf: '2026-07-30T12:00:00Z',
              quoteTimestamp: '2026-07-30T12:00:00Z',
              approximateYtmPercent: 4.47,
              matured: false,
              source: 'simulated',
              simulated: true
            })
          }
        }
      ]
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(TradeTicketComponent);
    component = fixture.componentInstance;
    component.account = dummyAccounts[0];
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should show ticket with initial values', async () => {
    await fixture.whenStable();
    const quantityField = fixture.debugElement.query(By.css('#quantityField'));
    expect(quantityField.nativeElement.value).toEqual('0');
    const buyButton = fixture.debugElement.query(By.css('#buyButton'));
    expect(buyButton.nativeElement.checked).toBeTrue();
    const accountLabel = fixture.debugElement.query(By.css('#accountLabel'));
    expect(accountLabel.nativeElement.value).toEqual(component.account?.displayName);
  });

  it('should update ticket object with given values on create click and emit create event', async () => {
    const quantityField = fixture.debugElement.query(By.css('#quantityField'));
    quantityField.nativeElement.value = 10;
    quantityField.nativeElement.dispatchEvent(new Event('input'));
    const sellButton = fixture.debugElement.query(By.css('#sellButton'));
    sellButton.nativeElement.click();
    component.ticket.security = dummyStocks[0].instrumentKey;

    spyOn(component.create, 'emit');
    const createButton = fixture.debugElement.query(By.css('#createButton'));
    createButton.nativeElement.click();
    fixture.detectChanges();

    expect(component.create.emit).toHaveBeenCalledWith(
      {
        quantity: 10, accountId: component.account?.id as any, side: 'Sell', security: component.ticket.security
      });
  });

  // it('getStockTicker should return instrument key value from stock', () => {
  //   expect(component.getStockTicker(dummyStocks[0])).toEqual(dummyStocks[0].instrumentKey);
  // });

  // it('getStockLabel should return display name from stock', () => {
  //   expect(component.getStockLabel(dummyStocks[0])).toEqual(dummyStocks[0].displayName);
  // });

  it('should emit cancel on cancel click', async () => {
    spyOn(component.cancel, 'emit');
    const cancelButton = fixture.debugElement.query(By.css('#cancelButton'));
    cancelButton.nativeElement.click();
    fixture.detectChanges();
    expect(component.cancel.emit).toHaveBeenCalled();
  });

  it('onQueryChange should return results based on given query', () => {
    component.stocks = dummyStocks;
    expect(component.filteredStocks.length).toEqual(0);
    component.ngOnChanges({ stocks: { currentValue: dummyStocks } } as any);
    const stockInput = fixture.debugElement.query(By.css('#stock-input'));
    stockInput.nativeElement.value = '';
    stockInput.nativeElement.dispatchEvent(new Event('input'));
    expect(component.filteredStocks.length).toEqual(5);
  });

  it('shows Treasury face amount and clean percent-of-par valuation without a dollar price', () => {
    const treasury: Stock = {
      instrumentKey: 'UST-20360515',
      displayName: 'U.S. Treasury Note 4.375% due May 15, 2036',
      assetClass: 'US_TREASURY',
      currency: 'USD',
      securityType: 'Debt',
      matured: false,
      observedAt: '2026-07-30T12:00:00Z',
      debtEconomics: {
        debtType: 'US_TREASURY_NOTE',
        issuer: 'United States Department of the Treasury',
        fixedInterest: {
          rateType: 'Fixed',
          couponRatePercent: 4.375,
          couponFrequency: 'Semiannual'
        },
        principalRepayment: { style: 'Bullet', parAmount: 100 },
        issueDate: '2026-05-15',
        maturityDate: '2036-05-15',
        originalTermYears: 10,
        priceProvenance: {
          sourceType: 'US_TREASURY_AUCTION_RESULT',
          sourceUrl: 'https://example.test/auction.pdf',
          officialCleanPrice: 99.256552,
          runtimeSeedCleanPrice: 99.257,
          simulated: true
        }
      }
    };
    component.stocks = [treasury];
    component.ngOnChanges({ stocks: { currentValue: [treasury] } } as any);
    component.onSelect({ item: treasury, value: treasury.instrumentKey } as any);
    component.ticket.quantity = 100_000;
    fixture.detectChanges();

    expect(component.isTreasury).toBeTrue();
    expect(component.formatLivePrice()).toBe('99.257% of par');
    expect(component.formatLivePrice()).not.toContain('$');
    expect(component.estimatedCleanValue).toBeCloseTo(99_257, 3);
    expect(component.remainingMaturity).toContain('days');
    expect(component.filteredStocks[0].selectorGroup).toBe('U.S. Treasuries');
    expect(fixture.nativeElement.textContent).toContain('Face Amount');
    expect(fixture.nativeElement.textContent).toContain('Accrued interest and dirty settlement value are excluded');
  });

  it('does not emit an invalid increment or matured Treasury trade', () => {
    component.selectedInstrument = {
      instrumentKey: 'UST-20280630',
      displayName: 'Treasury',
      assetClass: 'US_TREASURY',
      currency: 'USD',
      securityType: 'Debt',
      matured: false,
      observedAt: '2026-07-30T12:00:00Z'
    };
    component.ticket.security = 'UST-20280630';
    component.ticket.quantity = 150;
    spyOn(component.create, 'emit');
    component.onCreate();
    expect(component.create.emit).not.toHaveBeenCalled();

    component.ticket.quantity = 100;
    component.selectedQuote = { ticker: 'UST-20280630', price: 99, matured: true } as any;
    component.onCreate();
    expect(component.create.emit).not.toHaveBeenCalled();
  });

});
