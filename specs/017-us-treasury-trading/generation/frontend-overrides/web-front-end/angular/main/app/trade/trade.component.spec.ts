import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TradeComponent } from './trade.component';
import { FormsModule } from '@angular/forms';
import { AlertModule } from 'ngx-bootstrap/alert';
import { ModalModule } from 'ngx-bootstrap/modal';
import { AccountService } from '../service/account.service';
import { MockAccountService, MockSymbolService, accounts } from '../test-utils/mocks.service';
import { SymbolService } from '../service/symbols.service';
import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { TradeTicket, Side } from '../model/trade.model';
import { OrderCreateRequest } from '../model/order.model';
import { DropdownModule } from '../dropdown/dropdown.module';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { throwError } from 'rxjs';

describe('TradeComponent', () => {
    let component: TradeComponent;
    let fixture: ComponentFixture<TradeComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [
                TradeComponent
            ],
            imports: [
                FormsModule,
                DropdownModule,
                ModalModule.forRoot(),
                AlertModule.forRoot()
            ],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: AccountService,
                    useClass: MockAccountService
                },
                {
                    provide: SymbolService,
                    useClass: MockSymbolService
                }
            ],
            schemas: [CUSTOM_ELEMENTS_SCHEMA]
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(TradeComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should call symbol service to create ticket on call', async () => {
        const ticket: TradeTicket = {
            accountId: 1,
            quantity: 10,
            security: 'abc',
            side: Side.Buy
        };
        spyOn((component as any).symbolService, 'createTicket').and.callThrough();
        spyOn(component, 'closeTicket');
        component.createTradeTicket(ticket);
        expect((component as any).symbolService.createTicket).toHaveBeenCalledWith(ticket);
        expect(component.closeTicket).toHaveBeenCalled();
    });

    it('keeps the trade ticket open and exposes an API validation message', () => {
        const ticket: TradeTicket = {
            accountId: 1,
            quantity: 200,
            security: 'UST-20360515',
            side: Side.Sell
        };
        spyOn((component as any).symbolService, 'createTicket').and.returnValue(throwError(() => ({
            error: { detail: 'You cannot sell more Treasury face amount than you own and have available.' }
        })));
        spyOn(component, 'closeTicket');

        component.createTradeTicket(ticket);

        expect(component.tradeTicketError)
            .toBe('You cannot sell more Treasury face amount than you own and have available.');
        expect(component.closeTicket).not.toHaveBeenCalled();
    });

    it('keeps the order ticket open and exposes an API validation message', () => {
        const order: OrderCreateRequest = {
            accountId: 1,
            quantity: 200,
            security: 'UST-20360515',
            side: 'Sell',
            limitPrice: 99.25
        };
        spyOn((component as any).orderAdminService, 'createOrder').and.returnValue(throwError(() => ({
            error: { detail: 'You cannot sell more Treasury face amount than you own and have available.' }
        })));
        spyOn(component, 'closeTicket');

        component.createOrderTicket(order);

        expect(component.orderTicketError)
            .toBe('You cannot sell more Treasury face amount than you own and have available.');
        expect(component.closeTicket).not.toHaveBeenCalled();
    });

    it('should get accounts and stocks on init', () => {
        spyOn((component as any).accountService, 'getAccounts').and.callThrough();
        spyOn((component as any).symbolService, 'getStocks').and.callThrough();
        component.ngOnInit();
        expect((component as any).accountService.getAccounts).toHaveBeenCalled();
        expect(component.accounts.length).toEqual(6);
        expect((component as any).symbolService.getStocks).toHaveBeenCalled();
        expect(component.stocks.length).toEqual(5);
    });

    it('should open and close ticket on click', async () => {
        component.accountModel = accounts[0];
        spyOn(component, 'openTicket');
        spyOn(component, 'closeTicket');
        fixture.nativeElement.querySelector('#createTicketBtn').click();
        expect(component.openTicket).toHaveBeenCalled();
        // TODO: need to check how to test bootstrap modal in jasmine
        // fixture.nativeElement.click();
        // expect(component.closeTicket).toHaveBeenCalled();
    });

});
