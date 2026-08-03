import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AgGridModule } from 'ag-grid-angular';
import { PositionBlotterComponent } from './position-blotter.component';
import { PositionService } from 'main/app/service/position.service';
import { MockTradeService, MockTradeFeedService, positions } from 'main/app/test-utils/mocks.service';
import { TradeFeedService } from 'main/app/service/trade-feed.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('PositionBlotterComponent', () => {
  let component: PositionBlotterComponent;
  let fixture: ComponentFixture<PositionBlotterComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [PositionBlotterComponent],
      imports: [
        AgGridModule
      ],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: PositionService,
          useClass: MockTradeService
        },
        {
          provide: TradeFeedService,
          useClass: MockTradeFeedService
        }
      ]
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(PositionBlotterComponent);
    component = fixture.componentInstance;
    component.positions = positions;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should show given positions in the grid', async () => {
    const rows = fixture.nativeElement.querySelectorAll('.ag-center-cols-container .ag-row');
    expect(component.columnDefs.length).toEqual(10);
    expect(rows.length).toEqual(2);
    const firstRow = rows[0];
    expect(firstRow.children[0].innerText).toEqual(component.positions[0].security);
    expect(firstRow.children[1].innerText).toEqual(component.positions[0].quantity.toString());
  });

  it('provides full hover text for truncated position headers', () => {
    const byField = new Map(component.columnDefs.map((column) => [column.field, column]));

    expect(byField.get('quantity')?.headerTooltip).toBe('Quantity / Face Amount');
    expect(byField.get('averageCostBasis')?.headerTooltip).toBe('Average Cost / Clean Purchase Price');
    expect(byField.get('marketValue')?.headerTooltip).toBe('Position Value');
  });

  it('should upsert an existing position row for matching security', () => {
    const applyTransaction = jasmine.createSpy('applyTransaction');
    const getRowNode = jasmine.createSpy('getRowNode').and.returnValue({
      data: {
        security: 'IBM',
        quantity: 10
      }
    });
    component.gridApi = {
      getRowNode,
      applyTransaction,
      forEachNode: () => {}
    } as any;

    component.update({
      security: 'IBM',
      quantity: 35,
      updated: '2026-03-31T00:00:00.000Z'
    });

    expect(getRowNode).toHaveBeenCalledWith('Position-IBM');
    expect(applyTransaction).toHaveBeenCalledWith({
      update: [jasmine.objectContaining({ security: 'IBM', quantity: 35 })]
    });
  });

  it('should keep getRowId callback usable when invoked without component context', () => {
    const detached = component.getRowId;
    const rowId = detached({
      data: {
        security: 'IBM'
      }
    } as any);
    expect(rowId).toBe('Position-IBM');
  });

  it('values Treasury face amount at clean percent of par and preserves equity valuation', () => {
    component.instruments = [
      {
        instrumentKey: 'UST-20360515',
        displayName: 'Treasury',
        shortDisplayName: 'UST 10Y',
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
            sourceUrl: 'https://example.test',
            officialCleanPrice: 99.256552,
            runtimeSeedCleanPrice: 99.257,
            simulated: true
          }
        }
      },
      {
        instrumentKey: 'IBM',
        displayName: 'IBM',
        assetClass: 'Stock',
        currency: 'USD',
        securityType: 'Equity',
        matured: false,
        observedAt: '2026-07-30T12:00:00Z'
      }
    ];

    const treasury = (component as any).recomputePosition({
      security: 'UST-20360515',
      quantity: 100_000,
      averageCostBasis: 99.257,
      marketPrice: 99.500
    });
    const stock = (component as any).recomputePosition({
      security: 'IBM',
      quantity: 10,
      averageCostBasis: 100,
      marketPrice: 110
    });

    expect(treasury.costBasisValue).toBeCloseTo(99_257, 3);
    expect(treasury.marketValue).toBeCloseTo(99_500, 3);
    expect(treasury.pnl).toBeCloseTo(243, 3);
    expect(stock.costBasisValue).toBe(1_000);
    expect(stock.marketValue).toBe(1_100);
    expect(stock.pnl).toBe(100);
    expect((component as any).formatMarketPrice(99.5, 99.2, 'UST-20360515')).toBe('99.500%');
    expect((component as any).formatSecurity('UST-20360515')).toBe('UST 10Y');
    expect((component as any).formatSecurity('IBM')).toBe('IBM');
  });

});
