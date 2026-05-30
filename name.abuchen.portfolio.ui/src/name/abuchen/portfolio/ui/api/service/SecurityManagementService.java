package name.abuchen.portfolio.ui.api.service;

import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.money.CurrencyUnit;
import name.abuchen.portfolio.online.Factory;
import name.abuchen.portfolio.online.QuoteFeed;
import name.abuchen.portfolio.ui.api.dto.SecurityMutationDto;
import name.abuchen.portfolio.ui.api.dto.SecurityPriceUpdatesDto;

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
                        ? requireValidFeedId(request.getFeed())
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

    public static SecurityPriceUpdatesDto getPriceUpdates(Client client, String securityUuid)
    {
        Objects.requireNonNull(client, "client");

        var security = findSecurity(client, securityUuid);
        return toPriceUpdatesDto(security);
    }

    public static Security updatePriceUpdates(Client client, String securityUuid, SecurityPriceUpdatesDto request)
    {
        Objects.requireNonNull(client, "client");
        requirePriceUpdatesRequest(request);

        var security = findSecurity(client, securityUuid);
        applyPriceUpdates(security, request);
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

    public static SecurityPriceUpdatesDto toPriceUpdatesDto(Security security)
    {
        var dto = new SecurityPriceUpdatesDto();
        dto.setFeed(security.getFeed());
        dto.setFeedURL(security.getFeedURL());
        dto.setLatestFeed(security.getLatestFeed());
        dto.setLatestFeedURL(security.getLatestFeedURL());
        return dto;
    }

    private static void applyPriceUpdates(Security security, SecurityPriceUpdatesDto request)
    {
        var validatedFeed = requireValidFeedId(request.getFeed());
        var normalizedFeedURL = normalizeOptionalString(request.getFeedURL());
        var normalizedLatestFeed = normalizeOptionalString(request.getLatestFeed());
        var validatedLatestFeed = normalizedLatestFeed != null ? requireValidFeedId(normalizedLatestFeed) : null;
        var normalizedLatestFeedURL = normalizedLatestFeed != null
                        ? normalizeOptionalString(request.getLatestFeedURL())
                        : null;

        security.setFeed(validatedFeed);
        security.setFeedURL(normalizedFeedURL);
        security.setLatestFeed(validatedLatestFeed);
        security.setLatestFeedURL(normalizedLatestFeedURL);
    }

    private static void requirePriceUpdatesRequest(SecurityPriceUpdatesDto request)
    {
        if (request == null)
            throw new IllegalArgumentException("Request body is required");

        if (request.getFeed() == null || request.getFeed().isBlank())
            throw new IllegalArgumentException("feed is required");
    }

    private static String requireValidFeedId(String feedId)
    {
        var normalizedFeedId = feedId.trim();
        if (QuoteFeed.MANUAL.equals(normalizedFeedId) || Factory.getQuoteFeedProvider(normalizedFeedId) != null)
            return normalizedFeedId;

        throw new IllegalArgumentException("Unsupported quote feed: " + normalizedFeedId);
    }

    private static void applyOptionalFields(Security security, SecurityMutationDto request, boolean isCreate)
    {
        String validatedFeed = null;
        if (request.getFeed() != null && !request.getFeed().isBlank() && !isCreate)
            validatedFeed = requireValidFeedId(request.getFeed());

        String validatedLatestFeed = null;
        boolean clearLatestFeed = false;
        if (request.getLatestFeed() != null && !request.getLatestFeed().isBlank())
            validatedLatestFeed = requireValidFeedId(request.getLatestFeed());
        else if (request.getLatestFeed() != null)
            clearLatestFeed = true;

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

        if (validatedFeed != null)
            security.setFeed(validatedFeed);

        if (request.getFeedURL() != null)
            security.setFeedURL(normalizeOptionalString(request.getFeedURL()));

        if (validatedLatestFeed != null)
            security.setLatestFeed(validatedLatestFeed);
        else if (clearLatestFeed)
            security.setLatestFeed(null);

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
