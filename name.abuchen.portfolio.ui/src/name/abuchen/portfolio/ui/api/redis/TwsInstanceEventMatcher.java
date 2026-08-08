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

    static void logFilterDecision(Logger logger, String channel, String eventType, String eventInstanceId,
                    String portfolioId, Client client)
    {
        if (logger == null || !logger.isDebugEnabled())
            return;

        String normalizedEventInstanceId = eventInstanceId(
                        eventInstanceId == null ? Optional.empty() : Optional.of(eventInstanceId));
        String portfolioInstanceId = portfolioInstanceId(client);

        logger.debug(
                        "TWS instance filter: channel={}, eventType={}, eventInstanceId={}, portfolioId={}, portfolioInstanceId={}, applies={}",
                        channel, eventType, normalizedEventInstanceId, portfolioId, portfolioInstanceId,
                        matches(eventInstanceId, client));
    }
}
