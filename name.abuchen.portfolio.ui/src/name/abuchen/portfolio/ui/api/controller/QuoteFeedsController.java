package name.abuchen.portfolio.ui.api.controller;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import name.abuchen.portfolio.online.Factory;
import name.abuchen.portfolio.ui.api.dto.QuoteFeedDto;

/**
 * REST controller exposing available quote feed providers for price updates.
 */
@Path("/api/v1/quote-feeds")
public class QuoteFeedsController extends BaseController
{
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listQuoteFeeds()
    {
        try
        {
            List<QuoteFeedDto> feeds = Factory.getQuoteFeedProvider().stream()
                            .map(feed -> new QuoteFeedDto(feed.getId(), feed.getName()))
                            .sorted(Comparator.comparing(QuoteFeedDto::getName, String.CASE_INSENSITIVE_ORDER))
                            .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("count", feeds.size());
            response.put("quoteFeeds", feeds);

            return Response.ok(response).build();
        }
        catch (Exception e)
        {
            logger.error("Unexpected error listing quote feeds: {}", e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                            "Internal server error",
                            e.getMessage());
        }
    }
}
