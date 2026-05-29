package name.abuchen.portfolio.ui.api.controller;

import java.time.LocalDate;
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

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.money.CurrencyConverter;
import name.abuchen.portfolio.ui.api.dto.PortfolioDto;
import name.abuchen.portfolio.money.CurrencyConverterImpl;
import name.abuchen.portfolio.money.ExchangeRateProviderFactory;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.snapshot.PortfolioSnapshot;
import name.abuchen.portfolio.ui.api.dto.ValueDataPointDto;
import name.abuchen.portfolio.ui.api.dto.SecurityAccountMutationDto;
import name.abuchen.portfolio.ui.api.service.SecurityAccountManagementService;
import name.abuchen.portfolio.ui.api.service.SecurityAccountManagementService.SecurityAccountDeletionException;

/**
 * REST Controller for security account (portfolio) operations.
 * 
 * This controller provides endpoints to manage security accounts (portfolios) within a portfolio file.
 * Note: In Portfolio Performance terminology, a "Portfolio" is a securities account that holds stocks/securities.
 */
@Path("/api/v1/portfolios/{portfolioId}/securityaccounts")
public class SecurityAccountsController extends BaseController {
    
    /**
     * Get all security accounts in a portfolio.
     * 
     * @param portfolioId The portfolio ID (file)
     * @return List of all security accounts
     */
    @GET
    @Path("")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllSecurityAccounts(@PathParam("portfolioId") String portfolioId) {
        try {
            logger.info("Getting all security accounts for portfolio: {}", portfolioId);
            
            // Get the cached Client for this portfolio
            Client client = portfolioFileService.getPortfolio(portfolioId);
            
            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED", 
                    "Portfolio must be opened first before accessing security accounts");
            }
            
            // Get all portfolios (security accounts) with current values
            List<PortfolioDto> portfolios = client.getPortfolios().stream()
                .map(portfolio -> convertPortfolioToDtoWithValue(portfolio, client))
                .collect(Collectors.toList());
            
            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("portfolioId", portfolioId);
            response.put("count", portfolios.size());
            response.put("securityAccounts", portfolios);
            
            logger.info("Returning {} security accounts for portfolio {}", portfolios.size(), portfolioId);
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            logger.error("Unexpected error getting security accounts for portfolio {}: {}", 
                portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, 
                "Internal server error", 
                e.getMessage());
        }
    }
    
    /**
     * Get a specific security account by UUID.
     * 
     * @param portfolioId The portfolio ID (file)
     * @param securityAccountUuid The security account UUID
     * @return Security account details
     */
    @GET
    @Path("/{securityAccountUuid}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSecurityAccountById(@PathParam("portfolioId") String portfolioId,
                                          @PathParam("securityAccountUuid") String securityAccountUuid) {
        try {
            logger.info("Getting security account {} for portfolio {}", securityAccountUuid, portfolioId);
            
            // Get the cached Client for this portfolio
            Client client = portfolioFileService.getPortfolio(portfolioId);
            
            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED", 
                    "Portfolio must be opened first before accessing security accounts");
            }
            
            // Find the portfolio by UUID
            Portfolio portfolio = client.getPortfolios().stream()
                .filter(p -> securityAccountUuid.equals(p.getUUID()))
                .findFirst()
                .orElse(null);
            
            if (portfolio == null) {
                logger.warn("Security account not found: {} in portfolio: {}", 
                    securityAccountUuid, portfolioId);
                return createErrorResponse(Response.Status.NOT_FOUND, 
                    "Security account not found", 
                    "Security account with UUID " + securityAccountUuid + " not found in portfolio");
            }
            
            // Convert to DTO with value
            PortfolioDto portfolioDto = convertPortfolioToDtoWithValue(portfolio, client);
            
            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("portfolioId", portfolioId);
            response.put("securityAccount", portfolioDto);
            
            logger.info("Returning security account {} ({}) for portfolio {}", 
                portfolio.getName(), securityAccountUuid, portfolioId);
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            logger.error("Unexpected error getting security account {} for portfolio {}: {}", 
                securityAccountUuid, portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, 
                "Internal server error", 
                e.getMessage());
        }
    }
    
    /**
     * Get security account (portfolio) value over time.
     * 
     * This endpoint returns the portfolio's market value over a specified date range.
     * The values are calculated based on holdings and security prices at each date.
     * 
     * @param portfolioId The portfolio ID (file)
     * @param securityAccountUuid The security account (portfolio) UUID
     * @param startDate Start date for the time series (optional, defaults to 1 year ago)
     * @param endDate End date for the time series (optional, defaults to today)
     * @return Response containing the portfolio's values over time
     */
    @GET
    @Path("/{securityAccountUuid}/values")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSecurityAccountValues(@PathParam("portfolioId") String portfolioId,
                                            @PathParam("securityAccountUuid") String securityAccountUuid,
                                            @QueryParam("startDate") String startDate,
                                            @QueryParam("endDate") String endDate) {
        try {
            logger.info("Getting values for security account {} in portfolio {}", 
                securityAccountUuid, portfolioId);
            
            // Get the cached Client for this portfolio
            Client client = portfolioFileService.getPortfolio(portfolioId);
            
            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED", 
                    "Portfolio must be opened first before accessing portfolio values");
            }
            
            // Find the portfolio by UUID
            Portfolio portfolio = client.getPortfolios().stream()
                .filter(p -> securityAccountUuid.equals(p.getUUID()))
                .findFirst()
                .orElse(null);
            
            if (portfolio == null) {
                logger.warn("Security account not found: {} in client: {}", 
                    securityAccountUuid, portfolioId);
                return createErrorResponse(Response.Status.NOT_FOUND, 
                    "Security account not found", 
                    "Security account with UUID " + securityAccountUuid + " not found");
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
                PortfolioSnapshot snapshot = PortfolioSnapshot.create(portfolio, converter, currentDate);
                Money marketValue = snapshot.getValue();
                
                // Convert to double value
                double value = marketValue.getAmount() / Values.Amount.divider();
                
                valuePoints.add(new ValueDataPointDto(currentDate, value));
                currentDate = currentDate.plusDays(1);
            }
            
            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("portfolioId", portfolioId);
            response.put("securityAccountUuid", securityAccountUuid);
            response.put("securityAccountName", portfolio.getName());
            response.put("baseCurrency", client.getBaseCurrency());
            response.put("startDate", start);
            response.put("endDate", end);
            response.put("dataPointsCount", valuePoints.size());
            response.put("values", valuePoints);
            response.put("timezone", ZoneId.systemDefault().getId());
            
            logger.info("Returning {} value data points for security account {} ({})", 
                valuePoints.size(), portfolio.getName(), securityAccountUuid);
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            logger.error("Unexpected error getting values for security account {} in client {}: {}", 
                securityAccountUuid, portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, 
                "Internal server error", 
                e.getMessage());
        }
    }
    
    /**
     * Create a new security account.
     * TODO: Implement security account creation
     * 
     * @param portfolioId The portfolio ID
     * @param portfolioData Security account data
     * @return Created security account
     */
    @POST
    @Path("")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createSecurityAccount(@PathParam("portfolioId") String portfolioId,
                                         SecurityAccountMutationDto portfolioData) {
        try {
            logger.info("Creating security account for portfolio {}", portfolioId);

            Client client = portfolioFileService.getPortfolio(portfolioId);

            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED",
                    "Portfolio must be opened first before creating security accounts");
            }

            Portfolio portfolio = SecurityAccountManagementService.createSecurityAccount(client, portfolioData);
            client.markDirty();
            portfolioFileService.saveFile(portfolioId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("portfolioId", portfolioId);
            response.put("securityAccount", convertPortfolioToDtoWithValue(portfolio, client));
            response.put("message", "Security account created successfully");

            logger.info("Created security account {} ({}) for portfolio {}",
                portfolio.getName(), portfolio.getUUID(), portfolioId);

            return Response.status(Response.Status.CREATED).entity(response).build();

        } catch (NoSuchElementException e) {
            logger.warn("Reference account not found for security account creation in portfolio {}: {}",
                portfolioId, e.getMessage());
            return createErrorResponse(Response.Status.NOT_FOUND,
                "Reference account not found",
                e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid security account creation request for portfolio {}: {}", portfolioId, e.getMessage());
            return createErrorResponse(Response.Status.BAD_REQUEST,
                "Invalid request",
                e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error creating security account for portfolio {}: {}",
                portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                "Internal server error",
                e.getMessage());
        }
    }
    
    /**
     * Update an existing security account.
     * TODO: Implement security account update
     * 
     * @param portfolioId The portfolio ID
     * @param securityAccountUuid The security account UUID
     * @param portfolioData Updated security account data
     * @return Updated security account
     */
    @PUT
    @Path("/{securityAccountUuid}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateSecurityAccount(@PathParam("portfolioId") String portfolioId,
                                         @PathParam("securityAccountUuid") String securityAccountUuid,
                                         SecurityAccountMutationDto portfolioData) {
        try {
            logger.info("Updating security account {} for portfolio {}", securityAccountUuid, portfolioId);

            Client client = portfolioFileService.getPortfolio(portfolioId);

            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED",
                    "Portfolio must be opened first before updating security accounts");
            }

            Portfolio portfolio = SecurityAccountManagementService.updateSecurityAccount(client, securityAccountUuid,
                portfolioData);
            client.markDirty();
            portfolioFileService.saveFile(portfolioId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("portfolioId", portfolioId);
            response.put("securityAccount", convertPortfolioToDtoWithValue(portfolio, client));
            response.put("message", "Security account updated successfully");

            logger.info("Updated security account {} ({}) for portfolio {}",
                portfolio.getName(), securityAccountUuid, portfolioId);

            return Response.ok(response).build();

        } catch (NoSuchElementException e) {
            logger.warn("Security account or reference account not found while updating {} in portfolio {}: {}",
                securityAccountUuid, portfolioId, e.getMessage());
            return createErrorResponse(Response.Status.NOT_FOUND,
                "Security account or reference account not found",
                e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid security account update request for account {} in portfolio {}: {}",
                securityAccountUuid, portfolioId, e.getMessage());
            return createErrorResponse(Response.Status.BAD_REQUEST,
                "Invalid request",
                e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error updating security account {} for portfolio {}: {}",
                securityAccountUuid, portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                "Internal server error",
                e.getMessage());
        }
    }
    
    /**
     * Delete a security account.
     * TODO: Implement security account deletion
     * 
     * @param portfolioId The portfolio ID
     * @param securityAccountUuid The security account UUID
     * @return Deletion confirmation
     */
    @DELETE
    @Path("/{securityAccountUuid}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteSecurityAccount(@PathParam("portfolioId") String portfolioId,
                                         @PathParam("securityAccountUuid") String securityAccountUuid) {
        try {
            logger.info("Deleting security account {} for portfolio {}", securityAccountUuid, portfolioId);

            Client client = portfolioFileService.getPortfolio(portfolioId);

            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED",
                    "Portfolio must be opened first before deleting security accounts");
            }

            Portfolio portfolio = SecurityAccountManagementService.deleteSecurityAccount(client, securityAccountUuid);
            client.markDirty();
            portfolioFileService.saveFile(portfolioId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("portfolioId", portfolioId);
            response.put("securityAccountUuid", securityAccountUuid);
            response.put("securityAccountName", portfolio.getName());
            response.put("message", "Security account deleted successfully");

            logger.info("Deleted security account {} ({}) for portfolio {}",
                portfolio.getName(), securityAccountUuid, portfolioId);

            return Response.ok(response).build();

        } catch (NoSuchElementException e) {
            logger.warn("Security account not found: {} in portfolio: {}", securityAccountUuid, portfolioId);
            return createErrorResponse(Response.Status.NOT_FOUND,
                "Security account not found",
                e.getMessage());
        } catch (SecurityAccountDeletionException e) {
            logger.warn("Cannot delete security account {} in portfolio {}: {} transaction(s)",
                securityAccountUuid, portfolioId, e.getTransactionsCount());
            return createErrorResponse(Response.Status.CONFLICT,
                "Security account has transactions",
                e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error deleting security account {} for portfolio {}: {}",
                securityAccountUuid, portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                "Internal server error",
                e.getMessage());
        }
    }
    
    /**
     * Helper method to convert Portfolio to PortfolioDto.
     * Aligned with PortfolioFileService.loadPortfolios()
     */
    private PortfolioDto convertPortfolioToDto(Portfolio portfolio) {
        PortfolioDto dto = new PortfolioDto();
        dto.setUuid(portfolio.getUUID());
        dto.setName(portfolio.getName());
        dto.setNote(portfolio.getNote());
        dto.setRetired(portfolio.isRetired());
        dto.setTransactionsCount(portfolio.getTransactions().size());
        dto.setUpdatedAt(portfolio.getUpdatedAt());
        
        // Add reference account if exists
        if (portfolio.getReferenceAccount() != null) {
            dto.setReferenceAccountUuid(portfolio.getReferenceAccount().getUUID());
            dto.setReferenceAccountName(portfolio.getReferenceAccount().getName());
        }
        
        return dto;
    }
    
    /**
     * Helper method to convert Portfolio to PortfolioDto with current value.
     * This version includes current value calculation.
     */
    private PortfolioDto convertPortfolioToDtoWithValue(Portfolio portfolio, Client client) {
        PortfolioDto dto = convertPortfolioToDto(portfolio);
        
        // Create currency converter for portfolio valuations
        ExchangeRateProviderFactory factory = new ExchangeRateProviderFactory(client);
        CurrencyConverter converter = new CurrencyConverterImpl(factory, client.getBaseCurrency());
        java.time.LocalDate today = java.time.LocalDate.now();
        
        // Calculate current value (already in base currency)
        try {
            PortfolioSnapshot snapshot = PortfolioSnapshot.create(portfolio, converter, today);
            Money value = snapshot.getValue();
            // Convert from internal representation (multiplied by 100) to decimal
            dto.setCurrentValue(value.getAmount() / 100.0);
        } catch (Exception e) {
            logger.warn("Failed to calculate portfolio value for {}: {}", portfolio.getName(), e.getMessage());
            dto.setCurrentValue(0.0);
        }
        
        return dto;
    }
}

