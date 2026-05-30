package name.abuchen.portfolio.ui.api.service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;

import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.BuySellEntry;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.TransactionPair;
import name.abuchen.portfolio.money.CurrencyUnit;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.ui.api.dto.TransactionMutationDto;

public final class TransactionManagementService
{
    private TransactionManagementService()
    {
    }

    public static TransactionPair<?> createTransaction(Client client, TransactionMutationDto request)
    {
        Objects.requireNonNull(client, "client");
        requireRequest(request);

        var type = parseType(request.getType());
        var security = SecurityManagementService.findSecurity(client, requireUuid(request.getSecurityUuid(), "Security UUID"));
        var portfolio = findSecurityAccount(client, requireUuid(request.getSecurityAccountUuid(), "Security account UUID"));
        var dateTime = request.getDateTime() != null ? request.getDateTime() : LocalDateTime.now();
        var shares = toInternalShares(requirePositive(request.getShares(), "Shares"));
        var amount = toInternalAmount(requirePositive(request.getAmount(), "Amount"));
        var note = normalizeNote(request.getNote());
        var source = normalizeNote(request.getSource());

        if (type == PortfolioTransaction.Type.BUY || type == PortfolioTransaction.Type.SELL)
            return createBuySellTransaction(client, type, security, portfolio, dateTime, shares, amount, note, source,
                            request);

        return createDeliveryTransaction(type, security, portfolio, dateTime, shares, amount, note, source, request);
    }

    public static TransactionPair<?> updateTransaction(Client client, String transactionUuid,
                    TransactionMutationDto request)
    {
        Objects.requireNonNull(client, "client");
        requireRequest(request);

        findTransactionPair(client, transactionUuid);
        validateTransactionRequest(client, request);
        deleteTransaction(client, transactionUuid);
        return createTransaction(client, request);
    }

    private static void validateTransactionRequest(Client client, TransactionMutationDto request)
    {
        var type = parseType(request.getType());
        SecurityManagementService.findSecurity(client, requireUuid(request.getSecurityUuid(), "Security UUID"));
        var portfolio = findSecurityAccount(client, requireUuid(request.getSecurityAccountUuid(), "Security account UUID"));
        requirePositive(request.getShares(), "Shares");
        requirePositive(request.getAmount(), "Amount");

        if (type == PortfolioTransaction.Type.BUY || type == PortfolioTransaction.Type.SELL)
        {
            var account = resolveAccount(client, portfolio, request.getAccountUuid());
            resolveCurrencyCode(request.getCurrencyCode(), account.getCurrencyCode());
        }
        else
        {
            if (portfolio.getReferenceAccount() == null)
                throw new IllegalArgumentException("Security account must have a reference cash account");
            resolveCurrencyCode(request.getCurrencyCode(), portfolio.getReferenceAccount().getCurrencyCode());
        }
    }

    public static TransactionPair<?> deleteTransaction(Client client, String transactionUuid)
    {
        Objects.requireNonNull(client, "client");

        var pair = findTransactionPair(client, transactionUuid);
        pair.deleteTransaction(client);
        return pair;
    }

    private static TransactionPair<?> createBuySellTransaction(Client client, PortfolioTransaction.Type type,
                    Security security, Portfolio portfolio, LocalDateTime dateTime, long shares, long amount,
                    String note, String source, TransactionMutationDto request)
    {
        var account = resolveAccount(client, portfolio, request.getAccountUuid());
        var currencyCode = resolveCurrencyCode(request.getCurrencyCode(), account.getCurrencyCode());

        var entry = new BuySellEntry(portfolio, account);
        entry.setCurrencyCode(currencyCode);
        entry.setDate(dateTime);
        entry.setType(type);
        entry.setSecurity(security);
        entry.setShares(shares);
        entry.setAmount(amount);
        entry.setNote(note);
        entry.setSource(source);
        entry.insert();

        return new TransactionPair<>(portfolio, entry.getPortfolioTransaction());
    }

    private static TransactionPair<?> createDeliveryTransaction(PortfolioTransaction.Type type, Security security,
                    Portfolio portfolio, LocalDateTime dateTime, long shares, long amount, String note, String source,
                    TransactionMutationDto request)
    {
        if (portfolio.getReferenceAccount() == null)
            throw new IllegalArgumentException("Security account must have a reference cash account");

        var currencyCode = resolveCurrencyCode(request.getCurrencyCode(),
                        portfolio.getReferenceAccount().getCurrencyCode());

        var transaction = new PortfolioTransaction();
        transaction.setDateTime(dateTime);
        transaction.setType(type);
        transaction.setSecurity(security);
        transaction.setShares(shares);
        transaction.setAmount(amount);
        transaction.setCurrencyCode(currencyCode);
        transaction.setNote(note);
        transaction.setSource(source);

        portfolio.addTransaction(transaction);
        return new TransactionPair<>(portfolio, transaction);
    }

    private static Account resolveAccount(Client client, Portfolio portfolio, String accountUuid)
    {
        if (accountUuid != null && !accountUuid.isBlank())
            return AccountManagementService.findAccount(client, accountUuid);

        var referenceAccount = portfolio.getReferenceAccount();
        if (referenceAccount == null)
            throw new IllegalArgumentException("Security account must have a reference cash account");

        return referenceAccount;
    }

    private static Portfolio findSecurityAccount(Client client, String securityAccountUuid)
    {
        return client.getPortfolios().stream() //
                        .filter(portfolio -> securityAccountUuid.equals(portfolio.getUUID())) //
                        .findFirst() //
                        .orElseThrow(() -> new NoSuchElementException(
                                        "Security account with UUID " + securityAccountUuid + " not found in portfolio"));
    }

    private static TransactionPair<?> findTransactionPair(Client client, String transactionUuid)
    {
        if (transactionUuid == null || transactionUuid.isBlank())
            throw new NoSuchElementException("Transaction UUID is required");

        return client.getAllTransactions().stream() //
                        .filter(pair -> transactionUuid.equals(pair.getTransaction().getUUID())) //
                        .findFirst() //
                        .orElseThrow(() -> new NoSuchElementException(
                                        "Transaction with UUID " + transactionUuid + " not found in portfolio"));
    }

    private static PortfolioTransaction.Type parseType(String type)
    {
        if (type == null || type.isBlank())
            throw new IllegalArgumentException("Transaction type is required");

        try
        {
            var parsedType = PortfolioTransaction.Type.valueOf(type.trim().toUpperCase(Locale.ROOT));
            if (parsedType == PortfolioTransaction.Type.TRANSFER_IN
                            || parsedType == PortfolioTransaction.Type.TRANSFER_OUT)
            {
                throw new IllegalArgumentException(
                                "Transfer transactions are not supported via this API. Use BUY, SELL, DELIVERY_INBOUND, or DELIVERY_OUTBOUND.");
            }
            return parsedType;
        }
        catch (IllegalArgumentException e)
        {
            if (e.getMessage() != null && e.getMessage().startsWith("Transfer transactions"))
                throw e;
            throw new IllegalArgumentException("Unsupported transaction type: " + type);
        }
    }

    private static void requireRequest(TransactionMutationDto request)
    {
        if (request == null)
            throw new IllegalArgumentException("Request body is required");
    }

    private static String requireUuid(String uuid, String label)
    {
        if (uuid == null || uuid.isBlank())
            throw new IllegalArgumentException(label + " is required");

        return uuid.trim();
    }

    private static double requirePositive(Double value, String label)
    {
        if (value == null || !Double.isFinite(value) || value <= 0)
            throw new IllegalArgumentException(label + " must be a positive number");

        return value.doubleValue();
    }

    private static long toInternalShares(double shares)
    {
        double internalShares = shares * Values.Share.factor();

        if (internalShares > Long.MAX_VALUE || internalShares < Long.MIN_VALUE)
            throw new IllegalArgumentException("Shares value is outside the supported range");

        return Math.round(internalShares);
    }

    private static long toInternalAmount(double amount)
    {
        double internalAmount = amount * Values.Amount.divider();

        if (internalAmount > Long.MAX_VALUE || internalAmount < Long.MIN_VALUE)
            throw new IllegalArgumentException("Amount value is outside the supported range");

        return Math.round(internalAmount);
    }

    private static String normalizeNote(String note)
    {
        if (note == null)
            return null;

        var trimmed = note.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String resolveCurrencyCode(String requestedCurrencyCode, String fallbackCurrencyCode)
    {
        var currencyCode = requestedCurrencyCode == null || requestedCurrencyCode.isBlank()
                        ? fallbackCurrencyCode
                        : requestedCurrencyCode.trim().toUpperCase(Locale.ROOT);

        if (CurrencyUnit.getInstance(currencyCode) == null)
            throw new IllegalArgumentException("Unsupported currency code: " + currencyCode);

        return currencyCode;
    }
}
