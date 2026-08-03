import { OrderBlotterComponent } from './order-blotter.component';

describe('OrderBlotterComponent', () => {
  it('formats Treasury securities with their short display label', () => {
    const component = new OrderBlotterComponent({} as any, {} as any, {} as any);
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
