package name.abuchen.portfolio.ui.api.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;

import java.time.LocalDateTime;

import org.junit.Test;

import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.money.CurrencyUnit;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.ui.api.dto.TransactionMutationDto;

public class TransactionManagementServiceTest
{
    @Test
    public void createsAccountDepositTransaction()
    {
        var client = new Client();
        var account = new Account("Cash");
        account.setCurrencyCode(CurrencyUnit.EUR);
        client.addAccount(account);

        var request = new TransactionMutationDto();
        request.setTransactionType("ACCOUNT");
        request.setType("DEPOSIT");
        request.setOwnerUuid(account.getUUID());
        request.setDateTime(LocalDateTime.of(2026, 5, 30, 10, 0));
        request.setAmount(150.25);
        request.setNote("  Initial deposit  ");

        var pair = TransactionManagementService.createTransaction(client, request);

        assertThat(account.getTransactions().size(), is(1));
        assertThat(pair.getOwner(), is(account));
        assertThat(pair.getTransaction(), notNullValue());

        var transaction = (AccountTransaction) pair.getTransaction();
        assertThat(transaction.getType(), is(AccountTransaction.Type.DEPOSIT));
        assertThat(transaction.getAmount(), is(Math.round(150.25 * Values.Amount.divider())));
        assertThat(transaction.getNote(), is("Initial deposit"));
    }

    @Test
    public void createsPortfolioBuyTransaction()
    {
        var client = new Client();
        var account = new Account("Cash");
        account.setCurrencyCode(CurrencyUnit.EUR);
        client.addAccount(account);

        var portfolio = new Portfolio("Broker");
        portfolio.setReferenceAccount(account);
        client.addPortfolio(portfolio);

        var security = new Security("Apple Inc.", CurrencyUnit.EUR);
        client.addSecurity(security);

        var request = new TransactionMutationDto();
        request.setTransactionType("PORTFOLIO");
        request.setType("BUY");
        request.setOwnerUuid(portfolio.getUUID());
        request.setSecurityUuid(security.getUUID());
        request.setDateTime(LocalDateTime.of(2026, 5, 30, 11, 0));
        request.setAmount(1000.0);
        request.setShares(10.0);

        var pair = TransactionManagementService.createTransaction(client, request);

        assertThat(portfolio.getTransactions().size(), is(1));
        assertThat(account.getTransactions().size(), is(1));

        var transaction = (PortfolioTransaction) pair.getTransaction();
        assertThat(transaction.getType(), is(PortfolioTransaction.Type.BUY));
        assertThat(transaction.getSecurity(), is(security));
        assertThat(transaction.getShares(), is(Math.round(10.0 * Values.Share.factor())));
    }

    @Test
    public void updatesAndDeletesAccountTransaction()
    {
        var client = new Client();
        var account = new Account("Cash");
        account.setCurrencyCode(CurrencyUnit.EUR);
        client.addAccount(account);

        var createRequest = new TransactionMutationDto();
        createRequest.setTransactionType("ACCOUNT");
        createRequest.setType("DEPOSIT");
        createRequest.setOwnerUuid(account.getUUID());
        createRequest.setDateTime(LocalDateTime.of(2026, 5, 30, 10, 0));
        createRequest.setAmount(100.0);

        var created = TransactionManagementService.createTransaction(client, createRequest);
        var transactionUuid = created.getTransaction().getUUID();

        var updateRequest = new TransactionMutationDto();
        updateRequest.setAmount(250.0);
        updateRequest.setNote("Updated note");

        var updated = TransactionManagementService.updateTransaction(client, transactionUuid, updateRequest);
        assertThat(updated.getTransaction().getAmount(), is(Math.round(250.0 * Values.Amount.divider())));
        assertThat(updated.getTransaction().getNote(), is("Updated note"));

        TransactionManagementService.deleteTransaction(client, transactionUuid);
        assertThat(account.getTransactions().isEmpty(), is(true));
    }

    @Test
    public void findsTransactionByUuid()
    {
        var client = new Client();
        var account = new Account("Cash");
        account.setCurrencyCode(CurrencyUnit.EUR);
        account.addTransaction(new AccountTransaction(LocalDateTime.of(2026, 5, 30, 10, 0), CurrencyUnit.EUR,
                        1_000, null, AccountTransaction.Type.DEPOSIT));
        client.addAccount(account);

        var transactionUuid = account.getTransactions().get(0).getUUID();
        var found = TransactionManagementService.findTransaction(client, transactionUuid);

        assertThat(found.getTransaction().getUUID(), is(transactionUuid));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnsupportedAccountType()
    {
        var client = new Client();
        var account = new Account("Cash");
        account.setCurrencyCode(CurrencyUnit.EUR);
        client.addAccount(account);

        var request = new TransactionMutationDto();
        request.setTransactionType("ACCOUNT");
        request.setType("BUY");
        request.setOwnerUuid(account.getUUID());
        request.setDateTime(LocalDateTime.of(2026, 5, 30, 10, 0));
        request.setAmount(100.0);

        TransactionManagementService.createTransaction(client, request);
    }

    @Test
    public void normalizesEmptyNoteToNull()
    {
        var client = new Client();
        var account = new Account("Cash");
        account.setCurrencyCode(CurrencyUnit.EUR);
        client.addAccount(account);

        var request = new TransactionMutationDto();
        request.setTransactionType("ACCOUNT");
        request.setType("REMOVAL");
        request.setOwnerUuid(account.getUUID());
        request.setDateTime(LocalDateTime.of(2026, 5, 30, 10, 0));
        request.setAmount(50.0);
        request.setNote("   ");

        var pair = TransactionManagementService.createTransaction(client, request);
        assertThat(pair.getTransaction().getNote(), nullValue());
    }
}
