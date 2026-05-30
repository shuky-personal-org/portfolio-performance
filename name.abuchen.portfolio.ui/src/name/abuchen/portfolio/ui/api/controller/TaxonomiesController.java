package name.abuchen.portfolio.ui.api.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.Consumes;
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
import name.abuchen.portfolio.money.ExchangeRateProviderFactory;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.ui.api.dto.AssignmentDto;
import name.abuchen.portfolio.ui.api.dto.ClassificationDto;
import name.abuchen.portfolio.ui.api.dto.TaxonomyDto;
import name.abuchen.portfolio.ui.api.dto.TaxonomyMutationDto;
import name.abuchen.portfolio.ui.api.service.TaxonomyManagementService;
import name.abuchen.portfolio.ui.views.taxonomy.TaxonomyModel;
import name.abuchen.portfolio.ui.views.taxonomy.TaxonomyNode;
import java.time.LocalDate;

/**
 * REST Controller for taxonomy operations.
 * 
 * This controller provides endpoints to manage taxonomies and their classifications within a portfolio.
 */
@Path("/api/v1/portfolios/{portfolioId}/taxonomies")
public class TaxonomiesController extends BaseController {
    
    /**
     * Get all taxonomies in a portfolio.
     * 
     * @param portfolioId The portfolio ID
     * @return List of all taxonomies with their classifications
     */
    @GET
    @Path("")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllTaxonomies(@PathParam("portfolioId") String portfolioId) {
        try {
            logger.info("Getting all taxonomies for portfolio: {}", portfolioId);
            
            // Get the cached Client for this portfolio
            Client client = portfolioFileService.getPortfolio(portfolioId);
            
            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED", 
                    "Portfolio must be opened first before accessing taxonomies");
            }
            
            // Get all taxonomies and convert to DTOs
            List<TaxonomyDto> taxonomies = convertTaxonomiesToDto(client);
            
            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("portfolioId", portfolioId);
            response.put("count", taxonomies.size());
            response.put("taxonomies", taxonomies);
            
            logger.info("Returning {} taxonomies for portfolio {}", taxonomies.size(), portfolioId);
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            logger.error("Unexpected error getting taxonomies for portfolio {}: {}", 
                portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, 
                "Internal server error", 
                e.getMessage());
        }
    }
    
    /**
     * Get a specific taxonomy by ID.
     * 
     * @param portfolioId The portfolio ID
     * @param taxonomyId The taxonomy ID
     * @return Taxonomy details with classifications
     */
    @GET
    @Path("/{taxonomyId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTaxonomyById(@PathParam("portfolioId") String portfolioId,
                                    @PathParam("taxonomyId") String taxonomyId) {
        try {
            logger.info("Getting taxonomy {} for portfolio {}", taxonomyId, portfolioId);
            
            // Get the cached Client for this portfolio
            Client client = portfolioFileService.getPortfolio(portfolioId);
            
            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED", 
                    "Portfolio must be opened first before accessing taxonomies");
            }
            
            // Find the taxonomy by ID
            name.abuchen.portfolio.model.Taxonomy taxonomy = client.getTaxonomies().stream()
                .filter(t -> taxonomyId.equals(t.getId()))
                .findFirst()
                .orElse(null);
            
            if (taxonomy == null) {
                logger.warn("Taxonomy not found: {} in portfolio: {}", taxonomyId, portfolioId);
                return createErrorResponse(Response.Status.NOT_FOUND, 
                    "Taxonomy not found", 
                    "Taxonomy with ID " + taxonomyId + " not found in portfolio");
            }
            
            // Convert to DTO
            TaxonomyDto taxonomyDto = convertTaxonomyToDto(taxonomy, client);
            
            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("portfolioId", portfolioId);
            response.put("taxonomy", taxonomyDto);
            
            logger.info("Returning taxonomy {} ({}) for portfolio {}", 
                taxonomy.getName(), taxonomyId, portfolioId);
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            logger.error("Unexpected error getting taxonomy {} for portfolio {}: {}", 
                taxonomyId, portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, 
                "Internal server error", 
                e.getMessage());
        }
    }
    
    /**
     * Create a new taxonomy.
     * TODO: Implement taxonomy creation
     * 
     * @param portfolioId The portfolio ID
     * @param taxonomyData Taxonomy data
     * @return Created taxonomy
     */
    @POST
    @Path("")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createTaxonomy(@PathParam("portfolioId") String portfolioId,
                                   TaxonomyMutationDto taxonomyData) {
        try {
            logger.info("Creating taxonomy for portfolio {}", portfolioId);

            Client client = portfolioFileService.getPortfolio(portfolioId);

            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED",
                    "Portfolio must be opened first before creating taxonomies");
            }

            name.abuchen.portfolio.model.Taxonomy taxonomy = TaxonomyManagementService.createTaxonomy(client, taxonomyData);
            client.markDirty();
            portfolioFileService.saveFile(portfolioId);

            TaxonomyDto taxonomyDto = convertTaxonomyToDto(taxonomy, client);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("portfolioId", portfolioId);
            response.put("taxonomy", taxonomyDto);
            response.put("message", "Taxonomy created successfully");

            logger.info("Created taxonomy {} ({}) for portfolio {}", taxonomy.getName(), taxonomy.getId(), portfolioId);

            return Response.status(Response.Status.CREATED).entity(response).build();

        } catch (java.util.NoSuchElementException e) {
            logger.warn("Invalid reference in taxonomy creation request for portfolio {}: {}", portfolioId, e.getMessage());
            return createErrorResponse(Response.Status.BAD_REQUEST,
                "Invalid reference",
                e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid taxonomy creation request for portfolio {}: {}", portfolioId, e.getMessage());
            return createErrorResponse(Response.Status.BAD_REQUEST,
                "Invalid request",
                e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error creating taxonomy for portfolio {}: {}",
                portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                "Internal server error",
                e.getMessage());
        }
    }
    
    /**
     * Update an existing taxonomy.
     * TODO: Implement taxonomy update
     * 
     * @param portfolioId The portfolio ID
     * @param taxonomyId The taxonomy ID
     * @param taxonomyData Updated taxonomy data
     * @return Updated taxonomy
     */
    @PUT
    @Path("/{taxonomyId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateTaxonomy(@PathParam("portfolioId") String portfolioId,
                                   @PathParam("taxonomyId") String taxonomyId,
                                   TaxonomyMutationDto taxonomyData) {
        try {
            logger.info("Updating taxonomy {} for portfolio {}", taxonomyId, portfolioId);

            Client client = portfolioFileService.getPortfolio(portfolioId);

            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED",
                    "Portfolio must be opened first before updating taxonomies");
            }

            name.abuchen.portfolio.model.Taxonomy taxonomy = TaxonomyManagementService.updateTaxonomy(client, taxonomyId, taxonomyData);
            client.markDirty();
            portfolioFileService.saveFile(portfolioId);

            TaxonomyDto taxonomyDto = convertTaxonomyToDto(taxonomy, client);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("portfolioId", portfolioId);
            response.put("taxonomy", taxonomyDto);
            response.put("message", "Taxonomy updated successfully");

            logger.info("Updated taxonomy {} ({}) for portfolio {}", taxonomy.getName(), taxonomyId, portfolioId);

            return Response.ok(response).build();

        } catch (java.util.NoSuchElementException e) {
            String message = e.getMessage();
            if (message != null && message.startsWith("Taxonomy")) {
                logger.warn("Taxonomy not found: {} in portfolio: {}", taxonomyId, portfolioId);
                return createErrorResponse(Response.Status.NOT_FOUND,
                    "Taxonomy not found",
                    e.getMessage());
            } else {
                logger.warn("Invalid reference in taxonomy update request for taxonomy {} in portfolio {}: {}",
                    taxonomyId, portfolioId, e.getMessage());
                return createErrorResponse(Response.Status.BAD_REQUEST,
                    "Invalid reference",
                    e.getMessage());
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid taxonomy update request for taxonomy {} in portfolio {}: {}",
                taxonomyId, portfolioId, e.getMessage());
            return createErrorResponse(Response.Status.BAD_REQUEST,
                "Invalid request",
                e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error updating taxonomy {} for portfolio {}: {}",
                taxonomyId, portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                "Internal server error",
                e.getMessage());
        }
    }
    
    /**
     * Delete a taxonomy.
     * TODO: Implement taxonomy deletion
     * 
     * @param portfolioId The portfolio ID
     * @param taxonomyId The taxonomy ID
     * @return Deletion confirmation
     */
    @DELETE
    @Path("/{taxonomyId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteTaxonomy(@PathParam("portfolioId") String portfolioId,
                                   @PathParam("taxonomyId") String taxonomyId) {
        try {
            logger.info("Deleting taxonomy {} for portfolio {}", taxonomyId, portfolioId);

            Client client = portfolioFileService.getPortfolio(portfolioId);

            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED",
                    "Portfolio must be opened first before deleting taxonomies");
            }

            name.abuchen.portfolio.model.Taxonomy taxonomy = TaxonomyManagementService.deleteTaxonomy(client, taxonomyId);
            client.markDirty();
            portfolioFileService.saveFile(portfolioId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("portfolioId", portfolioId);
            response.put("taxonomyId", taxonomyId);
            response.put("taxonomyName", taxonomy.getName());
            response.put("message", "Taxonomy deleted successfully");

            logger.info("Deleted taxonomy {} ({}) for portfolio {}", taxonomy.getName(), taxonomyId, portfolioId);

            return Response.ok(response).build();

        } catch (java.util.NoSuchElementException e) {
            logger.warn("Taxonomy not found: {} in portfolio: {}", taxonomyId, portfolioId);
            return createErrorResponse(Response.Status.NOT_FOUND,
                "Taxonomy not found",
                e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error deleting taxonomy {} for portfolio {}: {}",
                taxonomyId, portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                "Internal server error",
                e.getMessage());
        }
    }
    
    /**
     * Get taxonomy DCA (Dollar Cost Averaging) data for a specific date.
     * 
     * This endpoint calculates the portfolio's taxonomy actual and target proportions
     * as they were at a specific point in time. This is useful for DCA planning.
     * 
     * @param portfolioId The portfolio ID
     * @param taxonomyId The taxonomy ID
     * @param date The date to calculate for (ISO format: YYYY-MM-DD). Defaults to today if not provided.
     * @return Taxonomy DCA data with actual and target proportions for each child classification
     */
    @GET
    @Path("/{taxonomyId}/dca")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTaxonomyDca(@PathParam("portfolioId") String portfolioId,
                                   @PathParam("taxonomyId") String taxonomyId,
                                   @QueryParam("date") String date) {
        try {
            logger.info("Getting taxonomy DCA for taxonomy {} in portfolio {} at date {}", 
                taxonomyId, portfolioId, date);
            
            // Get the cached Client for this portfolio
            Client client = portfolioFileService.getPortfolio(portfolioId);
            
            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED", 
                    "Portfolio must be opened first before accessing taxonomy DCA");
            }
            
            // Find the taxonomy by ID
            name.abuchen.portfolio.model.Taxonomy taxonomy = client.getTaxonomies().stream()
                .filter(t -> taxonomyId.equals(t.getId()))
                .findFirst()
                .orElse(null);
            
            if (taxonomy == null) {
                logger.warn("Taxonomy not found: {} in portfolio: {}", taxonomyId, portfolioId);
                return createErrorResponse(Response.Status.NOT_FOUND, 
                    "Taxonomy not found", 
                    "Taxonomy with ID " + taxonomyId + " not found in portfolio");
            }
            
            // Parse date (default to today)
            LocalDate targetDate;
            if (date != null && !date.trim().isEmpty()) {
                try {
                    targetDate = LocalDate.parse(date);
                } catch (Exception e) {
                    logger.warn("Failed to parse date: {}", date, e);
                    return createErrorResponse(Response.Status.BAD_REQUEST,
                        "INVALID_DATE_FORMAT",
                        "Invalid date format. Use ISO format: YYYY-MM-DD");
                }
            } else {
                targetDate = LocalDate.now();
            }
            
            // Convert taxonomy to DTO using the shared conversion method
            TaxonomyDto taxonomyDto = convertTaxonomyToDto(taxonomy, client, targetDate);
            
            // Get root node to calculate total value
            TaxonomyNode root = buildTaxonomyModel(client, taxonomy, targetDate);
            Money totalValue = root.getActual();
            double totalValueDecimal = totalValue.getAmount() / name.abuchen.portfolio.money.Values.Amount.divider();
            
            // Build response using DTO structure
            Map<String, Object> response = new HashMap<>();
            response.put("portfolioId", portfolioId);
            response.put("taxonomyId", taxonomyId);
            response.put("taxonomyName", taxonomy.getName());
            response.put("date", targetDate.toString());
            response.put("currency", client.getBaseCurrency());
            response.put("totalValue", totalValueDecimal);
            response.put("totalValueFormatted", name.abuchen.portfolio.money.Values.Money.format(totalValue));
            
            // Use children directly from DTO
            if (taxonomyDto.getRoot() != null && taxonomyDto.getRoot().getChildren() != null) {
                response.put("children", taxonomyDto.getRoot().getChildren());
                response.put("childrenCount", taxonomyDto.getRoot().getChildren().size());
            } else {
                response.put("children", new ArrayList<>());
                response.put("childrenCount", 0);
            }
            
            logger.info("Returning taxonomy DCA data for taxonomy {} ({}) at date {}", 
                taxonomy.getName(), taxonomyId, targetDate);
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            logger.error("Unexpected error getting taxonomy DCA for taxonomy {} in portfolio {}: {}", 
                taxonomyId, portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, 
                "Internal server error", 
                e.getMessage());
        }
    }
    
    // ===== Helper Methods (Migrated from PortfolioFileService) =====
    
    /**
     * Helper method to build TaxonomyModel and get the root classification node.
     * This centralizes the creation of TaxonomyModel with optional date parameter.
     * 
     * @param client The client
     * @param taxonomy The taxonomy
     * @param date Optional date (null means use today)
     * @return The root TaxonomyNode from the model
     */
    private TaxonomyNode buildTaxonomyModel(Client client, name.abuchen.portfolio.model.Taxonomy taxonomy, LocalDate date) {
        ExchangeRateProviderFactory factory = new ExchangeRateProviderFactory(client);
        TaxonomyModel model = new TaxonomyModel(factory, client, taxonomy, date != null ? date : LocalDate.now());
        return model.getClassificationRootNode();
    }
    
    /**
     * Helper method to convert all taxonomies from client to DTOs.
     * Migrated from PortfolioFileService.loadTaxonomies()
     */
    private List<TaxonomyDto> convertTaxonomiesToDto(Client client) {
        List<TaxonomyDto> taxonomyDtos = new ArrayList<>();
        
        // Create factory for exchange rates
        ExchangeRateProviderFactory factory = new ExchangeRateProviderFactory(client);
        
        for (name.abuchen.portfolio.model.Taxonomy taxonomy : client.getTaxonomies()) {
            taxonomyDtos.add(convertTaxonomyToDto(taxonomy, client));
        }
        
        return taxonomyDtos;
    }
    
    /**
     * Helper method to convert a single Taxonomy to TaxonomyDto.
     * 
     * @param taxonomy The taxonomy to convert
     * @param client The client
     * @param date Optional date (null means use today)
     * @return The converted TaxonomyDto
     */
    private TaxonomyDto convertTaxonomyToDto(name.abuchen.portfolio.model.Taxonomy taxonomy, Client client, LocalDate date) {
        TaxonomyDto dto = new TaxonomyDto();
        dto.setId(taxonomy.getId());
        dto.setName(taxonomy.getName());
        dto.setSource(taxonomy.getSource());
        dto.setDimensions(taxonomy.getDimensions());
        dto.setClassificationsCount(taxonomy.getAllClassifications().size());
        dto.setHeight(taxonomy.getHeigth());
        
        // Build TaxonomyModel with optional date
        TaxonomyNode rootNode = buildTaxonomyModel(client, taxonomy, date);
        
        // Convert the root classification
        if (taxonomy.getRoot() != null) {
            dto.setRoot(convertClassification(taxonomy.getRoot(), rootNode, rootNode.getActual()));
        }
        
        return dto;
    }
    
    /**
     * Helper method to convert a single Taxonomy to TaxonomyDto (using today's date).
     */
    private TaxonomyDto convertTaxonomyToDto(name.abuchen.portfolio.model.Taxonomy taxonomy, Client client) {
        return convertTaxonomyToDto(taxonomy, client, null);
    }
    
    /**
     * Recursively converts a Classification to a ClassificationDto.
     * Migrated from PortfolioFileService.convertClassification()
     * 
     * @param classification The classification to convert
     * @param taxonomyNode The corresponding TaxonomyNode (used to calculate proportions)
     * @param rootTotalValue The root total value for calculating proportions
     * @return The converted ClassificationDto
     */
    private ClassificationDto convertClassification(name.abuchen.portfolio.model.Classification classification, 
                                                   TaxonomyNode taxonomyNode, Money rootTotalValue) {
        ClassificationDto dto = new ClassificationDto();
        dto.setId(classification.getId());
        dto.setName(classification.getName());
        dto.setDescription(classification.getNote());
        dto.setColor(classification.getColor());
        dto.setWeight(classification.getWeight() / name.abuchen.portfolio.money.Values.Weight.divider());
        dto.setRank(classification.getRank());
        dto.setKey(classification.getKey());
        
        // Calculate proportion (Actual %) for this classification relative to its parent
        if (taxonomyNode != null && taxonomyNode.getParent() != null) {
            TaxonomyNode parentNode = taxonomyNode.getParent();
            if (parentNode.getActual() != null && parentNode.getActual().getAmount() > 0 &&
                taxonomyNode.getActual() != null) {
                long parentActualAmount = parentNode.getActual().getAmount();
                long actualAmount = taxonomyNode.getActual().getAmount();
                double proportion = (double) actualAmount / (double) parentActualAmount;
                dto.setProportion(proportion);
            }
        }
        
        // Add actual and target values and proportions relative to root total
        if (taxonomyNode != null) {
            long rootTotal = rootTotalValue != null ? rootTotalValue.getAmount() : 0;
            
            if (taxonomyNode.getActual() != null) {
                double actualValue = taxonomyNode.getActual().getAmount() / name.abuchen.portfolio.money.Values.Amount.divider();
                dto.setActualValue(actualValue);
                
                if (rootTotal > 0) {
                    double actualProportion = (taxonomyNode.getActual().getAmount() / (double) rootTotal) * 100;
                    dto.setActualProportion(actualProportion);
                }
            }
            
            if (taxonomyNode.getTarget() != null) {
                double targetValue = taxonomyNode.getTarget().getAmount() / name.abuchen.portfolio.money.Values.Amount.divider();
                dto.setTargetValue(targetValue);
                
                if (rootTotal > 0) {
                    double targetProportion = (taxonomyNode.getTarget().getAmount() / (double) rootTotal) * 100;
                    dto.setTargetProportion(targetProportion);
                }
            }
        }
        
        // Get parent actual amount for calculating assignment proportions
        long parentActual = taxonomyNode != null && taxonomyNode.getActual() != null 
            ? taxonomyNode.getActual().getAmount() : 0;
        long rootTotal = rootTotalValue != null ? rootTotalValue.getAmount() : 0;
        
        // Convert assignments
        List<AssignmentDto> assignmentDtos = new ArrayList<>();
        for (name.abuchen.portfolio.model.Classification.Assignment assignment : classification.getAssignments()) {
            AssignmentDto assignmentDto = new AssignmentDto();
            assignmentDto.setInvestmentVehicleUuid(assignment.getInvestmentVehicle().getUUID());
            assignmentDto.setInvestmentVehicleName(assignment.getInvestmentVehicle().getName());
            assignmentDto.setWeight(assignment.getWeight() / name.abuchen.portfolio.money.Values.Weight.divider());
            assignmentDto.setRank(assignment.getRank());
            
            // Get assignment node to extract actual and target values
            TaxonomyNode assignmentNode = findAssignmentNode(taxonomyNode, assignment);
            if (assignmentNode != null) {
                // Calculate proportion (Actual %) relative to parent
                if (parentActual > 0 && assignmentNode.getActual() != null) {
                    long actual = assignmentNode.getActual().getAmount();
                    double proportion = (double) actual / (double) parentActual;
                    assignmentDto.setProportion(proportion);
                }
                
                // Add actual values
                if (assignmentNode.getActual() != null) {
                    double actualValue = assignmentNode.getActual().getAmount() / name.abuchen.portfolio.money.Values.Amount.divider();
                    assignmentDto.setActualValue(actualValue);
                    
                    if (rootTotal > 0) {
                        double actualProportion = (assignmentNode.getActual().getAmount() / (double) rootTotal) * 100;
                        assignmentDto.setActualProportion(actualProportion);
                    }
                }
                
                // Add target values (now set by RecalculateTargetsAttachedModel)
                if (assignmentNode.getTarget() != null) {
                    double targetValue = assignmentNode.getTarget().getAmount() / name.abuchen.portfolio.money.Values.Amount.divider();
                    assignmentDto.setTargetValue(targetValue);
                    
                    if (rootTotal > 0) {
                        double targetProportion = (assignmentNode.getTarget().getAmount() / (double) rootTotal) * 100;
                        assignmentDto.setTargetProportion(targetProportion);
                    }
                }
            }
            
            assignmentDtos.add(assignmentDto);
        }
        dto.setAssignments(assignmentDtos);
        
        // Recursively convert children
        List<ClassificationDto> childrenDtos = new ArrayList<>();
        for (name.abuchen.portfolio.model.Classification child : classification.getChildren()) {
            // Find the corresponding child node
            TaxonomyNode childNode = findChildNode(taxonomyNode, child);
            childrenDtos.add(convertClassification(child, childNode, rootTotalValue));
        }
        dto.setChildren(childrenDtos);
        
        return dto;
    }
    
    /**
     * Finds the assignment node that corresponds to the given assignment.
     * Migrated from PortfolioFileService.findAssignmentNode()
     * 
     * @param parentNode The parent taxonomy node
     * @param assignment The assignment to find
     * @return The matching TaxonomyNode or null if not found
     */
    private TaxonomyNode findAssignmentNode(TaxonomyNode parentNode, 
                                           name.abuchen.portfolio.model.Classification.Assignment assignment) {
        if (parentNode == null) return null;
        
        for (TaxonomyNode child : parentNode.getChildren()) {
            if (child.isAssignment() && 
                child.getAssignment().getInvestmentVehicle().equals(assignment.getInvestmentVehicle())) {
                return child;
            }
        }
        return null;
    }
    
    /**
     * Finds the child taxonomy node that corresponds to the given classification.
     * Migrated from PortfolioFileService.findChildNode()
     * 
     * @param parentNode The parent taxonomy node
     * @param classification The classification to find
     * @return The matching TaxonomyNode or null if not found
     */
    private TaxonomyNode findChildNode(TaxonomyNode parentNode, 
                                      name.abuchen.portfolio.model.Classification classification) {
        if (parentNode == null) return null;
        
        for (TaxonomyNode child : parentNode.getChildren()) {
            if (child.isClassification() && 
                child.getClassification().getId().equals(classification.getId())) {
                return child;
            }
        }
        return null;
    }
    
}

