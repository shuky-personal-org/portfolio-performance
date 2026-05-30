package name.abuchen.portfolio.ui.api.service;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

import name.abuchen.portfolio.model.Classification;
import name.abuchen.portfolio.model.Classification.Assignment;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.InvestmentVehicle;
import name.abuchen.portfolio.model.Taxonomy;
import name.abuchen.portfolio.model.TaxonomyTemplate;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.ui.api.dto.AssignmentMutationDto;
import name.abuchen.portfolio.ui.api.dto.ClassificationMutationDto;
import name.abuchen.portfolio.ui.api.dto.TaxonomyMutationDto;

public final class TaxonomyManagementService
{
    private TaxonomyManagementService()
    {
    }

    public static Taxonomy createTaxonomy(Client client, TaxonomyMutationDto request)
    {
        Objects.requireNonNull(client, "client");
        requireRequest(request);

        var name = requireName(request.getName());
        Taxonomy taxonomy;

        if (request.getTemplateId() != null && !request.getTemplateId().isBlank())
        {
            var template = TaxonomyTemplate.byId(request.getTemplateId().trim());
            if (template == null)
                throw new IllegalArgumentException("Unknown taxonomy template: " + request.getTemplateId());

            taxonomy = template.build();
            taxonomy.setName(name);
            if (taxonomy.getRoot() != null)
                taxonomy.getRoot().setName(name);
        }
        else if (request.getRoot() != null)
        {
            taxonomy = new Taxonomy(name);
            taxonomy.setRootNode(buildClassification(client, null, request.getRoot(), name));
        }
        else
        {
            taxonomy = new Taxonomy(name);
            taxonomy.setRootNode(new Classification(UUID.randomUUID().toString(), name));
        }

        applyTaxonomyMetadata(taxonomy, request, true);
        client.addTaxonomy(taxonomy);
        return taxonomy;
    }

    public static Taxonomy updateTaxonomy(Client client, String taxonomyId, TaxonomyMutationDto request)
    {
        Objects.requireNonNull(client, "client");
        requireRequest(request);

        var taxonomy = findTaxonomy(client, taxonomyId);

        if (request.getName() != null)
        {
            var name = requireName(request.getName());
            taxonomy.setName(name);
            if (taxonomy.getRoot() != null)
                taxonomy.getRoot().setName(name);
        }

        applyTaxonomyMetadata(taxonomy, request, false);

        if (request.getRoot() != null)
        {
            taxonomy.setRootNode(buildClassification(client, null, request.getRoot(), taxonomy.getName()));
            if (taxonomy.getRoot() != null)
                taxonomy.getRoot().setName(taxonomy.getName());
        }

        return taxonomy;
    }

    public static Taxonomy deleteTaxonomy(Client client, String taxonomyId)
    {
        Objects.requireNonNull(client, "client");

        var taxonomy = findTaxonomy(client, taxonomyId);
        client.removeTaxonomy(taxonomy);
        return taxonomy;
    }

    public static Taxonomy findTaxonomy(Client client, String taxonomyId)
    {
        if (taxonomyId == null || taxonomyId.isBlank())
            throw new NoSuchElementException("Taxonomy ID is required");

        return client.getTaxonomies().stream() //
                        .filter(taxonomy -> taxonomyId.equals(taxonomy.getId())) //
                        .findFirst() //
                        .orElseThrow(() -> new NoSuchElementException(
                                        "Taxonomy with ID " + taxonomyId + " not found in portfolio"));
    }

    private static void applyTaxonomyMetadata(Taxonomy taxonomy, TaxonomyMutationDto request, boolean isCreate)
    {
        if (request.getSource() != null)
            taxonomy.setSource(request.getSource().trim().isEmpty() ? null : request.getSource().trim());

        if (request.getDimensions() != null)
            taxonomy.setDimensions(new ArrayList<>(request.getDimensions()));
        else if (isCreate && taxonomy.getDimensions() == null)
            taxonomy.setDimensions(List.of());
    }

    private static Classification buildClassification(Client client, Classification parent,
                    ClassificationMutationDto dto, String defaultRootName)
    {
        var name = dto.getName() != null && !dto.getName().isBlank() ? dto.getName().trim() : defaultRootName;
        var id = dto.getId() != null && !dto.getId().isBlank() ? dto.getId().trim() : UUID.randomUUID().toString();

        Classification classification = parent == null ? new Classification(id, name)
                        : new Classification(parent, id, name, dto.getColor());

        if (parent == null && dto.getColor() != null && !dto.getColor().isBlank())
            classification.setColor(dto.getColor());

        if (dto.getDescription() != null)
            classification.setNote(dto.getDescription());

        if (dto.getKey() != null)
            classification.setKey(dto.getKey());

        if (dto.getWeight() != null)
            classification.setWeight(toWeight(dto.getWeight()));

        if (dto.getRank() != null)
            classification.setRank(dto.getRank());

        if (dto.getAssignments() != null)
        {
            for (var assignmentDto : dto.getAssignments())
            {
                var assignment = buildAssignment(client, assignmentDto);
                classification.addAssignment(assignment);
            }
        }

        if (dto.getChildren() != null)
        {
            for (var childDto : dto.getChildren())
            {
                var child = buildClassification(client, classification, childDto, name);
                classification.addChild(child);
            }
        }

        return classification;
    }

    private static Assignment buildAssignment(Client client, AssignmentMutationDto dto)
    {
        if (dto.getInvestmentVehicleUuid() == null || dto.getInvestmentVehicleUuid().isBlank())
            throw new IllegalArgumentException("Assignment investmentVehicleUuid is required");

        var vehicle = findInvestmentVehicle(client, dto.getInvestmentVehicleUuid().trim());
        var weight = dto.getWeight() != null ? toWeight(dto.getWeight()) : Classification.ONE_HUNDRED_PERCENT;
        var assignment = new Assignment(vehicle, weight);

        if (dto.getRank() != null)
            assignment.setRank(dto.getRank());

        return assignment;
    }

    private static InvestmentVehicle findInvestmentVehicle(Client client, String uuid)
    {
        return client.getSecurities().stream() //
                        .filter(security -> uuid.equals(security.getUUID())) //
                        .map(security -> (InvestmentVehicle) security) //
                        .findFirst() //
                        .orElseGet(() -> client.getAccounts().stream() //
                                        .filter(account -> uuid.equals(account.getUUID())) //
                                        .map(account -> (InvestmentVehicle) account) //
                                        .findFirst() //
                                        .orElseThrow(() -> new NoSuchElementException(
                                                        "Investment vehicle with UUID " + uuid
                                                                        + " not found in portfolio")));
    }

    private static int toWeight(double weight)
    {
        return (int) Math.round(weight * Values.Weight.divider());
    }

    private static void requireRequest(TaxonomyMutationDto request)
    {
        if (request == null)
            throw new IllegalArgumentException("Request body is required");
    }

    private static String requireName(String name)
    {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Taxonomy name is required");

        return name.trim();
    }
}
