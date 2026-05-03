package name.abuchen.portfolio.server;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.swt.widgets.Display;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import name.abuchen.portfolio.PortfolioLog;
import name.abuchen.portfolio.money.ExchangeRateProvider;
import name.abuchen.portfolio.money.ExchangeRateProviderFactory;
import name.abuchen.portfolio.money.impl.ECBExchangeRateProvider;
import name.abuchen.portfolio.ui.api.PortfolioApiServer;
import name.abuchen.portfolio.ui.api.redis.RedisFlexImportListener;
import name.abuchen.portfolio.ui.api.redis.RedisPriceUpdateListener;
import name.abuchen.portfolio.ui.api.service.PortfolioFileService;
import name.abuchen.portfolio.ui.api.service.QuoteFeedApiKeyService;
import name.abuchen.portfolio.ui.api.service.ScheduledPriceUpdateService;

/**
 * Server launcher that starts the HTTP server from the UI project without
 * opening any windows.
 * <p>
 * This launcher creates an SWT Display (required for UI-bound code to run) but
 * does not open any Shell or Workbench windows. The HTTP server is started as
 * the main entry point.
 */
public class ServerLauncher implements IApplication
{
    private static final int DEFAULT_PORT = 8080;
    private static final String PORT_PROPERTY = "portfolio.server.port";
    
    private PortfolioApiServer apiServer;
    private ScheduledPriceUpdateService scheduledPriceUpdateService;
    private RedisPriceUpdateListener redisPriceUpdateListener;
    private RedisFlexImportListener redisFlexImportListener;
    private volatile boolean running = true;

    @Override
    public Object start(IApplicationContext context) throws Exception
    {
        PortfolioLog.info("🚀 Starting Portfolio Performance Server...");
        
        // Log workspace location for debugging
        String workspaceLocation = Platform.getInstanceLocation().getURL().getPath();
        PortfolioLog.info("📁 Workspace location: " + workspaceLocation);
        PortfolioLog.info("📁 Preferences path: " + workspaceLocation + ".metadata/.plugins/org.eclipse.core.runtime/.settings/");
        
        // Get port from system property or use default
        int port = getPort();
        
        // Create Display for SWT/JFace components (required for UI runtime)
        // but don't create any Shell or Window
        Display display = Display.getDefault();
        
        // Initialize quote feeds to ensure ServiceLoader works (must happen after SPI Fly is ready)
        initializeQuoteFeeds();
        
        // Initialize exchange rates (normally done by StartupAddon in UI mode)
        initializeExchangeRates();
        
        // Start the HTTP server from the UI project on the UI thread
        display.asyncExec(() -> {
            try
            {
                apiServer = new PortfolioApiServer();
                apiServer.start(port);
                PortfolioLog.info("✅ Server started successfully on port " + port);
                
                // Start the scheduled price and exchange rate update service
                startScheduledPriceUpdateService();

                startRedisPriceListener();
                startRedisFlexImportListener();

                PortfolioLog.info("📋 Press Ctrl+C to stop the server");
            }
            catch (Exception e)
            {
                PortfolioLog.error("❌ Failed to start server: " + e.getMessage());
                PortfolioLog.error(e);
                running = false;
            }
        });
        
        // Keep the server running until shutdown is requested
        // Process UI events to keep Display alive
        while (running)
        {
            try
            {
                if (!display.readAndDispatch())
                {
                    display.sleep();
                }
            }
            catch (Exception e)
            {
                PortfolioLog.error("Error in event loop: " + e.getMessage());
                PortfolioLog.error(e);
            }
        }
        
        return IApplication.EXIT_OK;
    }

    @Override
    public void stop()
    {
        PortfolioLog.info("🛑 Stopping Portfolio Performance Server...");
        running = false;
        
        // Stop the scheduled price and exchange rate update service
        if (scheduledPriceUpdateService != null)
        {
            scheduledPriceUpdateService.stop();
        }

        if (redisFlexImportListener != null)
        {
            try
            {
                redisFlexImportListener.stop();
            }
            catch (Exception e)
            {
                PortfolioLog.error("❌ Failed to stop Redis flex import listener: " + e.getMessage());
                PortfolioLog.error(e);
            }
            redisFlexImportListener = null;
        }

        if (redisPriceUpdateListener != null)
        {
            try
            {
                redisPriceUpdateListener.stop();
            }
            catch (Exception e)
            {
                PortfolioLog.error("❌ Failed to stop Redis price listener: " + e.getMessage());
                PortfolioLog.error(e);
            }
            redisPriceUpdateListener = null;
        }
        
        // Save exchange rates before shutdown
        saveExchangeRates();
        
        if (apiServer != null)
        {
            apiServer.stop();
        }
        
        Display display = Display.getCurrent();
        if (display != null && !display.isDisposed())
        {
            display.wake();
        }
        
        PortfolioLog.info("✅ Server stopped");
    }
    
    /**
     * Save exchange rates to cache files on shutdown.
     */
    private void saveExchangeRates()
    {
        PortfolioLog.info("💾 Saving exchange rates to cache...");
        NullProgressMonitor monitor = new NullProgressMonitor();
        
        for (ExchangeRateProvider provider : ExchangeRateProviderFactory.getProviders())
        {
            try
            {
                provider.save(monitor);
                PortfolioLog.info("   ✅ Saved: " + provider.getName());
            }
            catch (IOException e)
            {
                PortfolioLog.error("   ⚠️  Failed to save: " + provider.getName() + " - " + e.getMessage());
            }
        }
    }
    
    private int getPort()
    {
        String portStr = System.getProperty(PORT_PROPERTY);
        if (portStr != null && !portStr.isEmpty())
        {
            try
            {
                return Integer.parseInt(portStr);
            }
            catch (NumberFormatException e)
            {
                PortfolioLog.error("Invalid port specified: " + portStr + ", using default: " + DEFAULT_PORT);
            }
        }
        return DEFAULT_PORT;
    }
    
    /**
     * Initialize quote feeds by forcing Factory class to load.
     * This ensures ServiceLoader runs after SPI Fly is ready to intercept it.
     */
    private void initializeQuoteFeeds()
    {
        PortfolioLog.info("========================================");
        PortfolioLog.info("Initializing quote feed providers...");
        PortfolioLog.info("========================================");
        
        try
        {
            // Force Factory class to load, which triggers ServiceLoader in static initializer
            int feedCount = name.abuchen.portfolio.online.Factory.getQuoteFeedProvider().size();
            PortfolioLog.info("✅ Loaded " + feedCount + " quote feed providers");
            
            if (feedCount == 0)
            {
                PortfolioLog.error("⚠️  WARNING: No quote feed providers found!");
                PortfolioLog.error("   This likely means ServiceLoader failed to discover feeds.");
                PortfolioLog.error("   Check that Apache Aries SPI Fly is active and configured correctly.");
            }
            else
            {
                PortfolioLog.info("   Available feeds:");
                name.abuchen.portfolio.online.Factory.getQuoteFeedProvider().forEach(feed -> 
                    PortfolioLog.info("   - " + feed.getName() + " (" + feed.getId() + ")")
                );
            }
        }
        catch (Exception e)
        {
            PortfolioLog.error("❌ Failed to initialize quote feeds: " + e.getMessage());
            PortfolioLog.error(e);
        }
        
        PortfolioLog.info("========================================");
    }
    
    /**
     * Initialize exchange rate providers by loading their cached data from files.
     * Online updates are handled by the scheduled exchange rate update service.
     */
    private void initializeExchangeRates()
    {
        PortfolioLog.info("========================================");
        PortfolioLog.info("Initializing exchange rate providers...");
        PortfolioLog.info("========================================");
        
        // Check where ECB stores its cache files
        checkECBStorageLocation();
        
        List<ExchangeRateProvider> providers = ExchangeRateProviderFactory.getProviders();
        PortfolioLog.info("Found " + providers.size() + " exchange rate providers");
        
        if (providers.isEmpty())
        {
            PortfolioLog.error("⚠️  WARNING: No exchange rate providers found! This is a problem.");
            return;
        }
        
        NullProgressMonitor monitor = new NullProgressMonitor();
        
        for (ExchangeRateProvider provider : providers)
        {
            try
            {
                PortfolioLog.info("📥 Loading exchange rates for: " + provider.getName());
                provider.load(monitor);
                PortfolioLog.info("   ✅ Loaded successfully: " + provider.getName());
            }
            catch (Exception e)
            {
                PortfolioLog.error("❌ Failed to load exchange rates for: " + provider.getName());
                PortfolioLog.error("   Error: " + e.getMessage());
                PortfolioLog.error(e);
            }
        }
        
        PortfolioLog.info("========================================");
        PortfolioLog.info("Exchange rate initialization complete");
        PortfolioLog.info("   Online updates will be handled by the scheduled update service");
        PortfolioLog.info("========================================");
    }
    
    /**
     * Check where ECB exchange rate provider stores its cache files.
     */
    private void checkECBStorageLocation()
    {
        try
        {
            Bundle bundle = FrameworkUtil.getBundle(ECBExchangeRateProvider.class);
            if (bundle == null)
            {
                PortfolioLog.error("⚠️  ECB bundle is null - OSGi not initialized properly!");
                return;
            }
            
            PortfolioLog.info("📁 Checking ECB storage location...");
            PortfolioLog.info("   Bundle: " + bundle.getSymbolicName() + " [" + bundle.getBundleId() + "]");
            PortfolioLog.info("   Bundle location: " + bundle.getLocation());
            
            File dataFile = bundle.getDataFile("ecb_exchange_rates.pb");
            if (dataFile != null)
            {
                PortfolioLog.info("   Cache file path: " + dataFile.getAbsolutePath());
                PortfolioLog.info("   Cache file exists: " + dataFile.exists());
                if (dataFile.exists())
                {
                    PortfolioLog.info("   Cache file size: " + dataFile.length() + " bytes");
                    PortfolioLog.info("   Cache file last modified: " + new java.util.Date(dataFile.lastModified()));
                }
                else
                {
                    PortfolioLog.info("   ⚠️  Cache file does not exist - will use defaults until updated from internet");
                }
            }
            else
            {
                PortfolioLog.error("   ⚠️  Could not get data file path from bundle");
            }
        }
        catch (Exception e)
        {
            PortfolioLog.error("Error checking ECB storage location: " + e.getMessage());
            PortfolioLog.error(e);
        }
    }
    
    /**
     * Start the scheduled price and exchange rate update service.
     * This service will automatically update prices on two schedules:
     * - LATEST prices: every 30 seconds for real-time updates
     * - HISTORIC prices + exchange rates: every 10 minutes
     */
    private void startScheduledPriceUpdateService()
    {
        try
        {
            PortfolioLog.info("========================================");
            PortfolioLog.info("Starting unified scheduled update service...");
            PortfolioLog.info("========================================");
            
            // Initialize API keys from preferences once at startup
            PortfolioLog.info("🔑 Initializing quote feed API keys from preferences...");
            QuoteFeedApiKeyService.initializeApiKeys();
            PortfolioLog.info("✅ API keys initialized");
            
            // Get the singleton PortfolioFileService instance
            // This ensures the scheduled service uses the same cache as the PortfolioController
            PortfolioFileService portfolioFileService = PortfolioFileService.getInstance();
            
            scheduledPriceUpdateService = new ScheduledPriceUpdateService(portfolioFileService);
            scheduledPriceUpdateService.start();
            
            PortfolioLog.info("✅ Scheduled update service started");
            PortfolioLog.info("   LATEST prices: every 30 seconds (real-time)");
            PortfolioLog.info("   HISTORIC prices: every 10 minutes");
            PortfolioLog.info("   • Exchange rates from online sources");
            PortfolioLog.info("   • Security prices for all loaded portfolios");
            PortfolioLog.info("   • lastPriceUpdateTime property on all portfolios");
            PortfolioLog.info("========================================");
        }
        catch (Exception e)
        {
            PortfolioLog.error("❌ Failed to start scheduled update service: " + e.getMessage());
            PortfolioLog.error(e);
        }
    }

    private void startRedisPriceListener()
    {
        try
        {
            redisPriceUpdateListener = new RedisPriceUpdateListener();
            redisPriceUpdateListener.start();
            PortfolioLog.info("✅ Redis price listener started");
        }
        catch (Exception e)
        {
            PortfolioLog.error("❌ Failed to start Redis price listener: " + e.getMessage());
            PortfolioLog.error(e);
        }
    }

    private void startRedisFlexImportListener()
    {
        try
        {
            redisFlexImportListener = new RedisFlexImportListener();
            redisFlexImportListener.start();
        }
        catch (Exception e)
        {
            PortfolioLog.error("❌ Failed to start Redis flex import listener: " + e.getMessage());
            PortfolioLog.error(e);
        }
    }
}

