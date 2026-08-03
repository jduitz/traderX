import { ComponentFixture, TestBed, tick, fakeAsync } from '@angular/core/testing';
import { AgGridModule } from 'ag-grid-angular';
import { TradeBlotterComponent } from './trade-blotter.component';
import { PositionService } from 'main/app/service/position.service';
import { MockTradeService, MockTradeFeedService, accounts as dummyAccounts, trades } from 'main/app/test-utils/mocks.service';
import { TradeFeedService } from 'main/app/service/trade-feed.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('TradeBlotterComponent', () => {
    let component: TradeBlotterComponent;
    let fixture: ComponentFixture<TradeBlotterComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [TradeBlotterComponent],
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
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(TradeBlotterComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should show given trades columns in the grid', async () => {
        component.account = dummyAccounts[0];
        component.ngOnChanges({ account: { currentValue: dummyAccounts[0] } } as any);
        const rows = fixture.nativeElement.querySelectorAll('.ag-row');
        expect(component.columnDefs.length).toEqual(7);
        expect(rows.length).toEqual(0);
    });

    it('should call getTrades on changes and set trades', fakeAsync(() => {
        expect(component.account).not.toBeDefined();
        component.account = dummyAccounts[0];
        component.ngOnChanges({ account: { currentValue: dummyAccounts[0] } } as any);
        expect(component.trades.length).toEqual(2);
        fixture.detectChanges();
        tick(100)
        const rows = fixture.nativeElement.querySelectorAll('.ag-center-cols-container .ag-row');
        expect(rows.length).toEqual(2);
        expect(component.pendingTrades.length).toEqual(0);
    }));

    it('should call getTrades and subscribe to trade feed service for given account', async () => {
        spyOn((component as any).tradeService, 'getTrades').and.callThrough();
        spyOn((component as any).tradeFeed, 'subscribe').and.callThrough();
        const testAccount = dummyAccounts[0];
        component.account = testAccount;
        component.ngOnChanges({ account: { currentValue: testAccount } } as any);
        expect((component as any).tradeService.getTrades).toHaveBeenCalledWith(testAccount.id);
        expect((component as any).tradeFeed.subscribe).toHaveBeenCalled();

    });

    it('getRowId should return id from trade data', () => {
        const params = { data: trades[0] } as any;
        expect(component.getRowId(params)).toEqual(`Trade-${trades[0].id}`);
    });

    it('formats Treasury securities with their short display label', () => {
        component.instruments = [{
            instrumentKey: 'UST-20360515',
            displayName: 'U.S. Treasury Note 4.375% due May 15, 2036',
            shortDisplayName: 'UST 10Y',
            assetClass: 'US_TREASURY',
            currency: 'USD',
            securityType: 'Debt',
            matured: false,
            observedAt: '2026-07-30T12:00:00Z'
        }];

        expect((component as any).formatSecurity('UST-20360515')).toBe('UST 10Y');
        expect((component as any).formatSecurity('IBM')).toBe('IBM');
    });

});
