package name.abuchen.portfolio.ui.api.service;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.datatransfer.Extractor;
import name.abuchen.portfolio.datatransfer.Extractor.Item;
import name.abuchen.portfolio.datatransfer.ImportAction;
import name.abuchen.portfolio.datatransfer.ImportAction.Context;
import name.abuchen.portfolio.datatransfer.ImportAction.Status.Code;
import name.abuchen.portfolio.datatransfer.actions.CheckCurrenciesAction;
import name.abuchen.portfolio.datatransfer.actions.CheckForexGrossValueAction;
import name.abuchen.portfolio.datatransfer.actions.CheckSecurityRelatedValuesAction;
import name.abuchen.portfolio.datatransfer.actions.CheckTransactionDateAction;
import name.abuchen.portfolio.datatransfer.actions.CheckValidTypesAction;
import name.abuchen.portfolio.datatransfer.actions.DetectDuplicatesAction;
import name.abuchen.portfolio.datatransfer.actions.InsertAction;
import name.abuchen.portfolio.datatransfer.ibflex.IBFlexStatementExtractor;
import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.ui.api.dto.ExtractedEntryDto;
import name.abuchen.portfolio.ui.api.dto.FlexImportPreviewResponse;
import name.abuchen.portfolio.ui.api.dto.StatusDto;
import name.abuchen.portfolio.ui.jobs.ConsistencyChecksJob;
import name.abuchen.portfolio.ui.wizards.datatransfer.ExtractedEntry;

/**
 * Service for importing IB Flex reports programmatically.
 * 
 * This service encapsulates the logic from ReviewExtractedItemsPage and ImportController
 * without UI dependencies, allowing automated imports via API.
 */
public class FlexImportService
{
    private static final Logger logger = LoggerFactory.getLogger(FlexImportService.class);

    /**
     * Result of the import operation.
     */
    public static class ImportResult
    {
        private final boolean success;
        private final List<String> errors;
        private final int itemsImported;

        public ImportResult(boolean success, List<String> errors, int itemsImported)
        {
            this.success = success;
            this.errors = errors;
            this.itemsImported = itemsImported;
        }

        public boolean isSuccess()
        {
            return success;
        }

        public List<String> getErrors()
        {
            return errors;
        }

        public int getItemsImported()
        {
            return itemsImported;
        }
    }

    /**
     * Context implementation for import actions that uses the provided account/portfolio mappings.
     */
    private static class ImportContext implements Context
    {
        private final Map<String, Account> primaryAccounts;
        private final Map<String, Account> secondaryAccounts;
        private final Portfolio primaryPortfolio;
        private final Portfolio secondaryPortfolio;

        public ImportContext(Map<String, Account> primaryAccounts, Map<String, Account> secondaryAccounts,
                        Portfolio primaryPortfolio, Portfolio secondaryPortfolio)
        {
            this.primaryAccounts = primaryAccounts;
            this.secondaryAccounts = secondaryAccounts;
            this.primaryPortfolio = primaryPortfolio;
            this.secondaryPortfolio = secondaryPortfolio;
        }

        @Override
        public Account getAccount(String currencyCode)
        {
            Account account = primaryAccounts.get(currencyCode);
            if (account == null)
                logger.info("ImportContext.getAccount: no primary account for currency={}", currencyCode); //$NON-NLS-1$
            return account;
        }

        @Override
        public Portfolio getPortfolio()
        {
            if (primaryPortfolio == null)
                logger.info("ImportContext.getPortfolio: primaryPortfolio is null"); //$NON-NLS-1$
            return primaryPortfolio;
        }

        @Override
        public Account getSecondaryAccount(String currencyCode)
        {
            Account account = secondaryAccounts.get(currencyCode);
            if (account == null)
                logger.info("ImportContext.getSecondaryAccount: no secondary account for currency={}", //$NON-NLS-1$
                                currencyCode);
            return account;
        }

        @Override
        public Portfolio getSecondaryPortfolio()
        {
            if (secondaryPortfolio == null)
                logger.info("ImportContext.getSecondaryPortfolio: secondaryPortfolio is null"); //$NON-NLS-1$
            return secondaryPortfolio;
        }
    }

    /**
     * Imports Flex report from the given file.
     * 
     * @param client The client to import into
     * @param file The Flex report XML file
     * @param currencyAccountMap Map of currency -> primary account UUID
     * @param currencySecondaryAccountMap Map of currency -> secondary account UUID
     * @param portfolioUUID Primary portfolio UUID
     * @param secondaryPortfolioUUID Secondary portfolio UUID (can be null)
     * @param convertBuySellToDelivery Whether to convert BuySell transactions to Delivery
     * @param removeDividends Whether to remove dividends
     * @param importNotes Whether to import notes from source
     * @return ImportResult containing success status, errors, and count of imported items
     */
    public ImportResult importFlexReport(Client client, File file,
                    Map<String, String> currencyAccountMap,
                    Map<String, String> currencySecondaryAccountMap, String portfolioUUID,
                    String secondaryPortfolioUUID, boolean convertBuySellToDelivery,
                    boolean removeDividends, boolean importNotes)
    {
        // Build account mappings
        Map<String, Account> primaryAccounts = new HashMap<>();
        Map<String, Account> secondaryAccounts = new HashMap<>();

        for (Map.Entry<String, String> entry : currencyAccountMap.entrySet())
        {
            Account account = client.getAccounts().stream()
                            .filter(a -> entry.getValue().equals(a.getUUID()))
                            .findFirst()
                            .orElse(null);
            if (account == null)
            {
                return new ImportResult(false,
                                List.of(MessageFormat.format("Account with UUID {0} not found", entry.getValue())),
                                0);
            }
            primaryAccounts.put(entry.getKey(), account);
        }

        for (Map.Entry<String, String> entry : currencySecondaryAccountMap.entrySet())
        {
            Account account = client.getAccounts().stream()
                            .filter(a -> entry.getValue().equals(a.getUUID()))
                            .findFirst()
                            .orElse(null);
            if (account == null)
            {
                return new ImportResult(false,
                                List.of(MessageFormat.format("Secondary account with UUID {0} not found",
                                                entry.getValue())),
                                0);
            }
            secondaryAccounts.put(entry.getKey(), account);
        }

        // Get portfolios
        Portfolio primaryPortfolio = client.getPortfolios().stream()
                        .filter(p -> portfolioUUID.equals(p.getUUID()))
                        .findFirst()
                        .orElse(null);
        if (primaryPortfolio == null)
        {
            return new ImportResult(false,
                            List.of(MessageFormat.format("Portfolio with UUID {0} not found", portfolioUUID)), 0);
        }

        Portfolio secondaryPortfolio = null;
        if (secondaryPortfolioUUID != null && !secondaryPortfolioUUID.isEmpty())
        {
            secondaryPortfolio = client.getPortfolios().stream()
                            .filter(p -> secondaryPortfolioUUID.equals(p.getUUID()))
                            .findFirst()
                            .orElse(null);
            if (secondaryPortfolio == null)
            {
                return new ImportResult(false,
                                List.of(MessageFormat.format("Secondary portfolio with UUID {0} not found",
                                                secondaryPortfolioUUID)),
                                0);
            }
        }

        // Extract items from file
        IBFlexStatementExtractor extractor = new IBFlexStatementExtractor(client);
        List<Exception> extractionErrors = new ArrayList<>();
        List<Extractor.InputFile> inputFiles = List.of(new Extractor.InputFile(file));

        List<Extractor.Item> extractedItems = extractor.extract(inputFiles, extractionErrors);

        // Convert to ExtractedEntry list
        List<ExtractedEntry> entries = extractedItems.stream()
                        .map(ExtractedEntry::new)
                        .collect(Collectors.toList());

        // Setup security dependencies
        setupDependencies(entries);

        // Create context - items will use this automatically when apply() is called
        ImportContext context = new ImportContext(primaryAccounts, secondaryAccounts, primaryPortfolio,
                        secondaryPortfolio);

        // Check entries for errors
        List<String> allErrors = new ArrayList<>();
        checkEntries(entries, context, client, extractionErrors, allErrors);

        // Collect errors from entries that have ERROR status (these won't be imported)
        List<String> skippedErrors = new ArrayList<>(allErrors);
        entries.stream()
                        .filter(e -> e.getMaxCode() == Code.ERROR)
                        .forEach(e -> e.getStatus()
                                        .filter(s -> s.getCode() == Code.ERROR)
                                        .forEach(s -> skippedErrors.add(MessageFormat.format(
                                                        Messages.LabelColonSeparated, s.getMessage(),
                                                        e.getItem().toString()))));

        // Perform import - import entries that don't have errors
        InsertAction action = new InsertAction(client);
        action.setConvertBuySellToDelivery(convertBuySellToDelivery);
        action.setRemoveDividends(removeDividends);

        int itemsImported = 0;
        for (ExtractedEntry entry : entries)
        {
            // Only import entries without errors (OK or WARNING status)
            if (entry.isImported() && entry.getMaxCode() != Code.ERROR)
            {
                if (!importNotes)
                    entry.getItem().setNote(null);

                action.setInvestmentPlanItem(entry.getItem().isInvestmentPlanItem());

                // Apply security override if needed
                if (entry.getSecurityOverride() != null)
                    entry.getItem().setSecurity(entry.getSecurityOverride());

                entry.getItem().apply(action, context);
                itemsImported++;
            }
        }

        if (itemsImported > 0)
        {
            client.markDirty();
            // Run consistency checks
            new ConsistencyChecksJob(client, false).schedule();
        }

        // Return success if any items were imported, with errors for skipped entries
        return new ImportResult(itemsImported > 0, skippedErrors, itemsImported);
    }

    /**
     * Preview Flex report extraction and validation without importing.
     * 
     * @param client The client to validate against
     * @param file The Flex report XML file
     * @param currencyAccountMap Map of currency -> primary account UUID
     * @param currencySecondaryAccountMap Map of currency -> secondary account UUID
     * @param portfolioUUID Primary portfolio UUID
     * @param secondaryPortfolioUUID Secondary portfolio UUID (can be null)
     * @return FlexImportPreviewResponse containing all extracted entries with their status
     */
    public FlexImportPreviewResponse previewFlexReport(Client client, File file,
                    Map<String, String> currencyAccountMap,
                    Map<String, String> currencySecondaryAccountMap, String portfolioUUID,
                    String secondaryPortfolioUUID)
    {
        logger.info("previewFlexReport(file={}, portfolioUUID={}, secondaryPortfolioUUID={}, primaryCurrencyMapSize={}, secondaryCurrencyMapSize={})", //$NON-NLS-1$
                        file != null ? file.getAbsolutePath() : null,
                        portfolioUUID,
                        secondaryPortfolioUUID,
                        currencyAccountMap != null ? currencyAccountMap.size() : 0,
                        currencySecondaryAccountMap != null ? currencySecondaryAccountMap.size() : 0);

        if (client != null)
        {
            logger.info("Client accounts={}, portfolios={}", client.getAccounts().size(), //$NON-NLS-1$
                            client.getPortfolios().size());
            for (Account a : client.getAccounts())
                logger.info("  account: name='{}', uuid={}", a.getName(), a.getUUID()); //$NON-NLS-1$
            for (Portfolio p : client.getPortfolios())
                logger.info("  portfolio: name='{}', uuid={}", p.getName(), p.getUUID()); //$NON-NLS-1$
        }

        // Build account mappings
        Map<String, Account> primaryAccounts = new HashMap<>();
        Map<String, Account> secondaryAccounts = new HashMap<>();

        for (Map.Entry<String, String> entry : currencyAccountMap.entrySet())
        {
            logger.info("Resolving primary account mapping currency={} -> uuid={}", entry.getKey(), entry.getValue()); //$NON-NLS-1$
            Account account = client.getAccounts().stream()
                            .filter(a -> entry.getValue().equals(a.getUUID()))
                            .findFirst()
                            .orElse(null);
            if (account != null)
            {
                primaryAccounts.put(entry.getKey(), account);
                logger.info("  resolved primary currency={} to account name='{}', uuid={}", entry.getKey(), //$NON-NLS-1$
                                account.getName(), account.getUUID());
            }
            else
            {
                logger.info("  FAILED to resolve primary currency={} (uuid={}) to an Account in client", //$NON-NLS-1$
                                entry.getKey(), entry.getValue());
            }
        }

        for (Map.Entry<String, String> entry : currencySecondaryAccountMap.entrySet())
        {
            logger.info("Resolving secondary account mapping currency={} -> uuid={}", entry.getKey(), entry.getValue()); //$NON-NLS-1$
            Account account = client.getAccounts().stream()
                            .filter(a -> entry.getValue().equals(a.getUUID()))
                            .findFirst()
                            .orElse(null);
            if (account != null)
            {
                secondaryAccounts.put(entry.getKey(), account);
                logger.info("  resolved secondary currency={} to account name='{}', uuid={}", entry.getKey(), //$NON-NLS-1$
                                account.getName(), account.getUUID());
            }
            else
            {
                logger.info("  FAILED to resolve secondary currency={} (uuid={}) to an Account in client", //$NON-NLS-1$
                                entry.getKey(), entry.getValue());
            }
        }

        // Get portfolios
        Portfolio primaryPortfolio = client.getPortfolios().stream()
                        .filter(p -> portfolioUUID.equals(p.getUUID()))
                        .findFirst()
                        .orElse(null);
        if (primaryPortfolio != null)
            logger.info("Resolved primary portfolio uuid={} -> '{}'", portfolioUUID, primaryPortfolio.getName()); //$NON-NLS-1$
        else
            logger.info("FAILED to resolve primary portfolio uuid={}", portfolioUUID); //$NON-NLS-1$

        final Portfolio secondaryPortfolio;
        if (secondaryPortfolioUUID != null && !secondaryPortfolioUUID.isEmpty())
        {
            secondaryPortfolio = client.getPortfolios().stream()
                            .filter(p -> secondaryPortfolioUUID.equals(p.getUUID()))
                            .findFirst()
                            .orElse(null);
            if (secondaryPortfolio != null)
                logger.info("Resolved secondary portfolio uuid={} -> '{}'", secondaryPortfolioUUID, //$NON-NLS-1$
                                secondaryPortfolio.getName());
            else
                logger.info("FAILED to resolve secondary portfolio uuid={}", secondaryPortfolioUUID); //$NON-NLS-1$
        }
        else
        {
            secondaryPortfolio = null;
            logger.info("No secondary portfolio configured (secondaryPortfolioUUID empty)"); //$NON-NLS-1$
        }

        // Extract items from file
        IBFlexStatementExtractor extractor = new IBFlexStatementExtractor(client);
        List<Exception> extractionErrors = new ArrayList<>();
        List<Extractor.InputFile> inputFiles = List.of(new Extractor.InputFile(file));

        List<Extractor.Item> extractedItems = extractor.extract(inputFiles, extractionErrors);
        logger.info("Extraction complete: extractedItems={}, extractionErrors={}", extractedItems.size(), //$NON-NLS-1$
                        extractionErrors.size());
        for (Exception e : extractionErrors)
            logger.info("  extractionError: {}", e.getMessage() != null ? e.getMessage() : e.getClass().getName()); //$NON-NLS-1$

        // Convert to ExtractedEntry list
        List<ExtractedEntry> entries = extractedItems.stream()
                        .map(ExtractedEntry::new)
                        .collect(Collectors.toList());

        // Setup security dependencies
        setupDependencies(entries);

        // Create context - items will use this automatically when apply() is called
        ImportContext context = new ImportContext(primaryAccounts, secondaryAccounts, primaryPortfolio,
                        secondaryPortfolio);

        // Check entries for errors
        List<String> allErrors = new ArrayList<>();
        checkEntries(entries, context, client, extractionErrors, allErrors);
        logger.info("Validation complete: entries={}, allErrors={}", entries.size(), allErrors.size()); //$NON-NLS-1$

        // Convert to DTOs
        List<ExtractedEntryDto> entryDtos = entries.stream()
                        .map(e -> convertToDto(e, primaryAccounts, secondaryAccounts, primaryPortfolio,
                                        secondaryPortfolio))
                        .collect(Collectors.toList());

        // Calculate statistics
        int entriesWithErrors = (int) entries.stream()
                        .filter(e -> e.getMaxCode() == Code.ERROR)
                        .count();
        int entriesWithWarnings = (int) entries.stream()
                        .filter(e -> e.getMaxCode() == Code.WARNING)
                        .count();
        int entriesOk = (int) entries.stream()
                        .filter(e -> e.getMaxCode() == Code.OK)
                        .count();

        FlexImportPreviewResponse response = new FlexImportPreviewResponse();
        response.setEntries(entryDtos);
        response.setExtractionErrors(extractionErrors.stream()
                        .map(e -> e.getMessage() != null ? e.getMessage() : e.getClass().getName())
                        .collect(Collectors.toList()));
        response.setTotalEntries(entries.size());
        response.setEntriesWithErrors(entriesWithErrors);
        response.setEntriesWithWarnings(entriesWithWarnings);
        response.setEntriesOk(entriesOk);

        return response;
    }

    /**
     * Convert ExtractedEntry to ExtractedEntryDto.
     */
    private ExtractedEntryDto convertToDto(ExtractedEntry entry, Map<String, Account> primaryAccounts,
                    Map<String, Account> secondaryAccounts, Portfolio primaryPortfolio,
                    Portfolio secondaryPortfolio)
    {
        ExtractedEntryDto dto = new ExtractedEntryDto();
        Item item = entry.getItem();

        dto.setType(item.getTypeInformation());
        dto.setDate(item.getDate());
        dto.setAmount(item.getAmount());
        dto.setShares(item.getShares() != 0 ? item.getShares() : null);
        dto.setSource(item.getSource());

        // Security information
        Security security = item.getSecurity();
        if (security != null)
        {
            dto.setSecurityName(security.getName());
            dto.setSecurityUUID(security.getUUID());
        }

        // Account information
        if (item.getAmount() != null)
        {
            String currency = item.getAmount().getCurrencyCode();
            Account account = primaryAccounts.get(currency);
            if (account == null)
                logger.info("Item missing primary account for currency={} (itemType={}, source={})", //$NON-NLS-1$
                                currency, item.getTypeInformation(), item.getSource());
            if (account != null)
            {
                dto.setAccountPrimaryName(account.getName());
                dto.setAccountPrimaryUUID(account.getUUID());
            }
        }

        Account accountSecondary = item.getAccountSecondary();
        if (accountSecondary == null && item instanceof Extractor.AccountTransferItem)
        {
            Extractor.AccountTransferItem transferItem = (Extractor.AccountTransferItem) item;
            if (transferItem.getSubject() != null)
            {
                name.abuchen.portfolio.model.AccountTransferEntry transfer = (name.abuchen.portfolio.model.AccountTransferEntry) transferItem
                                .getSubject();
                String targetCurrency = transfer.getTargetTransaction().getCurrencyCode();
                accountSecondary = secondaryAccounts.get(targetCurrency);
                if (accountSecondary == null)
                    logger.info("AccountTransferItem missing secondary account for targetCurrency={} (itemType={}, source={})", //$NON-NLS-1$
                                    targetCurrency, item.getTypeInformation(), item.getSource());
            }
        }
        if (accountSecondary != null)
        {
            dto.setAccountSecondaryName(accountSecondary.getName());
            dto.setAccountSecondaryUUID(accountSecondary.getUUID());
        }

        // Portfolio information
        Portfolio portfolio = item.getPortfolioPrimary();
        if (portfolio == null && (item instanceof Extractor.BuySellEntryItem
                        || item instanceof Extractor.PortfolioTransferItem))
        {
            portfolio = primaryPortfolio;
        }
        if (portfolio != null)
        {
            dto.setPortfolioPrimaryName(portfolio.getName());
            dto.setPortfolioPrimaryUUID(portfolio.getUUID());
        }

        Portfolio portfolioSecondary = item.getPortfolioSecondary();
        if (portfolioSecondary == null && item instanceof Extractor.PortfolioTransferItem)
        {
            portfolioSecondary = secondaryPortfolio;
        }
        if (portfolioSecondary != null)
        {
            dto.setPortfolioSecondaryName(portfolioSecondary.getName());
            dto.setPortfolioSecondaryUUID(portfolioSecondary.getUUID());
        }

        // Status information
        dto.setMaxStatus(entry.getMaxCode());
        dto.setStatuses(entry.getStatus()
                        .map(s -> new StatusDto(s.getCode(), s.getMessage()))
                        .collect(Collectors.toList()));
        dto.setWillBeImported(entry.isImported());

        // Note
        if (item.getSubject() instanceof name.abuchen.portfolio.model.Annotated annotated)
        {
            dto.setNote(annotated.getNote());
        }

        return dto;
    }

    /**
     * Setup the securityDependency attribute of the extracted items by collecting all entries
     * which import a new security.
     */
    private void setupDependencies(List<ExtractedEntry> entries)
    {
        var security2entry = entries.stream()
                        .filter(e -> e.getItem() instanceof Extractor.SecurityItem)
                        .collect(Collectors.toMap(e -> e.getItem().getSecurity(), e -> e));

        for (ExtractedEntry entry : entries)
        {
            if (entry.getItem() instanceof Extractor.SecurityItem)
                continue;

            entry.setSecurityDependency(security2entry.get(entry.getItem().getSecurity()));
        }
    }

    /**
     * Check entries for validation errors and warnings.
     */
    private void checkEntries(List<ExtractedEntry> entries, ImportContext context, Client client,
                    List<Exception> extractionErrors, List<String> allErrors)
    {
        List<ImportAction> actions = new ArrayList<>();
        actions.add(new CheckTransactionDateAction());
        actions.add(new CheckValidTypesAction());
        actions.add(new CheckSecurityRelatedValuesAction());
        actions.add(new DetectDuplicatesAction(client));
        actions.add(new CheckCurrenciesAction());
        actions.add(new CheckForexGrossValueAction());

        allErrors.addAll(extractionErrors.stream()
                        .map(e -> e.getMessage() != null ? e.getMessage() : e.getClass().getName())
                        .collect(Collectors.toList()));

        for (ExtractedEntry entry : entries)
        {
            entry.clearStatus();

            if (entry.getItem().isFailure())
            {
                entry.addStatus(new ImportAction.Status(Code.ERROR, entry.getItem().getFailureMessage()));
                allErrors.add(entry.getItem().getFailureMessage() + ": " + entry.getItem().toString());
            }
            else
            {
                for (ImportAction action : actions)
                {
                    try
                    {
                        ImportAction.Status actionStatus = entry.getItem().apply(action, context);
                        entry.addStatus(actionStatus);
                    }
                    catch (Exception e)
                    {
                        entry.addStatus(new ImportAction.Status(Code.ERROR, e.getMessage()));
                        allErrors.add(e.getMessage());
                    }
                }

                entry.getStatus()
                                .filter(s -> s.getCode() == Code.ERROR)
                                .forEach(status -> allErrors.add(MessageFormat.format(Messages.LabelColonSeparated,
                                                status.getMessage(), entry.getItem().toString())));
            }
        }
    }
}

