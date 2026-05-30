package name.abuchen.portfolio.ui.api.config;

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import name.abuchen.portfolio.ui.api.controller.AccountsController;
import name.abuchen.portfolio.ui.api.controller.DashboardController;
import name.abuchen.portfolio.ui.api.controller.EarningController;
import name.abuchen.portfolio.ui.api.controller.HelloWorldController;
import name.abuchen.portfolio.ui.api.controller.OptionsController;
import name.abuchen.portfolio.ui.api.controller.PortfolioController;
import name.abuchen.portfolio.ui.api.controller.PriceController;
import name.abuchen.portfolio.ui.api.controller.QuoteFeedsController;
import name.abuchen.portfolio.ui.api.controller.SecurityAccountsController;
import name.abuchen.portfolio.ui.api.controller.SecurityController;
import name.abuchen.portfolio.ui.api.controller.TaxonomiesController;
import name.abuchen.portfolio.ui.api.controller.TransactionsController;
import name.abuchen.portfolio.ui.api.controller.WidgetController;
import name.abuchen.portfolio.ui.api.controller.FlexImportController;

/**
 * Jersey configuration for JSON support in the Portfolio Performance API server.
 * Uses Jersey 2.x with manual resource registration to avoid HK2 dependency issues.
 */
public class JerseyConfig extends ResourceConfig {
    
    /**
     * Provider to configure ObjectMapper with Java 8 date/time support
     */
    @Provider
    public static class ObjectMapperContextResolver implements ContextResolver<ObjectMapper> {
        private final ObjectMapper mapper;

        public ObjectMapperContextResolver() {
            mapper = new ObjectMapper();
            // Register JavaTimeModule to handle Java 8 date/time types like LocalDateTime
            mapper.registerModule(new JavaTimeModule());
        }

        @Override
        public ObjectMapper getContext(Class<?> type) {
            return mapper;
        }
    }
    
    public JerseyConfig() {
        // Register Jackson for JSON processing
        register(JacksonFeature.class);
        
        // Register our custom ObjectMapper provider
        register(ObjectMapperContextResolver.class);
        
        // Manually register controllers to avoid package scanning issues
        register(HelloWorldController.class);
        register(PortfolioController.class);
        register(PriceController.class);
        
        // Register new specialized controllers
        register(SecurityController.class);
        register(QuoteFeedsController.class);
        register(DashboardController.class);
        register(WidgetController.class);
        register(AccountsController.class);
        register(SecurityAccountsController.class);
        register(EarningController.class);
        register(OptionsController.class);
        register(TransactionsController.class);
        register(TaxonomiesController.class);
        register(FlexImportController.class);
        
        // Disable Bean Validation to avoid javax.validation dependency in OSGi
        property(ServerProperties.BV_FEATURE_DISABLE, true);

        // Disable WADL generation to avoid potential issues
        property("jersey.config.server.wadl.disable", "true");
    }
}