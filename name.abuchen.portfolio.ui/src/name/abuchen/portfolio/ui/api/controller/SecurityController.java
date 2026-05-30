package name.abuchen.portfolio.ui.api.controller;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
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
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.money.CurrencyConverter;
import name.abuchen.portfolio.money.CurrencyConverterImpl;
import name.abuchen.portfolio.money.ExchangeRateProviderFactory;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Quote;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.snapshot.security.CapitalGainsRecord;
import name.abuchen.portfolio.snapshot.security.SecurityPerformanceRecord;
import name.abuchen.portfolio.ui.api.dto.SecurityDto;
import name.abuchen.portfolio.ui.api.dto.SecurityMutationDto;
import name.abuchen.portfolio.ui.api.service.SecurityManagementService;
import name.abuchen.portfolio.ui.api.service.SecurityManagementService.SecurityDeletionException;
import name.abuchen.portfolio.ui.api.service.SecurityPerformanceSnapshotCacheService.SecurityPerformanceSnapshotBundle;

/**
 * REST Controller for security operations.
 * 
 * This controller provides endpoints to manage securities within a portfolio.
 */
@Path("/api/v1/portfolios/{portfolioId}/securities")
public class SecurityController extends BaseController {
    
    /**
     * Get all active securities in a portfolio.
     * 
     * This endpoint filters out:
     * - Retired securities
     * - Options contracts (ticker symbols matching pattern: 6 digits + C/P + 8 digits)
     * 
     * @param portfolioId The portfolio ID
     * @param useSnapshot If true (default), includes extended properties calculated from performance snapshots.
     *                    If false, returns simplified version without holdings data and performance metrics.
     * @return List of active securities (excluding retired and options contracts)
     */
    @GET
    @Path("")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllSecurities(@PathParam("portfolioId") String portfolioId,
                                    @QueryParam("useSnapshot") @DefaultValue("true") boolean useSnapshot) {
        try {
            logger.info("Getting all securities for portfolio: {} (useSnapshot: {})", portfolioId, useSnapshot);
            
            // Get the cached Client for this portfolio
            Client client = portfolioFileService.getPortfolio(portfolioId);
            
            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED", 
                    "Portfolio must be opened first before accessing securities");
            }
            
            // Get all securities (filter out retired securities and options contracts)
            List<Security> filteredSecurities = client.getSecurities().stream()
                .filter(security -> !security.isRetired()) // Skip retired securities
                .filter(this::isNotOptionsContract) // Skip options contracts
                .collect(Collectors.toList());
            
            // Get or create cached performance snapshots only if useSnapshot is true
            final SecurityPerformanceSnapshotBundle snapshots = useSnapshot
                ? securitySnapshotCacheService.getSnapshots(portfolioId, client, filteredSecurities)
                : null;
            
            if (useSnapshot && snapshots != null) {
                logger.debug("Got security performance snapshots for portfolio {} - {}", portfolioId, snapshots);
            }
            
            // Convert filtered securities to DTOs
            List<SecurityDto> securities = filteredSecurities.stream()
                .map(security -> convertSecurityToDto(security, client, snapshots, useSnapshot))
                .collect(Collectors.toList());
            
            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("portfolioId", portfolioId);
            response.put("count", securities.size());
            response.put("securities", securities);
            
            logger.info("Returning {} active securities for portfolio {} (filtered out retired and options contracts, useSnapshot: {})", 
                securities.size(), portfolioId, useSnapshot);
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            logger.error("Unexpected error getting securities for portfolio {}: {}", 
                portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, 
                "Internal server error", 
                e.getMessage());
        }
    }
    
    /**
     * Get a specific security by UUID.
     * 
     * This endpoint filters out retired securities and options contracts.
     * If the security is retired or an options contract, a 404 will be returned.
     * 
     * @param portfolioId The portfolio ID
     * @param securityUuid The security UUID
     * @return Security details (if not retired and not an options contract)
     */
    @GET
    @Path("/{securityUuid}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSecurityById(@PathParam("portfolioId") String portfolioId,
                                   @PathParam("securityUuid") String securityUuid) {
        try {
            logger.info("Getting security {} for portfolio {}", securityUuid, portfolioId);
            
            // Get the cached Client for this portfolio
            Client client = portfolioFileService.getPortfolio(portfolioId);
            
            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED", 
                    "Portfolio must be opened first before accessing securities");
            }
            
            // Find the security by UUID (excluding retired securities and options contracts)
            Security security = client.getSecurities().stream()
                .filter(s -> securityUuid.equals(s.getUUID()))
                .filter(s -> !s.isRetired()) // Skip retired securities
                .filter(this::isNotOptionsContract) // Skip options contracts
                .findFirst()
                .orElse(null);
            
            if (security == null) {
                logger.warn("Security not found or filtered out: {} in portfolio: {}", securityUuid, portfolioId);
                return createErrorResponse(Response.Status.NOT_FOUND, 
                    "Security not found", 
                    "Security with UUID " + securityUuid + " not found (or is retired/options contract)");
            }
            
            // Get or create cached performance snapshots for this portfolio
            SecurityPerformanceSnapshotBundle snapshots = securitySnapshotCacheService.getSnapshots(portfolioId,
                client);
            
            // Convert to DTO (always use snapshot for individual security endpoint)
            SecurityDto securityDto = convertSecurityToDto(security, client, snapshots, true);
            
            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("portfolioId", portfolioId);
            response.put("security", securityDto);
            
            logger.info("Returning security {} ({}) for portfolio {}", 
                security.getName(), securityUuid, portfolioId);
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            logger.error("Unexpected error getting security {} for portfolio {}: {}", 
                securityUuid, portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, 
                "Internal server error", 
                e.getMessage());
        }
    }
    
    /**
     * Create a new security.
     * TODO: Implement security creation
     * 
     * @param portfolioId The portfolio ID
     * @param securityData Security data
     * @return Created security
     */
    @POST
    @Path("")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createSecurity(@PathParam("portfolioId") String portfolioId,
                                   SecurityMutationDto securityData) {
        try {
            logger.info("Creating security for portfolio {}", portfolioId);

            Client client = portfolioFileService.getPortfolio(portfolioId);

            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED",
                    "Portfolio must be opened first before creating securities");
            }

            Security security = SecurityManagementService.createSecurity(client, securityData);
            client.markDirty();
            portfolioFileService.saveFile(portfolioId);
            clearCache(portfolioId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("portfolioId", portfolioId);
            response.put("security", convertSecurityToDto(security, client, null, false));
            response.put("message", "Security created successfully");

            logger.info("Created security {} ({}) for portfolio {}", security.getName(), security.getUUID(), portfolioId);

            return Response.status(Response.Status.CREATED).entity(response).build();

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid security creation request for portfolio {}: {}", portfolioId, e.getMessage());
            return createErrorResponse(Response.Status.BAD_REQUEST,
                "Invalid request",
                e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error creating security for portfolio {}: {}",
                portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                "Internal server error",
                e.getMessage());
        }
    }
    
    /**
     * Update an existing security.
     * TODO: Implement security update
     * 
     * @param portfolioId The portfolio ID
     * @param securityUuid The security UUID
     * @param securityData Updated security data
     * @return Updated security
     */
    @PUT
    @Path("/{securityUuid}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateSecurity(@PathParam("portfolioId") String portfolioId,
                                   @PathParam("securityUuid") String securityUuid,
                                   SecurityMutationDto securityData) {
        try {
            logger.info("Updating security {} for portfolio {}", securityUuid, portfolioId);

            Client client = portfolioFileService.getPortfolio(portfolioId);

            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED",
                    "Portfolio must be opened first before updating securities");
            }

            Security security = SecurityManagementService.updateSecurity(client, securityUuid, securityData);
            client.markDirty();
            portfolioFileService.saveFile(portfolioId);
            clearCache(portfolioId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("portfolioId", portfolioId);
            response.put("security", convertSecurityToDto(security, client, null, false));
            response.put("message", "Security updated successfully");

            logger.info("Updated security {} ({}) for portfolio {}", security.getName(), securityUuid, portfolioId);

            return Response.ok(response).build();

        } catch (java.util.NoSuchElementException e) {
            logger.warn("Security not found: {} in portfolio: {}", securityUuid, portfolioId);
            return createErrorResponse(Response.Status.NOT_FOUND,
                "Security not found",
                e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid security update request for security {} in portfolio {}: {}",
                securityUuid, portfolioId, e.getMessage());
            return createErrorResponse(Response.Status.BAD_REQUEST,
                "Invalid request",
                e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error updating security {} for portfolio {}: {}",
                securityUuid, portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                "Internal server error",
                e.getMessage());
        }
    }
    
    /**
     * Delete a security.
     * TODO: Implement security deletion
     * 
     * @param portfolioId The portfolio ID
     * @param securityUuid The security UUID
     * @return Deletion confirmation
     */
    @DELETE
    @Path("/{securityUuid}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteSecurity(@PathParam("portfolioId") String portfolioId,
                                   @PathParam("securityUuid") String securityUuid) {
        try {
            logger.info("Deleting security {} for portfolio {}", securityUuid, portfolioId);

            Client client = portfolioFileService.getPortfolio(portfolioId);

            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED",
                    "Portfolio must be opened first before deleting securities");
            }

            Security security = SecurityManagementService.deleteSecurity(client, securityUuid);
            client.markDirty();
            portfolioFileService.saveFile(portfolioId);
            clearCache(portfolioId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("portfolioId", portfolioId);
            response.put("securityUuid", securityUuid);
            response.put("securityName", security.getName());
            response.put("message", "Security deleted successfully");

            logger.info("Deleted security {} ({}) for portfolio {}", security.getName(), securityUuid, portfolioId);

            return Response.ok(response).build();

        } catch (java.util.NoSuchElementException e) {
            logger.warn("Security not found: {} in portfolio: {}", securityUuid, portfolioId);
            return createErrorResponse(Response.Status.NOT_FOUND,
                "Security not found",
                e.getMessage());
        } catch (SecurityDeletionException e) {
            logger.warn("Cannot delete security {} in portfolio {}: {} transaction(s)",
                securityUuid, portfolioId, e.getTransactionsCount());
            return createErrorResponse(Response.Status.CONFLICT,
                "Security has transactions",
                e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error deleting security {} for portfolio {}: {}",
                securityUuid, portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                "Internal server error",
                e.getMessage());
        }
    }
    
    /**
     * Helper method to check if a security is NOT an options contract.
     * Options contracts have ticker symbols matching pattern: 6 digits + C/P + 8 digits
     * Aligned with PortfolioFileService.loadSecurities()
     */
    private boolean isNotOptionsContract(Security security) {
        String tickerSymbol = security.getTickerSymbol();
        if (tickerSymbol != null && tickerSymbol.replaceAll("\\s+", "").matches(".*\\d{6}[CP]\\d{8}")) {
            return false; // This is an options contract, skip it
        }
        return true; // Not an options contract
    }
    
    /**
     * Clear the performance snapshots cache for a specific portfolio.
     * This should be called when the portfolio is updated.
     */
    public static void clearCache(String portfolioId) {
        securitySnapshotCacheService.handlePortfolioUpdate(portfolioId);
    }
    
    /**
     * Clear all performance snapshots cache.
     * This can be called during application shutdown or when memory is needed.
     */
    public static void clearAllCache() {
        securitySnapshotCacheService.clearAll();
    }
    
    /**
     * Helper method to convert Security to SecurityDto.
     * Aligned with PortfolioFileService.loadSecurities()
     * 
     * @param security The security to convert
     * @param client The client/portfolio
     * @param snapshots Performance snapshots (can be null if useSnapshot is false)
     * @param useSnapshot If true, calculates extended properties including holdings data
     */
    private SecurityDto convertSecurityToDto(Security security, Client client, 
                                            SecurityPerformanceSnapshotBundle snapshots,
                                            boolean useSnapshot) {
        SecurityDto dto = new SecurityDto();
        dto.setUuid(security.getUUID());
        dto.setName(security.getName());
        dto.setCurrencyCode(security.getCurrencyCode());
        dto.setTargetCurrencyCode(security.getTargetCurrencyCode());
        dto.setIsin(security.getIsin());
        dto.setTickerSymbol(security.getTickerSymbol());
        dto.setWkn(security.getWkn());
        dto.setNote(security.getNote());
        dto.setRetired(security.isRetired());
        dto.setFeed(security.getFeed());
        dto.setFeedURL(security.getFeedURL());
        dto.setLatestFeed(security.getLatestFeed());
        dto.setLatestFeedURL(security.getLatestFeedURL());
        dto.setPricesCount(security.getPrices().size());
        dto.setUpdatedAt(security.getUpdatedAt());
        
        // Set the last price
        try {
            name.abuchen.portfolio.model.SecurityPrice lastSecurityPrice = 
                security.getSecurityPrice(LocalDate.now());
            if (lastSecurityPrice != null && lastSecurityPrice.getValue() > 0) {
                dto.setLastPrice(lastSecurityPrice.getValue() / (double) Values.Quote.factor());
            }
        } catch (Exception e) {
            logger.warn("Failed to get last price for security {}: {}", 
                security.getName(), e.getMessage());
        }
        
        // Calculate holdings data using pre-created snapshots only if useSnapshot is true
        if (useSnapshot) {
            calculateHoldingsData(dto, security, client, snapshots);
        }
        
        return dto;
    }
    
    /**
     * Helper method to calculate and populate holdings data for a security.
     * Uses pre-created performance snapshots for efficiency.
     * Calculates:
     * - Shares held
     * - Average price per share
     * - Total holding value in security currency and base currency
     * - Unrealized gains for YTD and daily
     * - Total earnings (dividends)
     */
    private void calculateHoldingsData(SecurityDto dto, Security security, Client client, 
                                      SecurityPerformanceSnapshotBundle snapshots) {
        try {
            // Check if snapshots are available
            if (snapshots == null || snapshots.allTime() == null) {
                logger.warn("No performance snapshots available for security: {}", security.getName());
                return;
            }
            
            // Create currency converter for the client
            ExchangeRateProviderFactory factory = new ExchangeRateProviderFactory(client);
            CurrencyConverter baseConverter = new CurrencyConverterImpl(factory, client.getBaseCurrency());
            CurrencyConverter securityCurrencyConverter = new CurrencyConverterImpl(factory, security.getCurrencyCode());
            
            LocalDate today = LocalDate.now();
            
            // Get all-time performance record
            Optional<SecurityPerformanceRecord> recordOpt = snapshots.allTime().getRecord(security);
            
            if (recordOpt.isPresent()) {
                SecurityPerformanceRecord record = recordOpt.get();
                
                // Set shares held (convert from internal representation)
                long sharesHeld = record.getSharesHeld();
                dto.setSharesHeld(sharesHeld / (double) Values.Share.factor());
                
                // Set average price per share (FIFO cost per share)
                Quote avgPrice = record.getFifoCostPerSharesHeld();
                if (avgPrice != null && sharesHeld > 0) {
                    // Convert from internal representation (Value.Quote) to actual price
                    dto.setAvgPricePerShare(avgPrice.getAmount() / (double) Values.Quote.factor());
                }
                
                // Set total holding value
                // Market value from record is in base currency
                Money marketValueInBase = record.getMarketValue();
                if (marketValueInBase != null) {
                    dto.setTotalHoldingValueBaseCurrency(marketValueInBase.getAmount() / Values.Money.factor());
                    
                    // Convert back to security currency
                    Money marketValueInSecurityCurrency = securityCurrencyConverter.convert(today, marketValueInBase);
                    dto.setTotalHoldingValueSecurityCurrency(marketValueInSecurityCurrency.getAmount() / Values.Money.factor());
                }
                
                // Set total earnings (dividends)
                Money sumOfDividends = record.getSumOfDividends();
                if (sumOfDividends != null) {
                    // Convert to base currency
                    Money dividendsInBase = baseConverter.convert(today, sumOfDividends);
                    dto.setTotalEarnings(dividendsInBase.getAmount() / Values.Money.factor());
                }
                
                // Set all-time unrealized gains
                Money capitalGainsSecurityCurrency = record.getCapitalGainsOnHoldings();
                Money capitalGainsBaseCurrency = baseConverter.convert(today, capitalGainsSecurityCurrency);
                dto.setUnrealizedGainsAllTimeSecurityCurrency(
                                capitalGainsSecurityCurrency.getAmount() / Values.Money.factor());
                dto.setUnrealizedGainsAllTime(capitalGainsBaseCurrency.getAmount() / Values.Money.factor());
                
            } else {
                logger.warn("No All-Time performance record found for security: {}", security.getName());
            }
            
            // Get YTD performance record
            Optional<SecurityPerformanceRecord> ytdRecordOpt = snapshots.yearToDate().getRecord(security);
            if (ytdRecordOpt.isPresent()) {
                SecurityPerformanceRecord ytdRecord = ytdRecordOpt.get();
                // Set YTD unrealized gains
                Money capitalGainsSecurityCurrency = ytdRecord.getCapitalGainsOnHoldings();
                Money capitalGainsBaseCurrency = baseConverter.convert(today, capitalGainsSecurityCurrency);
                dto.setUnrealizedGainsYTDSecurityCurrency(
                                capitalGainsSecurityCurrency.getAmount() / Values.Money.factor());
                dto.setUnrealizedGainsYTD(capitalGainsBaseCurrency.getAmount() / Values.Money.factor());
            } else {
                logger.warn("No YTD performance record found for security: {}", security.getName());
            }
            
            // Get daily performance record
            Optional<SecurityPerformanceRecord> dailyRecordOpt = snapshots.daily().getRecord(security);
            if (dailyRecordOpt.isPresent()) {
                SecurityPerformanceRecord dailyRecord = dailyRecordOpt.get();
                Money capitalGainsSecurityCurrency = dailyRecord.getCapitalGainsOnHoldings();
                Money capitalGainsBaseCurrency = baseConverter.convert(today, capitalGainsSecurityCurrency);
                dto.setUnrealizedGainsDailySecurityCurrency(
                                capitalGainsSecurityCurrency.getAmount() / Values.Money.factor());
                dto.setUnrealizedGainsDaily(capitalGainsBaseCurrency.getAmount() / Values.Money.factor());

                double dailyReturn = dailyRecord.getTrueTimeWeightedRateOfReturn();
                if (Double.isFinite(dailyReturn)) {
                    dto.setDailyPriceChange(dailyReturn * 100d);
                }
            } else {
                logger.warn("No daily performance record found for security: {}", security.getName());
            }
        } catch (Exception e) {
            // Log error but don't fail the entire DTO conversion
            logger.warn("Failed to calculate holdings data for security {}: {}", 
                security.getName(), e.getMessage());
        }
    }
}

