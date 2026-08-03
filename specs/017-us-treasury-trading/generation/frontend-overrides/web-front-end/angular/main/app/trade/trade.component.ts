import { Component, OnInit, TemplateRef } from '@angular/core';
import { Subject } from 'rxjs';
import { TradeTicket } from '../model/trade.model';
import { OrderCreateRequest } from '../model/order.model';
import { Account } from '../model/account.model';
import { AccountService } from '../service/account.service';
import { Stock } from '../model/symbol.model';
import { SymbolService } from '../service/symbols.service';
import { BsModalService, BsModalRef } from 'ngx-bootstrap/modal';
import { OrderAdminService } from '../service/order-admin.service';

@Component({
    standalone: false,
    selector: 'app-trade',
    templateUrl: './trade.component.html',
    styleUrls: ['./trade.component.scss']
})
export class TradeComponent implements OnInit {
    private readonly allAccountsOption: Account = {
        id: 0,
        displayName: 'All Accounts'
    };
    accounts: Account[] = [];
    realAccounts: Account[] = [];
    accountIds: number[] = [];
    accountNameById: { [accountId: number]: string } = {};
    accountModel?: Account = undefined;
    stocks: Stock[] = [];
    blotterAssetClassFilter: 'All' | 'Stock' | 'ETF' | 'US_TREASURY' = 'All';
    modalRef?: BsModalRef;
    createTicketResponse: any;
    createOrderResponse: any;
    tradeTicketError = '';
    orderTicketError = '';
    selectedOrderSecurity = '';
    private account = new Subject<Account>();

    constructor(private accountService: AccountService,
        private symbolService: SymbolService,
        private orderAdminService: OrderAdminService,
        private modalService: BsModalService) { }

    ngOnInit(): void {
        this.accountService.getAccounts().subscribe((accounts) => {
            this.realAccounts = accounts;
            this.accountNameById = this.realAccounts.reduce((acc, account) => {
                acc[account.id] = account.displayName;
                return acc;
            }, {} as { [accountId: number]: string });
            this.accountIds = this.realAccounts.map((account) => account.id);
            this.accounts = [this.allAccountsOption, ...this.realAccounts];
            this.setAccount(
                this.realAccounts.find((account) => account.id === 17017)
                ?? this.realAccounts[0]
                ?? this.allAccountsOption);
            console.log(this.accounts);
        });
        this.symbolService.getStocks().subscribe((stocks) => this.stocks = stocks);
    }

    onAccountChange(account: Account) {
        console.log('onAccountChange', arguments);
        account && this.setAccount(account);
    }

    getAccountName(item: Account) {
        return item.displayName;
    }

    openTicket(template: TemplateRef<any>) {
        if (this.isAllAccountsSelected) {
            return;
        }
        this.tradeTicketError = '';
        this.modalRef = this.modalService.show(template);
    }

    openOrderTicket(template: TemplateRef<any>) {
        if (this.isAllAccountsSelected) {
            return;
        }
        this.orderTicketError = '';
        this.modalRef = this.modalService.show(template);
    }

    createTradeTicket(ticket: TradeTicket) {
        if (this.isAllAccountsSelected) {
            this.createTicketResponse = { success: false, message: 'Select a specific account to create a trade.' };
            return;
        }
        console.log('createTradeTicket', ticket);
        this.tradeTicketError = '';
        this.symbolService.createTicket(ticket).subscribe({
            next: (response) => {
                console.log(response);
                this.createTicketResponse = response;
                this.closeTicket();
            },
            error: (error) => {
                this.tradeTicketError = this.apiErrorMessage(
                    error,
                    'The Treasury trade could not be created.');
            }
        });
    }

    createOrderTicket(order: OrderCreateRequest) {
        if (this.isAllAccountsSelected) {
            this.createOrderResponse = { success: false, message: 'Select a specific account to create an order.' };
            return;
        }
        this.orderTicketError = '';
        this.orderAdminService.createOrder(order).subscribe({
            next: (response) => {
                this.createOrderResponse = response;
                this.closeTicket();
            },
            error: (error) => {
                this.orderTicketError = this.apiErrorMessage(
                    error,
                    'The Treasury order could not be created.');
            }
        });
    }

    onOrderSecuritySelected(security: string) {
        this.selectedOrderSecurity = String(security || '').trim().toUpperCase();
    }

    closeTicket() {
        this.modalRef?.hide();
    }

    onCloseAlert() {
        this.createTicketResponse = undefined;
    }

    onCloseOrderAlert() {
        this.createOrderResponse = undefined;
    }

    private setAccount(account: Account) {
        this.accountModel = account;
        this.account.next(account);
    }

    get isAllAccountsSelected(): boolean {
        return (this.accountModel?.id ?? -1) === this.allAccountsOption.id;
    }

    private apiErrorMessage(error: any, fallback: string): string {
        const responseBody = error?.error;
        if (typeof responseBody === 'string' && responseBody.trim()) {
            return responseBody.trim();
        }
        return responseBody?.detail || responseBody?.message || error?.message || fallback;
    }
}
