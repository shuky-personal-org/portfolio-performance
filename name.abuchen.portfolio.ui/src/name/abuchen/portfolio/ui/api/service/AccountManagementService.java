package name.abuchen.portfolio.ui.api.service;

import java.util.NoSuchElementException;
import java.util.Objects;

import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.ui.api.dto.AccountMutationDto;

public final class AccountManagementService
{
    public static final class AccountDeletionException extends IllegalStateException
    {
        private static final long serialVersionUID = 1L;

        private final int transactionsCount;

        public AccountDeletionException(int transactionsCount)
        {
            super("Only accounts without transactions can be deleted");
            this.transactionsCount = transactionsCount;
        }

        public int getTransactionsCount()
        {
            return transactionsCount;
        }
    }

    private AccountManagementService()
    {
    }

    public static Account createAccount(Client client, AccountMutationDto request)
    {
        Objects.requireNonNull(client, "client");
        requireRequest(request);

        var account = new Account();
        account.setName(requireName(request.getName()));
        account.setCurrencyCode(ServiceUtils.resolveCurrencyCode(request.getCurrencyCode(), client.getBaseCurrency()));
        account.setNote(ServiceUtils.normalizeNote(request.getNote()));
        account.setRetired(Boolean.TRUE.equals(request.getRetired()));

        client.addAccount(account);
        return account;
    }

    public static Account updateAccount(Client client, String accountUuid, AccountMutationDto request)
    {
        Objects.requireNonNull(client, "client");
        requireRequest(request);

        var account = findAccount(client, accountUuid);
        var name = request.getName() != null ? requireName(request.getName()) : null;
        var currencyCode = request.getCurrencyCode() != null
                        ? ServiceUtils.resolveCurrencyCode(request.getCurrencyCode(), account.getCurrencyCode())
                        : null;

        if (currencyCode != null && !account.getCurrencyCode().equals(currencyCode) && !account.getTransactions().isEmpty())
            throw new IllegalArgumentException("Currency can only be changed before transactions are added");

        if (name != null)
            account.setName(name);

        if (currencyCode != null)
            account.setCurrencyCode(currencyCode);

        if (request.getNote() != null)
            account.setNote(ServiceUtils.normalizeNote(request.getNote()));

        if (request.getRetired() != null)
            account.setRetired(request.getRetired().booleanValue());

        return account;
    }

    public static Account deleteAccount(Client client, String accountUuid)
    {
        Objects.requireNonNull(client, "client");

        var account = findAccount(client, accountUuid);
        if (!account.getTransactions().isEmpty())
            throw new AccountDeletionException(account.getTransactions().size());

        client.removeAccount(account);
        return account;
    }

    public static Account findAccount(Client client, String accountUuid)
    {
        if (accountUuid == null || accountUuid.isBlank())
            throw new NoSuchElementException("Account UUID is required");

        return client.getAccounts().stream() //
                        .filter(account -> accountUuid.equals(account.getUUID())) //
                        .findFirst() //
                        .orElseThrow(() -> new NoSuchElementException(
                                        "Account with UUID " + accountUuid + " not found in portfolio"));
    }

    private static void requireRequest(AccountMutationDto request)
    {
        if (request == null)
            throw new IllegalArgumentException("Request body is required");
    }

    private static String requireName(String name)
    {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Account name is required");

        return name.trim();
    }

}
