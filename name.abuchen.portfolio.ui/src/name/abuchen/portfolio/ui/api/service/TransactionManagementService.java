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

        if (request.getTransactionType() != null && !request.getTransactionType().isBlank())
        {
            var transactionType = requireTransactionType(request.getTransactionType());
            var dateTime = requireDateTime(request.getDateTime());
            var amount = toInternalAmount(requireAmount(request.getAmount()));

            if ("ACCOUNT".equals(transactionType))
                return createAccountTransaction(client, request, dateTime, amount);

            return createOwnerBasedPortfolioTransaction(client, request, dateTime, amount);
        }

        return createShareTransaction(client, request);
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
        {
            if (request.getTransactionType() != null && !request.getTransactionType().isBlank())
                return updatePortfolioTransaction(client, existing, portfolioTransaction, request);

            validateShareTransactionRequest(client, request);
            deleteTransaction(client, transactionUuid);
            return createShareTransaction(client, request);
        }

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
        var currencyCode = ServiceUtils.resolveCurrencyCode(request.getCurrencyCode(), account.getCurrencyCode());

        if (!currencyCode.equals(account.getCurrencyCode()))
            throw new IllegalArgumentException("Account transaction currency must match account currency");

        var transaction = new AccountTransaction(dateTime, currencyCode, amount, security, type);
        applyOptionalFields(transaction, request, supportsShares(type) ? toInternalShares(request.getShares()) : 0L);

        account.addTransaction(transaction);
        return new TransactionPair<>(account, transaction);
    }

    private static TransactionPair<?> createOwnerBasedPortfolioTransaction(Client client,
                    TransactionMutationDto request, LocalDateTime dateTime, long amount)
    {
        var portfolio = SecurityAccountManagementService.findSecurityAccount(client,
                        requireOwnerUuid(request.getOwnerUuid()));
        var type = parsePortfolioType(requireType(request.getType()));
        var security = resolveSecurity(client, request.getSecurityUuid(), true);
        var shares = toInternalShares(requireShares(request.getShares(), type));

        if (type == PortfolioTransaction.Type.BUY || type == PortfolioTransaction.Type.SELL)
        {
            var account = resolveAccount(client, portfolio, request.getAccountUuid());
            var currencyCode = ServiceUtils.resolveCurrencyCode(request.getCurrencyCode(), account.getCurrencyCode());
            var entry = new BuySellEntry(portfolio, account);
            entry.setDate(dateTime);
            entry.setType(type);
            entry.setSecurity(security);
            entry.setShares(shares);
            entry.setAmount(amount);
            entry.setCurrencyCode(currencyCode);
            entry.setNote(ServiceUtils.normalizeNote(request.getNote()));
            entry.setSource(normalizeSource(request.getSource()));
            entry.insert();

            return new TransactionPair<>(portfolio, entry.getPortfolioTransaction());
        }

        var currencyCode = ServiceUtils.resolveCurrencyCode(request.getCurrencyCode(),
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
        transaction.setNote(ServiceUtils.normalizeNote(request.getNote()));
        transaction.setSource(normalizeSource(request.getSource()));

        portfolio.addTransaction(transaction);
        return new TransactionPair<>(portfolio, transaction);
    }

    private static TransactionPair<?> createShareTransaction(Client client, TransactionMutationDto request)
    {
        var type = parseShareType(request.getType());
        var security = SecurityManagementService.findSecurity(client,
                        requireUuid(request.getSecurityUuid(), "Security UUID"));
        var portfolio = findSecurityAccount(client,
                        requireUuid(request.getSecurityAccountUuid(), "Security account UUID"));
        var dateTime = request.getDateTime() != null ? request.getDateTime() : LocalDateTime.now();
        var shares = toInternalShares(requirePositive(request.getShares(), "Shares"));
        var amount = toInternalAmount(requirePositive(request.getAmount(), "Amount"));
        var note = ServiceUtils.normalizeNote(request.getNote());
        var source = normalizeSource(request.getSource());

        if (type == PortfolioTransaction.Type.BUY || type == PortfolioTransaction.Type.SELL)
            return createBuySellTransaction(client, type, security, portfolio, dateTime, shares, amount, note, source,
                            request);

        return createDeliveryTransaction(type, security, portfolio, dateTime, shares, amount, note, source, request);
    }

    private static TransactionPair<?> createBuySellTransaction(Client client, PortfolioTransaction.Type type,
                    Security security, Portfolio portfolio, LocalDateTime dateTime, long shares, long amount,
                    String note, String source, TransactionMutationDto request)
    {
        var account = resolveAccount(client, portfolio, request.getAccountUuid());
        var currencyCode = ServiceUtils.resolveCurrencyCode(request.getCurrencyCode(), account.getCurrencyCode());

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

        var currencyCode = ServiceUtils.resolveCurrencyCode(request.getCurrencyCode(),
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

    private static TransactionPair<?> updateAccountTransaction(Client client, TransactionPair<?> existing,
                    AccountTransaction transaction, TransactionMutationDto request)
    {
        if (transaction.getCrossEntry() != null)
        {
            throw new IllegalArgumentException(
                            "Transfer transactions cannot be updated via API. Delete and recreate instead.");
        }

        var account = (Account) existing.getOwner();

        var dateTime = request.getDateTime();
        var amount = request.getAmount() != null ? toInternalAmount(request.getAmount()) : null;
        String currencyCode = null;
        if (request.getCurrencyCode() != null)
        {
            currencyCode = ServiceUtils.resolveCurrencyCode(request.getCurrencyCode(), account.getCurrencyCode());
            if (!currencyCode.equals(account.getCurrencyCode()))
                throw new IllegalArgumentException("Account transaction currency must match account currency");
        }
        var security = request.getSecurityUuid() != null
                        ? resolveSecurity(client, request.getSecurityUuid(), false)
                        : null;
        var shares = request.getShares() != null ? toInternalShares(request.getShares()) : null;
        var note = request.getNote() != null ? ServiceUtils.normalizeNote(request.getNote()) : null;
        var source = request.getSource() != null ? normalizeSource(request.getSource()) : null;

        if (dateTime != null)
            transaction.setDateTime(dateTime);
        if (amount != null)
            transaction.setAmount(amount);
        if (currencyCode != null)
            transaction.setCurrencyCode(currencyCode);
        if (request.getSecurityUuid() != null)
            transaction.setSecurity(security);
        if (request.getShares() != null)
            transaction.setShares(shares);
        if (request.getNote() != null)
            transaction.setNote(note);
        if (request.getSource() != null)
            transaction.setSource(source);

        return existing;
    }

    private static TransactionPair<?> updatePortfolioTransaction(Client client, TransactionPair<?> existing,
                    PortfolioTransaction transaction, TransactionMutationDto request)
    {
        var portfolio = (Portfolio) existing.getOwner();
        var crossEntry = transaction.getCrossEntry();

        if (crossEntry != null && !(crossEntry instanceof BuySellEntry))
        {
            throw new IllegalArgumentException(
                            "Transfer transactions cannot be updated via API. Delete and recreate instead.");
        }

        if (crossEntry instanceof BuySellEntry buySellEntry)
        {
            var dateTime = request.getDateTime();
            var security = request.getSecurityUuid() != null
                            ? resolveSecurity(client, request.getSecurityUuid(), true)
                            : null;
            var shares = request.getShares() != null ? toInternalShares(request.getShares()) : null;
            var amount = request.getAmount() != null ? toInternalAmount(request.getAmount()) : null;
            String currencyCode = null;
            if (request.getCurrencyCode() != null)
            {
                var account = buySellEntry.getAccount();
                currencyCode = ServiceUtils.resolveCurrencyCode(request.getCurrencyCode(), account.getCurrencyCode());
            }
            var note = request.getNote() != null ? ServiceUtils.normalizeNote(request.getNote()) : null;
            var source = request.getSource() != null ? normalizeSource(request.getSource()) : null;

            if (dateTime != null)
                buySellEntry.setDate(dateTime);
            if (request.getSecurityUuid() != null)
                buySellEntry.setSecurity(security);
            if (request.getShares() != null)
                buySellEntry.setShares(shares);
            if (amount != null)
                buySellEntry.setAmount(amount);
            if (currencyCode != null)
                buySellEntry.setCurrencyCode(currencyCode);
            if (request.getNote() != null)
                buySellEntry.setNote(note);
            if (request.getSource() != null)
                buySellEntry.setSource(source);

            return new TransactionPair<>(portfolio, buySellEntry.getPortfolioTransaction());
        }

        var dateTime = request.getDateTime();
        var security = request.getSecurityUuid() != null
                        ? resolveSecurity(client, request.getSecurityUuid(), true)
                        : null;
        var shares = request.getShares() != null ? toInternalShares(request.getShares()) : null;
        var amount = request.getAmount() != null ? toInternalAmount(request.getAmount()) : null;
        var currencyCode = request.getCurrencyCode() != null
                        ? ServiceUtils.resolveCurrencyCode(request.getCurrencyCode(), transaction.getCurrencyCode())
                        : null;
        var note = request.getNote() != null ? ServiceUtils.normalizeNote(request.getNote()) : null;
        var source = request.getSource() != null ? normalizeSource(request.getSource()) : null;

        if (dateTime != null)
            transaction.setDateTime(dateTime);
        if (request.getSecurityUuid() != null)
            transaction.setSecurity(security);
        if (request.getShares() != null)
            transaction.setShares(shares);
        if (amount != null)
            transaction.setAmount(amount);
        if (currencyCode != null)
            transaction.setCurrencyCode(currencyCode);
        if (request.getNote() != null)
            transaction.setNote(note);
        if (request.getSource() != null)
            transaction.setSource(source);

        return existing;
    }

    private static void validateShareTransactionRequest(Client client, TransactionMutationDto request)
    {
        var type = parseShareType(request.getType());
        SecurityManagementService.findSecurity(client, requireUuid(request.getSecurityUuid(), "Security UUID"));
        var portfolio = findSecurityAccount(client,
                        requireUuid(request.getSecurityAccountUuid(), "Security account UUID"));
        requirePositive(request.getShares(), "Shares");
        requirePositive(request.getAmount(), "Amount");

        if (type == PortfolioTransaction.Type.BUY || type == PortfolioTransaction.Type.SELL)
        {
            var account = resolveAccount(client, portfolio, request.getAccountUuid());
            ServiceUtils.resolveCurrencyCode(request.getCurrencyCode(), account.getCurrencyCode());
        }
        else
        {
            if (portfolio.getReferenceAccount() == null)
                throw new IllegalArgumentException("Security account must have a reference cash account");
            ServiceUtils.resolveCurrencyCode(request.getCurrencyCode(), portfolio.getReferenceAccount().getCurrencyCode());
        }
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
        return client.getPortfolios().stream()
                        .filter(portfolio -> securityAccountUuid.equals(portfolio.getUUID()))
                        .findFirst()
                        .orElseThrow(() -> new NoSuchElementException(
                                        "Security account with UUID " + securityAccountUuid + " not found in portfolio"));
    }

    private static void applyOptionalFields(Transaction transaction, TransactionMutationDto request, long shares)
    {
        transaction.setShares(shares);
        transaction.setNote(ServiceUtils.normalizeNote(request.getNote()));
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
        AccountTransaction.Type parsed;
        try
        {
            parsed = AccountTransaction.Type.valueOf(type.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException e)
        {
            throw new IllegalArgumentException("Unsupported account transaction type: " + type, e);
        }

        if (!ACCOUNT_ONLY_TYPES.contains(parsed))
            throw new IllegalArgumentException("Unsupported account transaction type: " + type);

        return parsed;
    }

    private static PortfolioTransaction.Type parsePortfolioType(String type)
    {
        PortfolioTransaction.Type parsed;
        try
        {
            parsed = PortfolioTransaction.Type.valueOf(type.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException e)
        {
            throw new IllegalArgumentException("Unsupported portfolio transaction type: " + type, e);
        }

        if (!PORTFOLIO_ONLY_TYPES.contains(parsed))
            throw new IllegalArgumentException("Unsupported portfolio transaction type: " + type);

        return parsed;
    }

    private static PortfolioTransaction.Type parseShareType(String type)
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

    private static long toInternalAmount(double value)
    {
        if (!Double.isFinite(value) || value <= 0)
            throw new IllegalArgumentException("Amount must be a positive finite number");

        double internalAmount = value * Values.Amount.divider();
        if (internalAmount > Long.MAX_VALUE || internalAmount < Long.MIN_VALUE)
            throw new IllegalArgumentException("Amount value is outside the supported range");

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

    private static String normalizeSource(String source)
    {
        if (source == null)
            return null;

        var trimmed = source.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
