package name.abuchen.portfolio.ui.api.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.SecurityPrice;
import name.abuchen.portfolio.money.CurrencyConverter;
import name.abuchen.portfolio.money.CurrencyConverterImpl;
import name.abuchen.portfolio.money.ExchangeRateProviderFactory;
import name.abuchen.portfolio.snapshot.SecurityPosition;
import name.abuchen.portfolio.snapshot.security.CalculationLineItem;
import name.abuchen.portfolio.snapshot.security.SecurityPerformanceRecord;
import name.abuchen.portfolio.snapshot.security.SecurityPerformanceSnapshot;
import name.abuchen.portfolio.snapshot.security.SecurityPerformanceIndicator;
import name.abuchen.portfolio.util.Interval;

/**
 * Service responsible for caching {@link SecurityPerformanceSnapshot} instances
 * per portfolio and invalidating or refreshing them based on incoming events.
 *
 * This cache relies on explicit events for structural or price changes. A full
 * rebuild happens on structural updates, while price-only updates trigger a
 * fast path that recalculates the affected performance records.
 */
public final class SecurityPerformanceSnapshotCacheService
{
    private static final Logger logger = LoggerFactory.getLogger(SecurityPerformanceSnapshotCacheService.class);

    private static final SecurityPerformanceSnapshotCacheService INSTANCE = new SecurityPerformanceSnapshotCacheService();

    private static final Class<? extends SecurityPerformanceIndicator> COSTS = SecurityPerformanceIndicator.Costs.class;
    private static final Class<? extends SecurityPerformanceIndicator> DIVIDENDS = SecurityPerformanceIndicator.Dividends.class;
    private static final Class<? extends SecurityPerformanceIndicator> RATE_OF_RETURN = SecurityPerformanceIndicator.RateOfReturn.class;

    private final ConcurrentMap<String, SnapshotCacheEntry> cache = new ConcurrentHashMap<>();

    private SecurityPerformanceSnapshotCacheService()
    {
    }

    public static SecurityPerformanceSnapshotCacheService getInstance()
    {
        return INSTANCE;
    }

    /**
     * Returns the latest cached (or lazily recomputed) snapshots for the given
     * portfolio. The caller must provide the already-loaded {@link Client}
     * instance.
     *
     * @param portfolioId
     *            the portfolio identifier
     * @param client
     *            cached {@link Client} instance
     * @return bundle containing daily, YTD and all-time snapshots
     */
    public SecurityPerformanceSnapshotBundle getSnapshots(String portfolioId, Client client)
    {
        Objects.requireNonNull(portfolioId, "portfolioId"); //$NON-NLS-1$
        Objects.requireNonNull(client, "client"); //$NON-NLS-1$

        SnapshotCacheEntry entry = cache.computeIfAbsent(portfolioId, id -> new SnapshotCacheEntry());
        return entry.ensureUpToDate(portfolioId, client, null);
    }

    /**
     * Returns the latest cached (or lazily recomputed) snapshots for the given
     * portfolio, ensuring that each security in the provided list has a record
     * in each snapshot. If any security is missing from any snapshot, the
     * snapshots will be rebuilt.
     *
     * @param portfolioId
     *            the portfolio identifier
     * @param client
     *            cached {@link Client} instance
     * @param securities
     *            list of securities that must have records in each snapshot
     * @return bundle containing daily, YTD and all-time snapshots
     */
    public SecurityPerformanceSnapshotBundle getSnapshots(String portfolioId, Client client, List<Security> securities)
    {
        Objects.requireNonNull(portfolioId, "portfolioId"); //$NON-NLS-1$
        Objects.requireNonNull(client, "client"); //$NON-NLS-1$

        SnapshotCacheEntry entry = cache.computeIfAbsent(portfolioId, id -> new SnapshotCacheEntry());
        return entry.ensureUpToDate(portfolioId, client, securities);
    }

    /**
     * Marks the cached snapshots for the given portfolio as needing a refresh due
     * to price updates on a subset of securities. The actual recalculation happens
     * lazily on the next {@link #getSnapshots(String, Client)} call.
     *
     * @param portfolioId
     *            portfolio identifier
     * @param securityUuids
     *            collection of security UUIDs whose prices changed (may be empty to
     *            indicate a broad price update)
     */
    public void handlePriceUpdate(String portfolioId, Collection<String> securityUuids)
    {
        if (portfolioId == null)
            return;

        SnapshotCacheEntry entry = cache.get(portfolioId);
        if (entry != null)
        {
            entry.markPricesDirty(securityUuids);
        }
    }

    /**
     * Clears the cached snapshots for the given portfolio. Used when structural
     * changes (transactions, securities, configuration) occur.
     *
     * @param portfolioId
     *            portfolio identifier
     */
    public void handlePortfolioUpdate(String portfolioId)
    {
        if (portfolioId == null)
            return;

        cache.remove(portfolioId);
        logger.debug("Cleared snapshot cache for portfolio {}", portfolioId); //$NON-NLS-1$
    }

    /**
     * Clears all cached snapshot data.
     */
    public void clearAll()
    {
        cache.clear();
        logger.info("Cleared all cached security performance snapshots"); //$NON-NLS-1$
    }

    /**
     * Bundle wrapper containing the snapshots required by controllers.
     */
    public static final class SecurityPerformanceSnapshotBundle
    {
        private final SecurityPerformanceSnapshot allTime;
        private final SecurityPerformanceSnapshot yearToDate;
        private final SecurityPerformanceSnapshot daily;

        private SecurityPerformanceSnapshotBundle(SecurityPerformanceSnapshot allTime,
                        SecurityPerformanceSnapshot yearToDate, SecurityPerformanceSnapshot daily)
        {
            this.allTime = allTime;
            this.yearToDate = yearToDate;
            this.daily = daily;
        }

        public SecurityPerformanceSnapshot allTime()
        {
            return allTime;
        }

        public SecurityPerformanceSnapshot yearToDate()
        {
            return yearToDate;
        }

        public SecurityPerformanceSnapshot daily()
        {
            return daily;
        }
    }

    private static final class SnapshotCacheEntry
    {
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private volatile SecurityPerformanceSnapshotBundle snapshots;
        private final Set<String> dirtySecurityIds = ConcurrentHashMap.newKeySet();

        SecurityPerformanceSnapshotBundle ensureUpToDate(String portfolioId, Client client, List<Security> requiredSecurities)
        {
            logger.debug("Ensuring up to date security performance snapshots for portfolio {}", portfolioId); //$NON-NLS-1$
            
            // Check if we need to verify security records
            boolean needsSecurityCheck = requiredSecurities != null && !requiredSecurities.isEmpty();
            
            // Fast-path: if we already have snapshots and there are no pending price updates, 
            // check if all required securities are present (read-only check).
            if (snapshots != null && dirtySecurityIds.isEmpty())
            {
                if (!needsSecurityCheck || hasAllSecurityRecords(snapshots, requiredSecurities, client))
                {
                    logger.debug("Returning cached security performance snapshots for portfolio {}", portfolioId); //$NON-NLS-1$
                    return snapshots;
                }
                // If securities are missing, fall through to rebuild
                logger.info("Some required securities are missing from snapshots, forcing rebuild for portfolio {}", portfolioId); //$NON-NLS-1$
            }

            // A refresh is required; take the write lock so only one thread rebuilds or refreshes the snapshots.
            lock.writeLock().lock();
            try
            {
                // Double-check after acquiring lock
                if (snapshots != null && dirtySecurityIds.isEmpty())
                {
                    if (!needsSecurityCheck || hasAllSecurityRecords(snapshots, requiredSecurities, client))
                    {
                        logger.debug("Returning cached security performance snapshots for portfolio {}", portfolioId); //$NON-NLS-1$
                        return snapshots;
                    }
                    // Force rebuild by clearing snapshots
                    snapshots = null;
                }

                // If the cache entry is empty, rebuild the snapshots from scratch.
                if (snapshots == null)
                {
                    logger.info("Rebuilding security performance snapshots for portfolio {}", portfolioId); //$NON-NLS-1$
                    snapshots = buildSnapshots(client);
                    dirtySecurityIds.clear();
                    
                    // After rebuild, verify all required securities are present
                    if (needsSecurityCheck && !hasAllSecurityRecords(snapshots, requiredSecurities, client))
                    {
                        logger.warn("Some required securities are still missing after rebuild for portfolio {}", portfolioId); //$NON-NLS-1$
                    }
                }
                else if (!dirtySecurityIds.isEmpty())
                {
                    logger.info("Refreshing security performance snapshots for portfolio {}", portfolioId); //$NON-NLS-1$
                    Set<String> affected = Set.copyOf(dirtySecurityIds);
                    refreshSnapshots(client, snapshots, affected);
                    dirtySecurityIds.clear();
                    
                    // After refresh, verify all required securities are present
                    if (needsSecurityCheck && !hasAllSecurityRecords(snapshots, requiredSecurities, client))
                    {
                        logger.info("Some required securities are missing after refresh, forcing rebuild for portfolio {}", portfolioId); //$NON-NLS-1$
                        snapshots = buildSnapshots(client);
                        dirtySecurityIds.clear();
                    }
                }

                return snapshots;
            }
            finally
            {
                lock.writeLock().unlock();
            }
        }

        /**
         * Checks if all required securities have records in each snapshot of the bundle.
         * Only checks securities that have transactions or shares held, as securities
         * without any activity won't have records in snapshots.
         *
         * @param bundle
         *            the snapshot bundle to check
         * @param requiredSecurities
         *            list of securities that must have records
         * @param client
         *            the client instance to check for transactions
         * @return true if all securities with activity have records in all snapshots, false otherwise
         */
        private boolean hasAllSecurityRecords(SecurityPerformanceSnapshotBundle bundle, List<Security> requiredSecurities, Client client)
        {
            if (bundle == null || bundle.allTime() == null || bundle.yearToDate() == null || bundle.daily() == null)
            {
                return false;
            }

            for (Security security : requiredSecurities)
            {
                if (security == null)
                    continue;

                // Skip securities that don't have any transactions - they won't have records
                if (!security.hasTransactions(client))
                {
                    logger.debug("Security {} has no transactions, skipping record check", security.getName()); //$NON-NLS-1$
                    continue;
                }

                boolean hasAllTime = bundle.allTime().getRecord(security).isPresent();
                boolean hasYearToDate = bundle.yearToDate().getRecord(security).isPresent();
                boolean hasDaily = bundle.daily().getRecord(security).isPresent();

                if (!hasAllTime || !hasYearToDate || !hasDaily)
                {
                    logger.debug("Security {} is missing from snapshots - allTime: {}, yearToDate: {}, daily: {}", //$NON-NLS-1$
                        security.getName(), hasAllTime, hasYearToDate, hasDaily);
                    return false;
                }
            }

            return true;
        }

        void markPricesDirty(Collection<String> securityUuids)
        {
            if (securityUuids == null || securityUuids.isEmpty())
                return;

            dirtySecurityIds.addAll(securityUuids);
        }
    }

    private static SecurityPerformanceSnapshotBundle buildSnapshots(Client client)
    {
        long startedAt = System.currentTimeMillis();
        try
        {
            ExchangeRateProviderFactory factory = new ExchangeRateProviderFactory(client);
            CurrencyConverter converter = new CurrencyConverterImpl(factory, client.getBaseCurrency());

            LocalDate today = LocalDate.now();
            Interval allTimeInterval = Interval.of(LocalDate.of(1900, 1, 1), today);

            LocalDate yearStart = LocalDate.of(today.getYear(), 1, 1);
            Interval ytdInterval = Interval.of(yearStart.minusDays(1), today);

            Interval dailyInterval = Interval.of(today.minusDays(2), today);

            CompletableFuture<SecurityPerformanceSnapshot> allTimeFuture = CompletableFuture.supplyAsync(
                            () -> SecurityPerformanceSnapshot.create(client, converter, allTimeInterval, false, COSTS,
                                            DIVIDENDS));
            CompletableFuture<SecurityPerformanceSnapshot> yearToDateFuture = CompletableFuture.supplyAsync(
                            () -> SecurityPerformanceSnapshot.create(client, converter, ytdInterval, false, COSTS));
            CompletableFuture<SecurityPerformanceSnapshot> dailyFuture = CompletableFuture.supplyAsync(
                            () -> SecurityPerformanceSnapshot.create(client, converter, dailyInterval, false, COSTS,
                                            RATE_OF_RETURN));

            SecurityPerformanceSnapshotBundle bundle = new SecurityPerformanceSnapshotBundle(allTimeFuture.join(),
                            yearToDateFuture.join(), dailyFuture.join());

            logger.info("Built security performance snapshots for portfolio in {} ms", //$NON-NLS-1$
                            System.currentTimeMillis() - startedAt);

            return bundle;
        }
        catch (Exception ex)
        {
            logger.error("Failed to build security performance snapshots: {}", ex.getMessage(), ex); //$NON-NLS-1$
            return new SecurityPerformanceSnapshotBundle(null, null, null);
        }
    }

    private static void refreshSnapshots(Client client, SecurityPerformanceSnapshotBundle bundle,
                    Set<String> affectedSecurityIds)
    {
        for (Security security : client.getSecurities())
        {
            if (!affectedSecurityIds.contains(security.getUUID()))
                continue;

            logger.info("Refreshing security performance snapshots for security {}", security.getName()); //$NON-NLS-1$
            refreshRecord(bundle.allTime, security, COSTS, DIVIDENDS);
            refreshRecord(bundle.yearToDate, security, COSTS);
            refreshRecord(bundle.daily, security, COSTS, RATE_OF_RETURN);
        }
    }

    @SafeVarargs
    private static void refreshRecord(SecurityPerformanceSnapshot snapshot, Security security,
                    Class<? extends SecurityPerformanceIndicator>... indicators)
    {
        snapshot.getRecord(security).ifPresent(record -> {
            updateValuationAtEndPositions(record, security);
            record.calculate(indicators);
        });
    }

    private static void updateValuationAtEndPositions(SecurityPerformanceRecord record, Security security)
    {
        var lineItems = record.getLineItems();

        for (int index = 0; index < lineItems.size(); index++)
        {
            CalculationLineItem item = lineItems.get(index);

            if (!(item instanceof CalculationLineItem.ValuationAtEnd valuation))
                continue;

            if (!(valuation.getOwner() instanceof Portfolio portfolio))
                continue;

            Optional<SecurityPosition> positionOpt = valuation.getSecurityPosition();
            if (positionOpt.isEmpty())
                continue;

            LocalDateTime timestamp = valuation.getDateTime();
            SecurityPrice priceAtDate = security.getSecurityPrice(timestamp.toLocalDate());
            if (priceAtDate == null)
                continue;

            SecurityPosition updatedPosition = positionOpt.get().withPrice(priceAtDate);
            lineItems.set(index, CalculationLineItem.atEnd(portfolio, updatedPosition, timestamp));
        }
    }
}
