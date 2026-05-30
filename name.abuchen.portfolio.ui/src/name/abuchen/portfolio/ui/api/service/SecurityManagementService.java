package name.abuchen.portfolio.ui.api.service;

import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.money.CurrencyUnit;
import name.abuchen.portfolio.online.QuoteFeed;
import name.abuchen.portfolio.ui.api.dto.SecurityMutationDto;

public final class SecurityManagementService
{
    public static final class SecurityDeletionException extends IllegalStateException
    {
        private static final long serialVersionUID = 1L;

        private final int transactionsCount;

        public SecurityDeletionException(int transactionsCount)
        {
            super("Only securities without transactions can be deleted");
            this.transactionsCount = transactionsCount;
        }

        public int getTransactionsCount()
        {
            return transactionsCount;
        }
    }

    private SecurityManagementService()
    {
    }

    public static Security createSecurity(Client client, SecurityMutationDto request)
    {
        Objects.requireNonNull(client, "client");
        requireRequest(request);

        var security = new Security();
        security.setName(requireName(request.getName()));
        security.setCurrencyCode(resolveCurrencyCode(request.getCurrencyCode(), client.getBaseCurrency()));
        applyOptionalFields(security, request, true);
        security.setFeed(request.getFeed() != null && !request.getFeed().isBlank()
                        ? request.getFeed().trim()
                        : QuoteFeed.MANUAL);

        client.addSecurity(security);
        return security;
    }

    public static Security updateSecurity(Client client, String securityUuid, SecurityMutationDto request)
    {
        Objects.requireNonNull(client, "client");
        requireRequest(request);

        var security = findSecurity(client, securityUuid);
        var name = request.getName() != null ? requireName(request.getName()) : null;
        var currencyCode = request.getCurrencyCode() != null
                        ? resolveCurrencyCode(request.getCurrencyCode(), security.getCurrencyCode())
                        : null;

        if (currencyCode != null && !security.getCurrencyCode().equals(currencyCode)
                        && !security.getTransactions(client).isEmpty())
            throw new IllegalArgumentException("Currency can only be changed before transactions are added");

        if (name != null)
            security.setName(name);

        if (currencyCode != null)
            security.setCurrencyCode(currencyCode);

        applyOptionalFields(security, request, false);

        return security;
    }

    public static Security deleteSecurity(Client client, String securityUuid)
    {
        Objects.requireNonNull(client, "client");

        var security = findSecurity(client, securityUuid);
        if (!security.getTransactions(client).isEmpty())
            throw new SecurityDeletionException(security.getTransactions(client).size());

        client.removeSecurity(security);
        return security;
    }

    public static Security findSecurity(Client client, String securityUuid)
    {
        if (securityUuid == null || securityUuid.isBlank())
            throw new NoSuchElementException("Security UUID is required");

        return client.getSecurities().stream() //
                        .filter(security -> securityUuid.equals(security.getUUID())) //
                        .findFirst() //
                        .orElseThrow(() -> new NoSuchElementException(
                                        "Security with UUID " + securityUuid + " not found in portfolio"));
    }

    private static void applyOptionalFields(Security security, SecurityMutationDto request, boolean isCreate)
    {
        if (request.getTargetCurrencyCode() != null)
            security.setTargetCurrencyCode(normalizeOptionalString(request.getTargetCurrencyCode()));

        if (request.getIsin() != null)
            security.setIsin(normalizeOptionalString(request.getIsin()));

        if (request.getTickerSymbol() != null)
            security.setTickerSymbol(normalizeOptionalString(request.getTickerSymbol()));

        if (request.getWkn() != null)
            security.setWkn(normalizeOptionalString(request.getWkn()));

        if (request.getNote() != null)
            security.setNote(normalizeNote(request.getNote()));

        if (request.getRetired() != null)
            security.setRetired(request.getRetired().booleanValue());

        if (request.getFeed() != null && !request.getFeed().isBlank() && !isCreate)
            security.setFeed(request.getFeed().trim());

        if (request.getFeedURL() != null)
            security.setFeedURL(normalizeOptionalString(request.getFeedURL()));

        if (request.getLatestFeed() != null)
            security.setLatestFeed(normalizeOptionalString(request.getLatestFeed()));

        if (request.getLatestFeedURL() != null)
            security.setLatestFeedURL(normalizeOptionalString(request.getLatestFeedURL()));
    }

    private static void requireRequest(SecurityMutationDto request)
    {
        if (request == null)
            throw new IllegalArgumentException("Request body is required");
    }

    private static String requireName(String name)
    {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Security name is required");

        return name.trim();
    }

    private static String normalizeNote(String note)
    {
        if (note == null)
            return null;

        var trimmed = note.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeOptionalString(String value)
    {
        if (value == null)
            return null;

        var trimmed = value.trim();
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
