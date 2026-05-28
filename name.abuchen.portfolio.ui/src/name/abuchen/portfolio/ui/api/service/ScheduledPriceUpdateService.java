package name.abuchen.portfolio.ui.api.service;

import java.io.IOException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.money.ExchangeRateProvider;
import name.abuchen.portfolio.money.ExchangeRateProviderFactory;
import name.abuchen.portfolio.ui.jobs.priceupdate.UpdatePricesJob;

/**
 * Service that automatically updates prices and exchange rates on a scheduled basis.
 * This service runs on two separate schedules:
 * 1. LATEST prices: Every 60 seconds for real-time updates
 * 2. HISTORIC prices: Every 10 minutes for historical data
 * 3. Exchange rates: Updated with historic prices (every 10 minutes)
 * 4. Sets the lastPriceUpdateTime property on all updated portfolios
 */
public class ScheduledPriceUpdateService {
    
    private static final Logger logger = LoggerFactory.getLogger(ScheduledPriceUpdateService.class);
    
    // Update intervals
    private static final int LATEST_UPDATE_INTERVAL_SECONDS = 3600; // 1 hour
    private static final int HISTORIC_UPDATE_INTERVAL_MINUTES = 360;
    
    private final PortfolioFileService portfolioFileService;
    private final ScheduledExecutorService latestScheduler;
    private final ScheduledExecutorService historicScheduler;
    private volatile boolean running = false;
    
    /**
     * Constructor with portfolio file service dependency.
     * 
     * @param portfolioFileService The portfolio file service to use for accessing cached portfolios
     */
    public ScheduledPriceUpdateService(PortfolioFileService portfolioFileService) {
        this.portfolioFileService = portfolioFileService;
        this.latestScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "ScheduledLatestPriceUpdate");
            thread.setDaemon(true);
            return thread;
        });
        this.historicScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "ScheduledHistoricPriceUpdate");
            thread.setDaemon(true);
            return thread;
        });
    }
    
    /**
     * Start the scheduled price and exchange rate update service.
     * The service will update latest prices every 60 seconds and historic prices/exchange rates every 10 minutes.
     */
    public void start() {
        if (running) {
            logger.warn("Scheduled price update service is already running");
            return;
        }
        
        running = true;
        logger.info("🕐 Starting scheduled price and exchange rate update service");
        logger.info("   • LATEST prices: every {} seconds", LATEST_UPDATE_INTERVAL_SECONDS);
        logger.info("   • HISTORIC prices + exchange rates: every {} minutes", HISTORIC_UPDATE_INTERVAL_MINUTES);
      
        // Load exchange rate providers from cache first (one-time operation)
        ExchangeRateProviderLoader.ensureLoaded();
        
        // Schedule LATEST price updates every 60 seconds - run immediately on startup
        latestScheduler.scheduleAtFixedRate(
            this::updateLatestPrices,
            0,  // Run immediately
            LATEST_UPDATE_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        
        // Schedule HISTORIC price + exchange rate updates every 10 minutes - run immediately on startup
        historicScheduler.scheduleAtFixedRate(
            this::updateHistoricPricesAndExchangeRates,
            0,  // Run immediately
            HISTORIC_UPDATE_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        );
        
        logger.info("✅ Scheduled price and exchange rate update service started successfully");
    }
    
    /**
     * Stop the scheduled price update service.
     */
    public void stop() {
        if (!running) {
            return;
        }
        
        logger.info("🛑 Stopping scheduled price update service...");
        running = false;
        
        latestScheduler.shutdown();
        historicScheduler.shutdown();
        
        try {
            if (!latestScheduler.awaitTermination(LATEST_UPDATE_INTERVAL_SECONDS, TimeUnit.SECONDS)) {
                logger.warn("Latest scheduler did not terminate within 60 seconds, forcing shutdown");
                latestScheduler.shutdownNow();
            }
            if (!historicScheduler.awaitTermination(LATEST_UPDATE_INTERVAL_SECONDS, TimeUnit.SECONDS)) {
                logger.warn("Historic scheduler did not terminate within 60 seconds, forcing shutdown");
                historicScheduler.shutdownNow();
            }
            logger.info("✅ Scheduled price update service stopped");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while waiting for schedulers to terminate", e);
            latestScheduler.shutdownNow();
            historicScheduler.shutdownNow();
        }
    }
    
    /**
     * Update only LATEST prices for all portfolios currently in the cache.
     * This method is called every 60 seconds by the latest price scheduler.
     */
    private void updateLatestPrices() {
        try {
            logger.info("📊 Starting LATEST price update...");
            
            // Get the IDs of all cached portfolios
            Set<String> portfolioIds = portfolioFileService.getCachedPortfolioIds();
            
            if (portfolioIds.isEmpty()) {
                logger.debug("No portfolios currently loaded in cache, skipping latest price update");
                return;
            }
            
            // Update each portfolio
            for (String portfolioId : portfolioIds) {
                try {
                    updatePortfolioPricesWithTarget(portfolioId, EnumSet.of(UpdatePricesJob.Target.LATEST));
                } catch (Exception e) {
                    logger.error("Failed to update latest prices for portfolio {}: {}", portfolioId, e.getMessage());
                }
            }
            
            logger.info("✅ LATEST price update completed for {} portfolio(s)", portfolioIds.size());
            
        } catch (Exception e) {
            logger.error("Error in latest price update task", e);
        }
    }
    
    /**
     * Update HISTORIC prices and exchange rates for all portfolios currently in the cache.
     * This method is called every 10 minutes by the historic price scheduler.
     */
    private void updateHistoricPricesAndExchangeRates() {
        try {
            logger.info("========================================");
            logger.info("📊 Starting HISTORIC price + exchange rate update");
            logger.info("========================================");
            
            // Step 1: Update exchange rates first (global operation)
            logger.info("Step 1/2: Updating exchange rates...");
            updateExchangeRates();
            
            // Step 2: Update historic prices for all cached portfolios
            logger.info("Step 2/2: Updating historic security prices...");
            
            // Get the IDs of all cached portfolios
            Set<String> portfolioIds = portfolioFileService.getCachedPortfolioIds();
            
            if (portfolioIds.isEmpty()) {
                logger.info("No portfolios currently loaded in cache, skipping historic price update");
                logger.info("========================================");
                return;
            }
            
            logger.info("Found {} portfolio(s) in cache", portfolioIds.size());
            
            // Update each portfolio
            int successCount = 0;
            int failureCount = 0;
            
            for (String portfolioId : portfolioIds) {
                try {
                    updatePortfolioPricesWithTarget(portfolioId, EnumSet.of(UpdatePricesJob.Target.HISTORIC));
                    successCount++;
                } catch (Exception e) {
                    logger.error("Failed to update historic prices for portfolio {}: {}", portfolioId, e.getMessage(), e);
                    failureCount++;
                }
            }
            
            logger.info("========================================");
            logger.info("✅ HISTORIC update completed");
            logger.info("   Portfolios updated: {}", successCount);
            if (failureCount > 0) {
                logger.info("   Portfolios failed: {}", failureCount);
            }
            logger.info("========================================");
            
        } catch (Exception e) {
            logger.error("Error in historic price update task", e);
        }
    }
    
    /**
     * Update exchange rates and prices for a specific portfolio.
     * This is a public method that can be called by the REST API or scheduled tasks.
     * 
     * @param portfolioId The portfolio ID
     * @throws Exception if the update fails
     */
    public void updatePortfolioPricesAndExchangeRates(String portfolioId) throws Exception {
        logger.info("Updating exchange rates and prices for portfolio: {}", portfolioId);
        
        // Step 1: Update exchange rates first (global operation)
        updateExchangeRates();
        
        // Step 2: Update portfolio prices
        updatePortfolioPrices(portfolioId);
    }
    
    /**
     * Update prices for a specific portfolio with the given target (LATEST and/or HISTORIC).
     * 
     * @param portfolioId The portfolio ID
     * @param targets The update targets (LATEST and/or HISTORIC)
     * @throws Exception if the update fails
     */
    private void updatePortfolioPricesWithTarget(String portfolioId, Set<UpdatePricesJob.Target> targets) throws Exception {
        boolean isLatestOnly = targets.equals(EnumSet.of(UpdatePricesJob.Target.LATEST));
        
        if (isLatestOnly) {
            logger.debug("📈 Updating LATEST prices for portfolio: {}", portfolioId);
        } else {
            logger.info("📈 Updating prices for portfolio: {}", portfolioId);
        }
        
        // Get the cached Client for this portfolio
        Client client = portfolioFileService.getPortfolio(portfolioId);
        
        if (client == null) {
            logger.warn("Portfolio {} is no longer in cache, skipping", portfolioId);
            return;
        }
        
        // Create predicate to filter only active (non-retired) securities
        Predicate<Security> onlyActive = s -> !s.isRetired()
                 && (s.getTickerSymbol() == null || !s.getTickerSymbol().replaceAll("\\s+", "").matches(".*\\d{6}[CP]\\d{8}"));
        
        // Count active securities
        long activeSecurities = client.getSecurities().stream()
                .filter(onlyActive)
                .count();
        
        if (isLatestOnly) {
            logger.debug("   Found {} active securities to update", activeSecurities);
        } else {
            logger.info("   Found {} active securities to update", activeSecurities);
        }
        
        if (activeSecurities == 0) {
            if (isLatestOnly) {
                logger.debug("   No active securities to update, skipping");
            } else {
                logger.info("   No active securities to update, skipping");
            }
            return;
        }
        
        // Create and schedule the update quotes job with the specified targets
        Job updateJob = new UpdatePricesJob(client, onlyActive, targets);
        updateJob.schedule();
        
        // Wait for the job to complete
        updateJob.join();
        
        if (isLatestOnly) {
            logger.debug("   Price update job completed");
        } else {
            logger.info("   Price update job completed. Saving portfolio file...");
            
            // Set the lastPriceUpdateTime property on the client
            String updateTimestamp = Instant.now().toString();
            client.setProperty("lastPriceUpdateTime", updateTimestamp);
            logger.info("   Set lastPriceUpdateTime to: {}", updateTimestamp);
            
            // Save the portfolio file after the update
            portfolioFileService.saveFile(portfolioId);
            
            logger.info("   ✅ Portfolio file saved successfully");
        }
    }
    
    /**
     * Update prices for a specific portfolio (both LATEST and HISTORIC).
     * 
     * @param portfolioId The portfolio ID
     * @throws Exception if the update fails
     */
    private void updatePortfolioPrices(String portfolioId) throws Exception {
        updatePortfolioPricesWithTarget(portfolioId, 
            EnumSet.of(UpdatePricesJob.Target.LATEST, UpdatePricesJob.Target.HISTORIC));
    }
    
    /**
     * Update exchange rates from online sources.
     * This updates exchange rates globally before updating portfolio prices.
     */
    private void updateExchangeRates() {
        try {
            logger.info("💱 Updating exchange rates from online sources...");
            
            List<ExchangeRateProvider> providers = ExchangeRateProviderFactory.getProviders();
            
            if (providers.isEmpty()) {
                logger.warn("No exchange rate providers found, skipping exchange rate update");
                return;
            }
            
            logger.info("Found {} exchange rate provider(s)", providers.size());
            
            NullProgressMonitor monitor = new NullProgressMonitor();
            int successCount = 0;
            int failureCount = 0;
            
            for (ExchangeRateProvider provider : providers) {
                try {
                    logger.info("   🌐 Updating from: {} [@{}]", 
                                provider.getName(),
                                Integer.toHexString(System.identityHashCode(provider)));
                    provider.update(monitor);
                    
                    // Save the updated data to cache file
                    provider.save(monitor);
                    logger.info("   ✅ Updated and saved: {} [@{}]", 
                                provider.getName(),
                                Integer.toHexString(System.identityHashCode(provider)));
                    
                    successCount++;
                } catch (IOException e) {
                    logger.error("   ⚠️  Failed to update {} [@{}]: {}", 
                                 provider.getName(),
                                 Integer.toHexString(System.identityHashCode(provider)),
                                 e.getMessage());
                    failureCount++;
                }
            }
            
            logger.info("Exchange rate update completed: {} succeeded, {} failed", successCount, failureCount);
            
        } catch (Exception e) {
            logger.error("Error updating exchange rates: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Check if the service is running.
     * 
     * @return true if the service is running
     */
    public boolean isRunning() {
        return running;
    }
}

