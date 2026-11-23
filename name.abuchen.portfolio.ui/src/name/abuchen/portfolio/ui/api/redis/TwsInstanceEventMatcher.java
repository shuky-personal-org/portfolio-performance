package name.abuchen.portfolio.ui.api.redis;

import java.util.Optional;

import org.slf4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.ClientProperties;

final class TwsInstanceEventMatcher
{
    static final String EVENT_PROPERTY = "tws_instance_id";

    /**
     * When {@code true}, an event is applied only to portfolios assigned to the
     * TWS instance that sent it. Unset or anything else: every event is applied
     * as before and the decision is only logged.
     */
    static final String ENFORCE_ENV_VARIABLE = "TWS_INSTANCE_FILTER_ENFORCE"; //$NON-NLS-1$

    private TwsInstanceEventMatcher()
    {
    }

    static String eventInstanceId(JsonObject payload)
    {
        if (payload == null)
            return ClientProperties.DEFAULT_TWS_INSTANCE_ID;

        JsonElement element = payload.get(EVENT_PROPERTY);
        if (element == null || element.isJsonNull())
            return ClientProperties.DEFAULT_TWS_INSTANCE_ID;

        return eventInstanceId(Optional.ofNullable(element.getAsString()));
    }

    static String eventInstanceId(Optional<String> value)
    {
        return value.map(String::trim).filter(v -> !v.isEmpty()).orElse(ClientProperties.DEFAULT_TWS_INSTANCE_ID);
    }

    static String portfolioInstanceId(Client client)
    {
        if (client == null)
            return ClientProperties.DEFAULT_TWS_INSTANCE_ID;

        return new ClientProperties(client).getTwsInstanceId();
    }

    static boolean matches(String eventInstanceId, Client client)
    {
        String normalizedEventInstanceId = eventInstanceId(
                        eventInstanceId == null ? Optional.empty() : Optional.of(eventInstanceId));
        String normalizedPortfolioInstanceId = portfolioInstanceId(client);

        if (normalizedPortfolioInstanceId.equals(normalizedEventInstanceId))
            return true;

        return "primary".equals(normalizedPortfolioInstanceId)
                        && ClientProperties.DEFAULT_TWS_INSTANCE_ID.equals(normalizedEventInstanceId);
    }

    /** Whether filtering is switched on for this process. */
    static boolean isEnforced()
    {
        return isEnforced(System.getenv(ENFORCE_ENV_VARIABLE));
    }

    static boolean isEnforced(String configured)
    {
        return configured != null && "true".equalsIgnoreCase(configured.trim()); //$NON-NLS-1$
    }

    /**
     * Whether an event should be applied to a portfolio: always when filtering
     * is off, otherwise only when the instances match.
     */
    static boolean applies(String eventInstanceId, Client client)
    {
        return applies(isEnforced(), eventInstanceId, client);
    }

    static boolean applies(boolean enforced, String eventInstanceId, Client client)
    {
        return !enforced || matches(eventInstanceId, client);
    }

    static void logFilterDecision(Logger logger, String channel, String eventType, String eventInstanceId,
                    String portfolioId, Client client)
    {
        if (logger == null || !logger.isDebugEnabled())
            return;

        String normalizedEventInstanceId = eventInstanceId(
                        eventInstanceId == null ? Optional.empty() : Optional.of(eventInstanceId));
        String portfolioInstanceId = portfolioInstanceId(client);

        logger.debug(
                        "TWS instance filter: channel={}, eventType={}, eventInstanceId={}, portfolioId={}, portfolioInstanceId={}, matches={}, enforced={}",
                        channel, eventType, normalizedEventInstanceId, portfolioId, portfolioInstanceId,
                        matches(eventInstanceId, client), isEnforced());
    }
}
