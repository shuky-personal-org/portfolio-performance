package name.abuchen.portfolio.ui.api.service;

import java.util.Locale;
import java.util.Objects;

import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.money.CurrencyUnit;

public final class AccountManagementService
{
    private AccountManagementService()
    {
    }

    public static Account createAccount(Client client, String name, String currencyCode, String note)
    {
        validateClient(client);

        var normalizedName = normalizeRequiredName(name);
        var normalizedCurrencyCode = normalizeCurrencyCode(currencyCode, client.getBaseCurrency());

        var account = new Account();
        account.setName(normalizedName);
        account.setCurrencyCode(normalizedCurrencyCode);
        account.setNote(normalizeOptionalText(note));

        client.addAccount(account);
        return account;
    }

    public static boolean updateAccount(Account account, String name, String currencyCode, String note, Boolean retired)
    {
        Objects.requireNonNull(account, "Account must not be null");

        var modified = false;

        if (name != null)
        {
            var normalizedName = normalizeRequiredName(name);
            if (!normalizedName.equals(account.getName()))
            {
                account.setName(normalizedName);
                modified = true;
            }
        }

        if (currencyCode != null)
        {
            var normalizedCurrencyCode = normalizeCurrencyCode(currencyCode, account.getCurrencyCode());
            if (!normalizedCurrencyCode.equals(account.getCurrencyCode()))
            {
                if (!account.getTransactions().isEmpty())
                    throw new IllegalStateException("Account currency cannot be changed while transactions exist");

                account.setCurrencyCode(normalizedCurrencyCode);
                modified = true;
            }
        }

        if (note != null)
        {
            var normalizedNote = normalizeOptionalText(note);
            if (!Objects.equals(normalizedNote, account.getNote()))
            {
                account.setNote(normalizedNote);
                modified = true;
            }
        }

        if (retired != null && retired.booleanValue() != account.isRetired())
        {
            account.setRetired(retired.booleanValue());
            modified = true;
        }

        return modified;
    }

    public static void deleteAccount(Client client, Account account)
    {
        validateClient(client);
        Objects.requireNonNull(account, "Account must not be null");

        if (!account.getTransactions().isEmpty())
            throw new IllegalStateException("Only accounts without transactions can be deleted");

        client.removeAccount(account);
    }

    public static String normalizeRequiredName(String name)
    {
        var normalizedName = normalizeOptionalText(name);
        if (normalizedName == null)
            throw new IllegalArgumentException("Account name is required");

        return normalizedName;
    }

    public static String normalizeCurrencyCode(String currencyCode, String fallbackCurrencyCode)
    {
        var normalizedCurrencyCode = normalizeOptionalText(currencyCode);
        if (normalizedCurrencyCode == null)
            normalizedCurrencyCode = normalizeOptionalText(fallbackCurrencyCode);

        if (normalizedCurrencyCode == null)
            throw new IllegalArgumentException("Currency code is required");

        normalizedCurrencyCode = normalizedCurrencyCode.toUpperCase(Locale.ROOT);

        if (!CurrencyUnit.containsCurrencyCode(normalizedCurrencyCode))
            throw new IllegalArgumentException("Unsupported currency code: " + normalizedCurrencyCode);

        return normalizedCurrencyCode;
    }

    private static String normalizeOptionalText(String value)
    {
        if (value == null)
            return null;

        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void validateClient(Client client)
    {
        Objects.requireNonNull(client, "Client must not be null");
    }
}
