package name.abuchen.portfolio.ui.api.service;

import java.util.NoSuchElementException;
import java.util.Objects;

import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.ui.api.dto.SecurityAccountMutationDto;

public final class SecurityAccountManagementService
{
    private static final String DEFAULT_REFERENCE_ACCOUNT_NAME = "Reference Account";

    public static final class SecurityAccountDeletionException extends IllegalStateException
    {
        private static final long serialVersionUID = 1L;

        private final int transactionsCount;

        public SecurityAccountDeletionException(int transactionsCount)
        {
            super("Only security accounts without transactions can be deleted");
            this.transactionsCount = transactionsCount;
        }

        public int getTransactionsCount()
        {
            return transactionsCount;
        }
    }

    private SecurityAccountManagementService()
    {
    }

    public static Portfolio createSecurityAccount(Client client, SecurityAccountMutationDto request)
    {
        Objects.requireNonNull(client, "client");
        requireRequest(request);

        var portfolio = new Portfolio();
        portfolio.setName(requireName(request.getName()));
        portfolio.setNote(ServiceUtils.normalizeNote(request.getNote()));
        portfolio.setRetired(Boolean.TRUE.equals(request.getRetired()));
        portfolio.setReferenceAccount(resolveReferenceAccount(client, request.getReferenceAccountUuid()));

        client.addPortfolio(portfolio);
        return portfolio;
    }

    public static Portfolio updateSecurityAccount(Client client, String securityAccountUuid,
                    SecurityAccountMutationDto request)
    {
        Objects.requireNonNull(client, "client");
        requireRequest(request);

        var portfolio = findSecurityAccount(client, securityAccountUuid);
        var name = request.getName() != null ? requireName(request.getName()) : null;
        var referenceAccount = request.getReferenceAccountUuid() != null
                        ? requireReferenceAccount(client, request.getReferenceAccountUuid())
                        : null;

        if (name != null)
            portfolio.setName(name);

        if (request.getNote() != null)
            portfolio.setNote(ServiceUtils.normalizeNote(request.getNote()));

        if (request.getRetired() != null)
            portfolio.setRetired(request.getRetired().booleanValue());

        if (referenceAccount != null)
            portfolio.setReferenceAccount(referenceAccount);

        return portfolio;
    }

    public static Portfolio deleteSecurityAccount(Client client, String securityAccountUuid)
    {
        Objects.requireNonNull(client, "client");

        var portfolio = findSecurityAccount(client, securityAccountUuid);
        if (!portfolio.getTransactions().isEmpty())
            throw new SecurityAccountDeletionException(portfolio.getTransactions().size());

        client.removePortfolio(portfolio);
        return portfolio;
    }

    public static Portfolio findSecurityAccount(Client client, String securityAccountUuid)
    {
        if (securityAccountUuid == null || securityAccountUuid.isBlank())
            throw new NoSuchElementException("Security account UUID is required");

        return client.getPortfolios().stream() //
                        .filter(portfolio -> securityAccountUuid.equals(portfolio.getUUID())) //
                        .findFirst() //
                        .orElseThrow(() -> new NoSuchElementException(
                                        "Security account with UUID " + securityAccountUuid + " not found in portfolio"));
    }

    private static void requireRequest(SecurityAccountMutationDto request)
    {
        if (request == null)
            throw new IllegalArgumentException("Request body is required");
    }

    private static String requireName(String name)
    {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Security account name is required");

        return name.trim();
    }

    private static Account resolveReferenceAccount(Client client, String referenceAccountUuid)
    {
        if (referenceAccountUuid != null && !referenceAccountUuid.isBlank())
            return requireReferenceAccount(client, referenceAccountUuid);

        if (!client.getAccounts().isEmpty())
            return client.getAccounts().get(0);

        var account = new Account();
        account.setName(DEFAULT_REFERENCE_ACCOUNT_NAME);
        account.setCurrencyCode(client.getBaseCurrency());
        client.addAccount(account);
        return account;
    }

    private static Account requireReferenceAccount(Client client, String referenceAccountUuid)
    {
        if (referenceAccountUuid == null || referenceAccountUuid.isBlank())
            throw new NoSuchElementException("Reference account UUID is required");

        return AccountManagementService.findAccount(client, referenceAccountUuid);
    }
}
