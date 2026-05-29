package name.abuchen.portfolio.ui.api.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.money.CurrencyConverter;
import name.abuchen.portfolio.ui.api.dto.AccountMutationDto;
import name.abuchen.portfolio.ui.api.dto.AccountValueUpdateDto;
import name.abuchen.portfolio.ui.api.dto.AccountDto;
import name.abuchen.portfolio.ui.api.dto.TransactionDto;
import name.abuchen.portfolio.money.CurrencyConverterImpl;
import name.abuchen.portfolio.money.ExchangeRateProviderFactory;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.snapshot.AccountSnapshot;
import name.abuchen.portfolio.ui.api.service.AccountManagementService;
import name.abuchen.portfolio.ui.api.service.AccountManagementService.AccountDeletionException;
import name.abuchen.portfolio.ui.api.service.AccountValueAdjustmentService;
import name.abuchen.portfolio.ui.api.dto.ValueDataPointDto;

/**
 * REST Controller for account operations.
 * 
 * This controller provides endpoints to manage accounts within a portfolio.
 */
@Path("/api/v1/portfolios/{portfolioId}/accounts")
public class AccountsController extends BaseController {
    
    /**
     * Get all accounts in a portfolio.
     * 
     * @param portfolioId The portfolio ID
     * @return List of all accounts
     */
    @GET
    @Path("")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllAccounts(@PathParam("portfolioId") String portfolioId) {
        try {
            logger.info("Getting all accounts for portfolio: {}", portfolioId);
            
            // Get the cached Client for this portfolio
            Client client = portfolioFileService.getPortfolio(portfolioId);
            
            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED", 
                    "Portfolio must be opened first before accessing accounts");
            }
            
            // Get all accounts with current values
            List<AccountDto> accounts = client.getAccounts().stream()
                .map(account -> convertAccountToDtoWithValues(account, client))
                .collect(Collectors.toList());
            
            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("portfolioId", portfolioId);
            response.put("count", accounts.size());
            response.put("accounts", accounts);
            
            logger.info("Returning {} accounts for portfolio {}", accounts.size(), portfolioId);
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            logger.error("Unexpected error getting accounts for portfolio {}: {}", 
                portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, 
                "Internal server error", 
                e.getMessage());
        }
    }
    
    /**
     * Get a specific account by UUID.
     * 
     * @param portfolioId The portfolio ID
     * @param accountUuid The account UUID
     * @return Account details
     */
    @GET
    @Path("/{accountUuid}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAccountById(@PathParam("portfolioId") String portfolioId,
                                   @PathParam("accountUuid") String accountUuid) {
        try {
            logger.info("Getting account {} for portfolio {}", accountUuid, portfolioId);
            
            // Get the cached Client for this portfolio
            Client client = portfolioFileService.getPortfolio(portfolioId);
            
            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED", 
                    "Portfolio must be opened first before accessing accounts");
            }
            
            // Find the account by UUID
            Account account = client.getAccounts().stream()
                .filter(a -> accountUuid.equals(a.getUUID()))
                .findFirst()
                .orElse(null);
            
            if (account == null) {
                logger.warn("Account not found: {} in portfolio: {}", accountUuid, portfolioId);
                return createErrorResponse(Response.Status.NOT_FOUND, 
                    "Account not found", 
                    "Account with UUID " + accountUuid + " not found in portfolio");
            }
            
            // Convert to DTO with values
            AccountDto accountDto = convertAccountToDtoWithValues(account, client);
            
            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("portfolioId", portfolioId);
            response.put("account", accountDto);
            
            logger.info("Returning account {} ({}) for portfolio {}", 
                account.getName(), accountUuid, portfolioId);
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            logger.error("Unexpected error getting account {} for portfolio {}: {}", 
                accountUuid, portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, 
                "Internal server error", 
                e.getMessage());
        }
    }
    
    /**
     * Get account value over time.
     * 
     * This endpoint returns the account balance over a specified date range.
     * The values are calculated based on account transactions up to each date.
     * 
     * @param portfolioId The portfolio ID
     * @param accountUuid The account UUID
     * @param startDate Start date for the time series (optional, defaults to 1 year ago)
     * @param endDate End date for the time series (optional, defaults to today)
     * @return Response containing the account's values over time
     */
    @GET
    @Path("/{accountUuid}/values")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAccountValues(@PathParam("portfolioId") String portfolioId,
                                     @PathParam("accountUuid") String accountUuid,
                                     @QueryParam("startDate") String startDate,
                                     @QueryParam("endDate") String endDate) {
        try {
            logger.info("Getting values for account {} in portfolio {}", accountUuid, portfolioId);
            
            // Get the cached Client for this portfolio
            Client client = portfolioFileService.getPortfolio(portfolioId);
            
            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED", 
                    "Portfolio must be opened first before accessing account values");
            }
            
            // Find the account by UUID
            Account account = client.getAccounts().stream()
                .filter(a -> accountUuid.equals(a.getUUID()))
                .findFirst()
                .orElse(null);
            
            if (account == null) {
                logger.warn("Account not found: {} in portfolio: {}", accountUuid, portfolioId);
                return createErrorResponse(Response.Status.NOT_FOUND, 
                    "Account not found", 
                    "Account with UUID " + accountUuid + " not found in portfolio");
            }
            
            // Parse dates with defaults
            LocalDate start = startDate != null && !startDate.isEmpty() 
                ? LocalDate.parse(startDate) 
                : LocalDate.now().minusYears(1);
            LocalDate end = endDate != null && !endDate.isEmpty() 
                ? LocalDate.parse(endDate) 
                : LocalDate.now();
            
            // Validate date range
            if (start.isAfter(end)) {
                return createErrorResponse(Response.Status.BAD_REQUEST, 
                    "Invalid date range", 
                    "Start date must be before or equal to end date");
            }
            
            // Create currency converter
            ExchangeRateProviderFactory factory = new ExchangeRateProviderFactory(client);
            CurrencyConverter converter = new CurrencyConverterImpl(factory, client.getBaseCurrency());
            
            // Calculate values for each date in range
            List<ValueDataPointDto> valuePoints = new ArrayList<>();
            LocalDate currentDate = start;
            
            while (!currentDate.isAfter(end)) {
                AccountSnapshot snapshot = AccountSnapshot.create(account, converter, currentDate);
                Money funds = snapshot.getFunds();
                
                // Convert to double value
                double value = funds.getAmount() / Values.Amount.divider();
                
                valuePoints.add(new ValueDataPointDto(currentDate, value));
                currentDate = currentDate.plusDays(1);
            }
            
            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("portfolioId", portfolioId);
            response.put("accountUuid", accountUuid);
            response.put("accountName", account.getName());
            response.put("currencyCode", account.getCurrencyCode());
            response.put("startDate", start);
            response.put("endDate", end);
            response.put("dataPointsCount", valuePoints.size());
            response.put("values", valuePoints);
            response.put("timezone", ZoneId.systemDefault().getId());
            
            logger.info("Returning {} value data points for account {} ({})", 
                valuePoints.size(), account.getName(), accountUuid);
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            logger.error("Unexpected error getting values for account {} in portfolio {}: {}", 
                accountUuid, portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, 
                "Internal server error", 
                e.getMessage());
        }
    }

    /**
     * Set the account to a target value by creating a balancing deposit or
     * withdrawal transaction.
     *
     * @param portfolioId The portfolio ID
     * @param accountUuid The account UUID
     * @param valueUpdate Target value data
     * @return Response containing the created adjustment transaction, if any
     */
    @PUT
    @Path("/{accountUuid}/value")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response setAccountValue(@PathParam("portfolioId") String portfolioId,
                                    @PathParam("accountUuid") String accountUuid,
                                    AccountValueUpdateDto valueUpdate) {
        try {
            logger.info("Setting value for account {} in portfolio {}", accountUuid, portfolioId);

            if (valueUpdate == null || valueUpdate.getValue() == null) {
                return createErrorResponse(Response.Status.BAD_REQUEST,
                    "Missing value",
                    "Request body must contain a numeric value");
            }

            double requestedValue = valueUpdate.getValue().doubleValue();
            if (!Double.isFinite(requestedValue)) {
                return createErrorResponse(Response.Status.BAD_REQUEST,
                    "Invalid value",
                    "Account value must be a finite number");
            }

            long targetAmount = toInternalAmount(requestedValue);
            LocalDateTime dateTime = valueUpdate.getDateTime() != null
                ? valueUpdate.getDateTime()
                : LocalDateTime.now();

            Client client = portfolioFileService.getPortfolio(portfolioId);

            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED",
                    "Portfolio must be opened first before setting account values");
            }

            Account account = client.getAccounts().stream()
                .filter(a -> accountUuid.equals(a.getUUID()))
                .findFirst()
                .orElse(null);

            if (account == null) {
                logger.warn("Account not found: {} in portfolio: {}", accountUuid, portfolioId);
                return createErrorResponse(Response.Status.NOT_FOUND,
                    "Account not found",
                    "Account with UUID " + accountUuid + " not found in portfolio");
            }

            var adjustment = AccountValueAdjustmentService.setValue(
                account,
                dateTime,
                targetAmount,
                valueUpdate.getNote(),
                valueUpdate.getSource());

            if (adjustment.isModified()) {
                client.markDirty();
                portfolioFileService.saveFile(portfolioId);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("portfolioId", portfolioId);
            response.put("accountUuid", accountUuid);
            response.put("accountName", account.getName());
            response.put("currencyCode", account.getCurrencyCode());
            response.put("previousValue", adjustment.previousAmount() / Values.Amount.divider());
            response.put("newValue", adjustment.targetAmount() / Values.Amount.divider());
            response.put("adjustmentAmount", adjustment.deltaAmount() / Values.Amount.divider());
            response.put("wasModified", adjustment.isModified());

            if (adjustment.transaction() != null)
                response.put("transaction", convertAccountTransactionToDto(adjustment.transaction(), account));

            logger.info("Set account {} ({}) value from {} to {} in portfolio {}",
                account.getName(), accountUuid, adjustment.previousAmount(), adjustment.targetAmount(), portfolioId);

            return Response.ok(response).build();

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid account value update for account {} in portfolio {}: {}",
                accountUuid, portfolioId, e.getMessage());
            return createErrorResponse(Response.Status.BAD_REQUEST,
                "Invalid value",
                e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error setting value for account {} in portfolio {}: {}",
                accountUuid, portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                "Internal server error",
                e.getMessage());
        }
    }
    
    /**
     * Create a new account.
     * 
     * @param portfolioId The portfolio ID
     * @param accountData Account data
     * @return Created account
     */
    @POST
    @Path("")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createAccount(@PathParam("portfolioId") String portfolioId,
                                  AccountMutationDto accountData) {
        try {
            logger.info("Creating account for portfolio {}", portfolioId);

            Client client = portfolioFileService.getPortfolio(portfolioId);

            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED",
                    "Portfolio must be opened first before creating accounts");
            }

            Account account = AccountManagementService.createAccount(client, accountData);
            client.markDirty();
            portfolioFileService.saveFile(portfolioId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("portfolioId", portfolioId);
            response.put("account", convertAccountToDtoWithValues(account, client));
            response.put("message", "Account created successfully");

            logger.info("Created account {} ({}) for portfolio {}", account.getName(), account.getUUID(), portfolioId);

            return Response.status(Response.Status.CREATED).entity(response).build();

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid account creation request for portfolio {}: {}", portfolioId, e.getMessage());
            return createErrorResponse(Response.Status.BAD_REQUEST,
                "Invalid request",
                e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error creating account for portfolio {}: {}",
                portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                "Internal server error",
                e.getMessage());
        }
    }
    
    /**
     * Update an existing account.
     * 
     * @param portfolioId The portfolio ID
     * @param accountUuid The account UUID
     * @param accountData Updated account data
     * @return Updated account
     */
    @PUT
    @Path("/{accountUuid}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateAccount(@PathParam("portfolioId") String portfolioId,
                                  @PathParam("accountUuid") String accountUuid,
                                  AccountMutationDto accountData) {
        try {
            logger.info("Updating account {} for portfolio {}", accountUuid, portfolioId);

            Client client = portfolioFileService.getPortfolio(portfolioId);

            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED",
                    "Portfolio must be opened first before updating accounts");
            }

            Account account = AccountManagementService.updateAccount(client, accountUuid, accountData);
            client.markDirty();
            portfolioFileService.saveFile(portfolioId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("portfolioId", portfolioId);
            response.put("account", convertAccountToDtoWithValues(account, client));
            response.put("message", "Account updated successfully");

            logger.info("Updated account {} ({}) for portfolio {}", account.getName(), accountUuid, portfolioId);

            return Response.ok(response).build();

        } catch (NoSuchElementException e) {
            logger.warn("Account not found: {} in portfolio: {}", accountUuid, portfolioId);
            return createErrorResponse(Response.Status.NOT_FOUND,
                "Account not found",
                e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid account update request for account {} in portfolio {}: {}",
                accountUuid, portfolioId, e.getMessage());
            return createErrorResponse(Response.Status.BAD_REQUEST,
                "Invalid request",
                e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error updating account {} for portfolio {}: {}",
                accountUuid, portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                "Internal server error",
                e.getMessage());
        }
    }
    
    /**
     * Delete an account.
     * 
     * @param portfolioId The portfolio ID
     * @param accountUuid The account UUID
     * @return Deletion confirmation
     */
    @DELETE
    @Path("/{accountUuid}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteAccount(@PathParam("portfolioId") String portfolioId,
                                  @PathParam("accountUuid") String accountUuid) {
        try {
            logger.info("Deleting account {} for portfolio {}", accountUuid, portfolioId);

            Client client = portfolioFileService.getPortfolio(portfolioId);

            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED",
                    "Portfolio must be opened first before deleting accounts");
            }

            Account account = AccountManagementService.deleteAccount(client, accountUuid);
            client.markDirty();
            portfolioFileService.saveFile(portfolioId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("portfolioId", portfolioId);
            response.put("accountUuid", accountUuid);
            response.put("accountName", account.getName());
            response.put("message", "Account deleted successfully");

            logger.info("Deleted account {} ({}) for portfolio {}", account.getName(), accountUuid, portfolioId);

            return Response.ok(response).build();

        } catch (NoSuchElementException e) {
            logger.warn("Account not found: {} in portfolio: {}", accountUuid, portfolioId);
            return createErrorResponse(Response.Status.NOT_FOUND,
                "Account not found",
                e.getMessage());
        } catch (AccountDeletionException e) {
            logger.warn("Cannot delete account {} in portfolio {}: {} transaction(s)",
                accountUuid, portfolioId, e.getTransactionsCount());
            return createErrorResponse(Response.Status.CONFLICT,
                "Account has transactions",
                e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error deleting account {} for portfolio {}: {}",
                accountUuid, portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                "Internal server error",
                e.getMessage());
        }
    }
    
    /**
     * Helper method to convert Account to AccountDto.
     * Aligned with PortfolioFileService.loadAccounts()
     */
    private AccountDto convertAccountToDto(Account account) {
        AccountDto dto = new AccountDto();
        dto.setUuid(account.getUUID());
        dto.setName(account.getName());
        dto.setCurrencyCode(account.getCurrencyCode());
        dto.setNote(account.getNote());
        dto.setRetired(account.isRetired());
        dto.setTransactionsCount(account.getTransactions().size());
        dto.setUpdatedAt(account.getUpdatedAt());
        
        return dto;
    }
    
    /**
     * Helper method to convert Account to AccountDto with current values.
     * This version includes current value calculations.
     */
    private AccountDto convertAccountToDtoWithValues(Account account, Client client) {
        AccountDto dto = convertAccountToDto(account);
        
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        
        // Create currency converter for account valuations in base currency
        ExchangeRateProviderFactory factory = new ExchangeRateProviderFactory(client);
        CurrencyConverter converter = new CurrencyConverterImpl(factory, client.getBaseCurrency());
        
        // Calculate current value
        try {
            long currentAmount = account.getCurrentAmount(now);
            // Convert from internal representation (multiplied by 100) to decimal
            dto.setCurrentValue(currentAmount / 100.0);
            
            // Convert to base currency
            Money accountMoney = Money.of(account.getCurrencyCode(), currentAmount);
            Money convertedMoney = accountMoney.with(converter.at(java.time.LocalDate.now()));
            dto.setCurrentValueInBaseCurrency(convertedMoney.getAmount() / 100.0);
        } catch (Exception e) {
            logger.warn("Failed to calculate account balance for {}: {}", account.getName(), e.getMessage());
            dto.setCurrentValue(0.0);
            dto.setCurrentValueInBaseCurrency(0.0);
        }
        
        return dto;
    }

    private long toInternalAmount(double value) {
        double internalAmount = value * Values.Amount.divider();

        if (internalAmount > Long.MAX_VALUE || internalAmount < Long.MIN_VALUE)
            throw new IllegalArgumentException("Account value is outside the supported range");

        return Math.round(internalAmount);
    }

    private TransactionDto convertAccountTransactionToDto(AccountTransaction transaction, Account account) {
        TransactionDto dto = new TransactionDto();

        dto.setUuid(transaction.getUUID());
        dto.setDateTime(transaction.getDateTime());
        dto.setType(transaction.getType().name());
        dto.setTransactionType("ACCOUNT");
        dto.setCurrencyCode(transaction.getCurrencyCode());
        dto.setAmount(transaction.getAmount() / Values.Amount.divider());
        dto.setNote(transaction.getNote());
        dto.setSource(transaction.getSource());
        dto.setOwnerUuid(account.getUUID());
        dto.setOwnerName(account.getName());
        dto.setUpdatedAt(transaction.getUpdatedAt());

        return dto;
    }
}

