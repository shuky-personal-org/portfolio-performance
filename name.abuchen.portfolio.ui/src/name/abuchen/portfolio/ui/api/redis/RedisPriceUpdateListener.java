package name.abuchen.portfolio.ui.api.redis;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.LatestSecurityPrice;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.ui.api.controller.SecurityController;
import name.abuchen.portfolio.ui.api.service.PortfolioFileService;
import name.abuchen.portfolio.ui.api.service.SecurityPerformanceSnapshotCacheService;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.exceptions.JedisConnectionException;

/**
 * Redis listener that subscribes to price update events and updates
 * in-memory portfolio data accordingly.
 */
public class RedisPriceUpdateListener
{
    private static final Logger logger = LoggerFactory.getLogger(RedisPriceUpdateListener.class);
    private static final String CHANNEL_MARKET_PRICES = "market:prices";

    private final PortfolioFileService portfolioFileService;
    private final RedisConnectionConfig config;

    private volatile boolean running;
    private Thread subscriberThread;
    private volatile Jedis activeSubscriber;

    public RedisPriceUpdateListener()
    {
        this(PortfolioFileService.getInstance(), RedisConnectionConfig.fromEnvironment());
    }

    RedisPriceUpdateListener(PortfolioFileService portfolioFileService, RedisConnectionConfig config)
    {
        this.portfolioFileService = Objects.requireNonNull(portfolioFileService, "portfolioFileService"); //$NON-NLS-1$
        this.config = Objects.requireNonNull(config, "config"); //$NON-NLS-1$
    }

    /**
     * Start listening for price update events on Redis.
     */
    public void start()
    {
        if (running)
        {
            logger.warn("Redis price listener already running");
            return;
        }

        if (!config.isEnabled())
        {
            logger.info("Redis price listener disabled (set REDIS_ENABLED=false to hide this message)");
            return;
        }

        running = true;
        subscriberThread = new Thread(this::runSubscriberLoop, "RedisPriceUpdateListener"); //$NON-NLS-1$
        subscriberThread.setDaemon(true);
        subscriberThread.start();
        logger.info("Starting Redis price listener on {}:{}", config.host(), config.port());
    }

    /**
     * Stop listening and close connections.
     */
    public void stop()
    {
        running = false;

        Jedis subscriber = activeSubscriber;
        if (subscriber != null)
        {
            try
            {
                subscriber.close();
            }
            catch (Exception e)
            {
                logger.debug("Error while closing Redis subscriber: {}", e.getMessage());
            }
        }

        if (subscriberThread != null)
        {
            subscriberThread.interrupt();
            try
            {
                subscriberThread.join(TimeUnit.SECONDS.toMillis(5));
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void runSubscriberLoop()
    {
        JedisPubSub pubSub = createPubSub();

        while (running)
        {
            try (Jedis jedis = new Jedis(new HostAndPort(config.host(), config.port()), buildClientConfig()))
            {
                activeSubscriber = jedis;
                jedis.subscribe(pubSub, CHANNEL_MARKET_PRICES);
            }
            catch (JedisConnectionException e)
            {
                if (!running)
                    break;

                logger.warn("Lost Redis connection: {}. Reconnecting in {} ms", e.getMessage(), config.reconnectDelayMillis());
                sleep(config.reconnectDelayMillis());
            }
            catch (Exception e)
            {
                if (!running)
                    break;

                logger.error("Unexpected Redis subscription error", e);
                sleep(config.reconnectDelayMillis());
            }
            finally
            {
                activeSubscriber = null;
            }
        }

        try
        {
            pubSub.unsubscribe();
        }
        catch (Exception e)
        {
            logger.debug("Error unsubscribing Redis pub/sub: {}", e.getMessage());
        }
    }

    private JedisPubSub createPubSub()
    {
        return new JedisPubSub()
        {
            @Override
            public void onMessage(String channel, String message)
            {
                if (!CHANNEL_MARKET_PRICES.equals(channel))
                    return;

                handlePriceMessage(message);
            }

            @Override
            public void onSubscribe(String channel, int subscribedChannels)
            {
                logger.info("Subscribed to Redis channel '{}'", channel);
            }

            @Override
            public void onUnsubscribe(String channel, int subscribedChannels)
            {
                logger.info("Unsubscribed from Redis channel '{}'", channel);
            }
        };
    }

    private DefaultJedisClientConfig buildClientConfig()
    {
        DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder()
                        .database(config.database())
                        .timeoutMillis(config.timeoutMillis());

        config.username().ifPresent(builder::user);
        config.password().ifPresent(builder::password);

        return builder.build();
    }

    private void handlePriceMessage(String message)
    {
        try
        {
            JsonElement parsed = JsonParser.parseString(message);
            if (!parsed.isJsonObject())
            {
                logger.debug("Ignoring non-object Redis message: {}", message);
                return;
            }

            JsonObject payload = parsed.getAsJsonObject();

            Optional<String> isin = readString(payload, "isin");
            Optional<String> symbol = readString(payload, "symbol");
            Optional<Double> priceOpt = readDouble(payload, "price");
            Optional<String> currency = readString(payload, "currency");
            String eventInstanceId = TwsInstanceEventMatcher.eventInstanceId(payload);

            if (priceOpt.isEmpty())
            {
                logger.debug("Ignoring price update without numeric price: {}", message);
                return;
            }

            if (isin.isEmpty() && symbol.isEmpty())
            {
                logger.debug("Ignoring price update without identifiers: {}", message);
                return;
            }

            Instant eventInstant = readString(payload, "timestamp")
                            .flatMap(this::parseTimestamp)
                            .orElseGet(Instant::now);

            LocalDate priceDate = eventInstant.atZone(ZoneId.systemDefault()).toLocalDate();
            double price = priceOpt.orElse(Double.NaN);

            applyPriceUpdate(
                            isin.orElse(null),
                            symbol.orElse(null),
                            currency.orElse(null),
                            price,
                            priceDate,
                            eventInstant,
                            eventInstanceId);
        }
        catch (JsonSyntaxException e)
        {
            logger.warn("Failed to parse Redis price update: {}", e.getMessage());
        }
        catch (Exception e)
        {
            logger.error("Error handling Redis price update", e);
        }
    }

    private void applyPriceUpdate(String isin, String symbol, String currency, double price, LocalDate priceDate,
                    Instant eventInstant, String eventInstanceId)
    {
        if (!Double.isFinite(price) || price <= 0d)
        {
            logger.debug("Ignoring invalid price value: {}", price);
            return;
        }

        Set<String> portfolioIds = portfolioFileService.getCachedPortfolioIds();
        if (portfolioIds.isEmpty())
            return;

        int securitiesUpdated = 0;

        for (String portfolioId : portfolioIds)
        {
            Client client = portfolioFileService.getPortfolio(portfolioId);
            if (client == null)
                continue;

            TwsInstanceEventMatcher.logObserveOnlyDecision(logger, CHANNEL_MARKET_PRICES, "price_update",
                            eventInstanceId, portfolioId, client);

            boolean updatedPortfolio = false;
            Set<String> updatedSecurityIds = new HashSet<>();

            for (Security security : client.getSecurities())
            {
                if (security == null || security.isRetired())
                    continue;

                if (!matchesSecurity(security, isin, symbol, currency))
                    continue;

                if (updateSecurityLatestPrice(security, price, priceDate, eventInstant))
                {
                    updatedPortfolio = true;
                    securitiesUpdated++;
                    updatedSecurityIds.add(security.getUUID());
                    logger.info("Updated latest price for security '{}' (portfolio {}, price {})",
                                    security.getName(), portfolioId, price);
                }
            }

            if (updatedPortfolio)
            {
                SecurityPerformanceSnapshotCacheService.getInstance()
                                .handlePriceUpdate(portfolioId, updatedSecurityIds);
            }
        }

        if (securitiesUpdated == 0 && logger.isDebugEnabled())
        {
            logger.debug("Price update did not match any cached securities (isin={}, symbol={}, currency={})", isin,
                            symbol, currency);
        }
    }

    private boolean updateSecurityLatestPrice(Security security, double price, LocalDate priceDate, Instant eventInstant)
    {
        LatestSecurityPrice latestPrice = new LatestSecurityPrice(priceDate, Values.Quote.factorize(price));
        latestPrice.setHigh(LatestSecurityPrice.NOT_AVAILABLE);
        latestPrice.setLow(LatestSecurityPrice.NOT_AVAILABLE);
        latestPrice.setVolume(LatestSecurityPrice.NOT_AVAILABLE);

        boolean updated = security.setLatest(latestPrice);
        if (updated)
            security.setUpdatedAt(eventInstant);

        return updated;
    }

    private boolean matchesSecurity(Security security, String isin, String symbol, String currency)
    {
        if (currency != null && !currency.isBlank())
        {
            String securityCurrency = security.getCurrencyCode();
            if (securityCurrency != null && !securityCurrency.isBlank())
            {
                if (!securityCurrency.equalsIgnoreCase(currency))
                    return false;
            }
            else
            {
                String targetCurrency = security.getTargetCurrencyCode();
                if (targetCurrency == null || targetCurrency.isBlank()
                                || !targetCurrency.equalsIgnoreCase(currency))
                    return false;
            }
        }

        if (isin != null && !isin.isBlank())
        {
            String securityIsin = security.getIsin();
            if (securityIsin != null && securityIsin.equalsIgnoreCase(isin))
                return true;
        }

        if (symbol != null && !symbol.isBlank())
        {
            String ticker = security.getTickerSymbol();
            if (ticker != null && ticker.equalsIgnoreCase(symbol))
                return true;
        }

        return false;
    }

    private Optional<String> readString(JsonObject object, String property)
    {
        JsonElement element = object.get(property);
        if (element == null || element.isJsonNull())
            return Optional.empty();

        String value = element.getAsString();
        return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value.trim());
    }

    private Optional<Double> readDouble(JsonObject object, String property)
    {
        JsonElement element = object.get(property);
        if (element == null || element.isJsonNull())
            return Optional.empty();

        try
        {
            return Optional.of(element.getAsDouble());
        }
        catch (NumberFormatException | ClassCastException e)
        {
            return Optional.empty();
        }
    }

    private Optional<Instant> parseTimestamp(String timestamp)
    {
        if (timestamp == null || timestamp.isBlank())
            return Optional.empty();

        try
        {
            return Optional.of(Instant.parse(timestamp));
        }
        catch (DateTimeParseException e)
        {
            logger.debug("Unable to parse timestamp '{}': {}", timestamp, e.getMessage());
            return Optional.empty();
        }
    }

    private void sleep(long millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }
}

