import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TypeaheadModule } from 'ngx-bootstrap/typeahead';
import { of } from 'rxjs';

import { OrderTicketComponent } from './order-ticket.component';
import { TradeFeedService } from 'main/app/service/trade-feed.service';
import { PriceSnapshotService } from 'main/app/service/price-snapshot.service';
import { Stock } from 'main/app/model/symbol.model';

describe('OrderTicketComponent', () => {
  let component: OrderTicketComponent;
  let fixture: ComponentFixture<OrderTicketComponent>;

  const treasury: Stock = {
    instrumentKey: 'UST-20360515',
    displayName: 'U.S. Treasury Note',
    shortDisplayName: 'UST 10Y',
    assetClass: 'US_TREASURY',
    currency: 'USD',
    securityType: 'Debt',
    matured: false,
    observedAt: '2026-07-30T12:00:00Z'
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [OrderTicketComponent],
      imports: [FormsModule, TypeaheadModule.forRoot()],
      providers: [
        { provide: TradeFeedService, useValue: { subscribe: () => () => {} } },
        { provide: PriceSnapshotService, useValue: { getPrice: () => of(null) } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(OrderTicketComponent);
    component = fixture.componentInstance;
    component.ngOnInit();
    component.selectedInstrument = treasury;
    component.ticket.security = treasury.instrumentKey;
    component.ticket.limitPrice = 99.25;
    fixture.detectChanges();
  });

  it('uses the short Treasury label while preserving the internal key', () => {
    component.stocks = [treasury];
    component.onSelect({ item: treasury, value: treasury.instrumentKey } as any);
    fixture.detectChanges();

    expect(component.selectedCompany).toBe('UST 10Y');
    expect(component.ticket.security).toBe('UST-20360515');
    expect(fixture.nativeElement.textContent).toContain('Internal key: UST-20360515');
  });

  it('shows distinct minimum and increment errors before emitting a Treasury order', () => {
    spyOn(component.create, 'emit');

    component.ticket.quantity = 50;
    component.onCreate();
    fixture.detectChanges();
    expect(component.validationError).toBe('Treasury quantity must be at least 100.');
    expect(fixture.nativeElement.querySelector('#treasuryOrderQuantityError').textContent)
      .toContain('Treasury quantity must be at least 100.');
    expect(component.create.emit).not.toHaveBeenCalled();

    component.ticket.quantity = 150;
    component.onCreate();
    fixture.detectChanges();
    expect(component.validationError).toBe('Treasury quantity must be a multiple of 100.');
    expect(fixture.nativeElement.querySelector('#treasuryOrderQuantityError').textContent)
      .toContain('Treasury quantity must be a multiple of 100.');
    expect(component.create.emit).not.toHaveBeenCalled();

    component.ticket.quantity = 100;
    component.onCreate();
    expect(component.validationError).toBe('');
    expect(component.create.emit).toHaveBeenCalledWith(jasmine.objectContaining({ quantity: 100 }));
  });
});
