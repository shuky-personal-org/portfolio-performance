package name.abuchen.portfolio.ui.api.service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.model.BuySellEntry;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.Transaction;
import name.abuchen.portfolio.model.TransactionPair;
import name.abuchen.portfolio.money.CurrencyUnit;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.ui.api.dto.TransactionMutationDto;

public final class TransactionManagementService
{
    private static final Set<AccountTransaction.Type> ACCOUNT_ONLY_TYPES = Set.of(
                    AccountTransaction.Type.DEPOSIT,
                    AccountTransaction.Type.REMOVAL,
                    AccountTransaction.Type.INTEREST,
                    AccountTransaction.Type.INTEREST_CHARGE,
                    AccountTransaction.Type.DIVIDENDS,
                    AccountTransaction.Type.FEES,
                    AccountTransaction.Type.FEES_REFUND,
                    AccountTransaction.Type.TAXES,
                    AccountTransaction.Type.TAX_REFUND);

    private static final Set<PortfolioTransaction.Type> PORTFOLIO_ONLY_TYPES = Set.of(
                    PortfolioTransaction.Type.BUY,
                    PortfolioTransaction.Type.SELL,
                    PortfolioTransaction.Type.DELIVERY_INBOUND,
                    PortfolioTransaction.Type.DELIVERY_OUTBOUND);

    private TransactionManagementService()
    {
    }

    public static TransactionPair<?> createTransaction(Client client, TransactionMutationDto request)
    {
        Objects.requireNonNull(client, "client");
        requireRequest(request);

        var transactionType = requireTransactionType(request.getTransactionType());
        var dateTime = requireDateTime(request.getDateTime());
        var amount = toInternalAmount(requireAmount(request.getAmount()));

        if ("ACCOUNT".equals(transactionType))
            return createAccountTransaction(client, request, dateTime, amount);

        return createPortfolioTransaction(client, request, dateTime, amount);
    }

    public static TransactionPair<?> updateTransaction(Client client, String transactionUuid,
                    TransactionMutationDto request)
    {
        Objects.requireNonNull(client, "client");
        requireRequest(request);

        var existing = findTransaction(client, transactionUuid);
        var transaction = existing.getTransaction();

        if (transaction instanceof AccountTransaction accountTransaction)
            return updateAccountTransaction(client, existing, accountTransaction, request);

        if (transaction instanceof PortfolioTransaction portfolioTransaction)
            return updatePortfolioTransaction(client, existing, portfolioTransaction, request);

        throw new IllegalStateException("Unsupported transaction type");
    }

    public static TransactionPair<?> deleteTransaction(Client client, String transactionUuid)
    {
        Objects.requireNonNull(client, "client");

        var existing = findTransaction(client, transactionUuid);
        existing.deleteTransaction(client);
        return existing;
    }

    public static TransactionPair<?> findTransaction(Client client, String transactionUuid)
    {
        if (transactionUuid == null || transactionUuid.isBlank())
            throw new NoSuchElementException("Transaction UUID is required");

        return client.getAllTransactions().stream()
                        .filter(pair -> transactionUuid.equals(pair.getTransaction().getUUID()))
                        .findFirst()
                        .orElseThrow(() -> new NoSuchElementException(
                                        "Transaction with UUID " + transactionUuid + " not found in portfolio"));
    }

    private static TransactionPair<?> createAccountTransaction(Client client, TransactionMutationDto request,
                    LocalDateTime dateTime, long amount)
    {
        var account = AccountManagementService.findAccount(client, requireOwnerUuid(request.getOwnerUuid()));
        var type = parseAccountType(requireType(request.getType()));
        var security = resolveSecurity(client, request.getSecurityUuid(), requiresSecurity(type));
        var currencyCode = resolveCurrencyCode(request.getCurrencyCode(), account.getCurrencyCode());

        if (!currencyCode.equals(account.getCurrencyCode()))
            throw new IllegalArgumentException("Account transaction currency must match account currency");

        var transaction = new AccountTransaction(dateTime, currencyCode, amount, security, type);
        applyOptionalFields(transaction, request, supportsShares(type) ? toInternalShares(request.getShares()) : 0L);

        account.addTransaction(transaction);
        return new TransactionPair<>(account, transaction);
    }

    private static TransactionPair<?> createPortfolioTransaction(Client client, TransactionMutationDto request,
                    LocalDateTime dateTime, long amount)
    {
        var portfolio = SecurityAccountManagementService.findSecurityAccount(client,
                        requireOwnerUuid(request.getOwnerUuid()));
        var type = parsePortfolioType(requireType(request.getType()));
        var security = resolveSecurity(client, request.getSecurityUuid(), true);
        var shares = toInternalShares(requireShares(request.getShares(), type));

        if (type == PortfolioTransaction.Type.BUY || type == PortfolioTransaction.Type.SELL)
        {
            var account = portfolio.getReferenceAccount();
            if (account == null)
                throw new IllegalArgumentException("Security account must have a reference account for buy/sell transactions");

            var currencyCode = resolveCurrencyCode(request.getCurrencyCode(), account.getCurrencyCode());
            var entry = new BuySellEntry(portfolio, account);
            entry.setDate(dateTime);
            entry.setType(type);
            entry.setSecurity(security);
            entry.setShares(shares);
            entry.setAmount(amount);
            entry.setCurrencyCode(currencyCode);
            entry.setNote(normalizeNote(request.getNote()));
            entry.setSource(normalizeSource(request.getSource()));
            entry.insert();

            return new TransactionPair<>(portfolio, entry.getPortfolioTransaction());
        }

        var currencyCode = resolveCurrencyCode(request.getCurrencyCode(),
                        portfolio.getReferenceAccount() != null
                                        ? portfolio.getReferenceAccount().getCurrencyCode()
                                        : client.getBaseCurrency());

        var transaction = new PortfolioTransaction();
        transaction.setDateTime(dateTime);
        transaction.setType(type);
        transaction.setSecurity(security);
        transaction.setShares(shares);
        transaction.setAmount(amount);
        transaction.setCurrencyCode(currencyCode);
        transaction.setNote(normalizeNote(request.getNote()));
        transaction.setSource(normalizeSource(request.getSource()));

        portfolio.addTransaction(transaction);
        return new TransactionPair<>(portfolio, transaction);
    }

    private static TransactionPair<?> updateAccountTransaction(Client client, TransactionPair<?> existing,
                    AccountTransaction transaction, TransactionMutationDto request)
    {
        var account = (Account) existing.getOwner();

        if (request.getDateTime() != null)
            transaction.setDateTime(request.getDateTime());

        if (request.getAmount() != null)
            transaction.setAmount(toInternalAmount(request.getAmount()));

        if (request.getCurrencyCode() != null)
        {
            var currencyCode = resolveCurrencyCode(request.getCurrencyCode(), account.getCurrencyCode());
            if (!currencyCode.equals(account.getCurrencyCode()))
                throw new IllegalArgumentException("Account transaction currency must match account currency");
            transaction.setCurrencyCode(currencyCode);
        }

        if (request.getSecurityUuid() != null)
            transaction.setSecurity(resolveSecurity(client, request.getSecurityUuid(), false));

        if (request.getShares() != null)
            transaction.setShares(toInternalShares(request.getShares()));

        if (request.getNote() != null)
            transaction.setNote(normalizeNote(request.getNote()));

        if (request.getSource() != null)
            transaction.setSource(normalizeSource(request.getSource()));

        return existing;
    }

    private static TransactionPair<?> updatePortfolioTransaction(Client client, TransactionPair<?> existing,
                    PortfolioTransaction transaction, TransactionMutationDto request)
    {
        var portfolio = (Portfolio) existing.getOwner();
        var crossEntry = transaction.getCrossEntry();

        if (crossEntry instanceof BuySellEntry buySellEntry)
        {
            if (request.getDateTime() != null)
                buySellEntry.setDate(request.getDateTime());

            if (request.getSecurityUuid() != null)
                buySellEntry.setSecurity(resolveSecurity(client, request.getSecurityUuid(), true));

            if (request.getShares() != null)
                buySellEntry.setShares(toInternalShares(request.getShares()));

            if (request.getAmount() != null)
                buySellEntry.setAmount(toInternalAmount(request.getAmount()));

            if (request.getCurrencyCode() != null)
            {
                var account = buySellEntry.getAccount();
                var currencyCode = resolveCurrencyCode(request.getCurrencyCode(), account.getCurrencyCode());
                buySellEntry.setCurrencyCode(currencyCode);
            }

            if (request.getNote() != null)
                buySellEntry.setNote(normalizeNote(request.getNote()));

            if (request.getSource() != null)
                buySellEntry.setSource(normalizeSource(request.getSource()));

            return new TransactionPair<>(portfolio, buySellEntry.getPortfolioTransaction());
        }

        if (request.getDateTime() != null)
            transaction.setDateTime(request.getDateTime());

        if (request.getSecurityUuid() != null)
            transaction.setSecurity(resolveSecurity(client, request.getSecurityUuid(), true));

        if (request.getShares() != null)
            transaction.setShares(toInternalShares(request.getShares()));

        if (request.getAmount() != null)
            transaction.setAmount(toInternalAmount(request.getAmount()));

        if (request.getCurrencyCode() != null)
            transaction.setCurrencyCode(resolveCurrencyCode(request.getCurrencyCode(), transaction.getCurrencyCode()));

        if (request.getNote() != null)
            transaction.setNote(normalizeNote(request.getNote()));

        if (request.getSource() != null)
            transaction.setSource(normalizeSource(request.getSource()));

        return existing;
    }

    private static void applyOptionalFields(Transaction transaction, TransactionMutationDto request, long shares)
    {
        transaction.setShares(shares);
        transaction.setNote(normalizeNote(request.getNote()));
        transaction.setSource(normalizeSource(request.getSource()));
    }

    private static boolean requiresSecurity(AccountTransaction.Type type)
    {
        return type == AccountTransaction.Type.DIVIDENDS;
    }

    private static boolean supportsShares(AccountTransaction.Type type)
    {
        return type == AccountTransaction.Type.DIVIDENDS;
    }

    private static boolean requiresShares(PortfolioTransaction.Type type)
    {
        return type == PortfolioTransaction.Type.BUY
                        || type == PortfolioTransaction.Type.SELL
                        || type == PortfolioTransaction.Type.DELIVERY_INBOUND
                        || type == PortfolioTransaction.Type.DELIVERY_OUTBOUND;
    }

    private static Double requireShares(Double shares, PortfolioTransaction.Type type)
    {
        if (!requiresShares(type))
            return shares;

        if (shares == null || shares <= 0)
            throw new IllegalArgumentException("Shares are required for portfolio security transactions");

        return shares;
    }

    private static AccountTransaction.Type parseAccountType(String type)
    {
        try
        {
            var parsed = AccountTransaction.Type.valueOf(type.trim().toUpperCase(Locale.ROOT));
            if (!ACCOUNT_ONLY_TYPES.contains(parsed))
                throw new IllegalArgumentException("Unsupported account transaction type: " + type);
            return parsed;
        }
        catch (IllegalArgumentException e)
        {
            throw new IllegalArgumentException("Unsupported account transaction type: " + type, e);
        }
    }

    private static PortfolioTransaction.Type parsePortfolioType(String type)
    {
        try
        {
            var parsed = PortfolioTransaction.Type.valueOf(type.trim().toUpperCase(Locale.ROOT));
            if (!PORTFOLIO_ONLY_TYPES.contains(parsed))
                throw new IllegalArgumentException("Unsupported portfolio transaction type: " + type);
            return parsed;
        }
        catch (IllegalArgumentException e)
        {
            throw new IllegalArgumentException("Unsupported portfolio transaction type: " + type, e);
        }
    }

    private static Security resolveSecurity(Client client, String securityUuid, boolean required)
    {
        if (securityUuid == null || securityUuid.isBlank())
        {
            if (required)
                throw new IllegalArgumentException("Security UUID is required for this transaction type");
            return null;
        }

        return client.getSecurities().stream()
                        .filter(security -> securityUuid.equals(security.getUUID()))
                        .findFirst()
                        .orElseThrow(() -> new NoSuchElementException(
                                        "Security with UUID " + securityUuid + " not found in portfolio"));
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

    private static long toInternalAmount(double value)
    {
        if (!Double.isFinite(value) || value <= 0)
            throw new IllegalArgumentException("Amount must be a positive finite number");

        double internalAmount = value * Values.Amount.divider();
        if (internalAmount > Long.MAX_VALUE)
            throw new IllegalArgumentException("Amount is outside the supported range");

        return Math.round(internalAmount);
    }

    private static long toInternalShares(Double shares)
    {
        if (shares == null)
            return 0L;

        if (!Double.isFinite(shares) || shares < 0)
            throw new IllegalArgumentException("Shares must be a non-negative finite number");

        double internalShares = shares * Values.Share.factor();
        if (internalShares > Long.MAX_VALUE)
            throw new IllegalArgumentException("Shares are outside the supported range");

        return Math.round(internalShares);
    }

    private static String requireTransactionType(String transactionType)
    {
        if (transactionType == null || transactionType.isBlank())
            throw new IllegalArgumentException("transactionType is required");

        var normalized = transactionType.trim().toUpperCase(Locale.ROOT);
        if (!"ACCOUNT".equals(normalized) && !"PORTFOLIO".equals(normalized))
            throw new IllegalArgumentException("transactionType must be ACCOUNT or PORTFOLIO");

        return normalized;
    }

    private static String requireType(String type)
    {
        if (type == null || type.isBlank())
            throw new IllegalArgumentException("type is required");

        return type.trim();
    }

    private static String requireOwnerUuid(String ownerUuid)
    {
        if (ownerUuid == null || ownerUuid.isBlank())
            throw new IllegalArgumentException("ownerUuid is required");

        return ownerUuid.trim();
    }

    private static LocalDateTime requireDateTime(LocalDateTime dateTime)
    {
        if (dateTime == null)
            throw new IllegalArgumentException("dateTime is required");

        return dateTime;
    }

    private static double requireAmount(Double amount)
    {
        if (amount == null)
            throw new IllegalArgumentException("amount is required");

        return amount.doubleValue();
    }

    private static void requireRequest(TransactionMutationDto request)
    {
        if (request == null)
            throw new IllegalArgumentException("Request body is required");
    }

    private static String normalizeNote(String note)
    {
        if (note == null)
            return null;

        var trimmed = note.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeSource(String source)
    {
        if (source == null)
            return null;

        var trimmed = source.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
