package name.abuchen.portfolio.ui.api.redis;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Optional;

import org.junit.Test;

import com.google.gson.JsonObject;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.ClientProperties;

public class TwsInstanceEventMatcherTest
{
    @Test
    public void defaultsMissingEventInstanceId()
    {
        assertThat(TwsInstanceEventMatcher.eventInstanceId(Optional.empty()),
                        is(ClientProperties.DEFAULT_TWS_INSTANCE_ID));
        assertThat(TwsInstanceEventMatcher.eventInstanceId(Optional.of("   ")),
                        is(ClientProperties.DEFAULT_TWS_INSTANCE_ID));
        assertThat(TwsInstanceEventMatcher.eventInstanceId(new JsonObject()),
                        is(ClientProperties.DEFAULT_TWS_INSTANCE_ID));
    }

    @Test
    public void readsExplicitEventInstanceId()
    {
        JsonObject payload = new JsonObject();
        payload.addProperty(TwsInstanceEventMatcher.EVENT_PROPERTY, " primary ");

        assertThat(TwsInstanceEventMatcher.eventInstanceId(payload), is("primary"));
    }

    @Test
    public void resolvesPortfolioInstanceIdFromClientProperties()
    {
        Client client = new Client();

        assertThat(TwsInstanceEventMatcher.portfolioInstanceId(client),
                        is(ClientProperties.DEFAULT_TWS_INSTANCE_ID));

        client.setProperty(ClientProperties.Keys.TWS_INSTANCE_ID, "secondary");

        assertThat(TwsInstanceEventMatcher.portfolioInstanceId(client), is("secondary"));
    }

    @Test
    public void matchesNormalizedEventAndPortfolioInstanceIds()
    {
        Client client = new Client();
        client.setProperty(ClientProperties.Keys.TWS_INSTANCE_ID, "primary");

        assertThat(TwsInstanceEventMatcher.matches("primary", client), is(true));
        assertThat(TwsInstanceEventMatcher.matches("secondary", client), is(false));
        assertThat(TwsInstanceEventMatcher.matches(null, client), is(false));
    }
}
