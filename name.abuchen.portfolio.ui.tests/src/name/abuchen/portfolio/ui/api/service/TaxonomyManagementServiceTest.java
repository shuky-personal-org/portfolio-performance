package name.abuchen.portfolio.ui.api.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;

import org.junit.Test;

import name.abuchen.portfolio.model.Classification;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.Taxonomy;
import name.abuchen.portfolio.money.CurrencyUnit;
import name.abuchen.portfolio.ui.api.dto.AssignmentMutationDto;
import name.abuchen.portfolio.ui.api.dto.ClassificationMutationDto;
import name.abuchen.portfolio.ui.api.dto.TaxonomyMutationDto;

public class TaxonomyManagementServiceTest
{
    @Test
    public void createsEmptyTaxonomyWithRootClassification()
    {
        var client = new Client();
        var request = new TaxonomyMutationDto();
        request.setName("  Asset Allocation  ");

        var taxonomy = TaxonomyManagementService.createTaxonomy(client, request);

        assertThat(client.getTaxonomies(), hasSize(1));
        assertThat(taxonomy.getName(), is("Asset Allocation"));
        assertThat(taxonomy.getRoot(), notNullValue());
        assertThat(taxonomy.getRoot().getName(), is("Asset Allocation"));
        assertThat(taxonomy.getDimensions(), hasSize(0));
    }

    @Test
    public void createsTaxonomyFromTemplate()
    {
        var client = new Client();
        var request = new TaxonomyMutationDto();
        request.setName("My Asset Classes");
        request.setTemplateId("assetclasses");

        var taxonomy = TaxonomyManagementService.createTaxonomy(client, request);

        assertThat(taxonomy.getName(), is("My Asset Classes"));
        assertThat(taxonomy.getRoot().getChildren().size(), greaterThan(0));
        assertThat(taxonomy.getDimensions(), notNullValue());
    }

    @Test
    public void createsTaxonomyWithCustomClassificationsAndAssignments()
    {
        var client = new Client();
        var security = new Security("Apple", CurrencyUnit.EUR);
        client.addSecurity(security);

        var equities = new ClassificationMutationDto();
        equities.setName("Equities");
        equities.setWeight(60.0);
        equities.setColor("#ff0000");

        var assignment = new AssignmentMutationDto();
        assignment.setInvestmentVehicleUuid(security.getUUID());
        assignment.setWeight(100.0);
        equities.setAssignments(List.of(assignment));

        var root = new ClassificationMutationDto();
        root.setName("Allocation");
        root.setChildren(List.of(equities));

        var request = new TaxonomyMutationDto();
        request.setName("Custom");
        request.setSource("api");
        request.setDimensions(List.of("Type"));
        request.setRoot(root);

        var taxonomy = TaxonomyManagementService.createTaxonomy(client, request);

        assertThat(taxonomy.getSource(), is("api"));
        assertThat(taxonomy.getDimensions(), hasSize(1));
        assertThat(taxonomy.getRoot().getChildren(), hasSize(1));
        assertThat(taxonomy.getRoot().getChildren().get(0).getAssignments(), hasSize(1));
        assertThat(taxonomy.getRoot().getChildren().get(0).getAssignments().get(0).getInvestmentVehicle(),
                        is(security));
    }

    @Test
    public void updatesTaxonomyNameAndReplacesRootTree()
    {
        var client = new Client();
        var taxonomy = new Taxonomy("Old Name");
        taxonomy.setRootNode(new Classification(java.util.UUID.randomUUID().toString(), "Old Name"));
        client.addTaxonomy(taxonomy);

        var child = new ClassificationMutationDto();
        child.setName("Bonds");
        child.setWeight(40.0);

        var root = new ClassificationMutationDto();
        root.setName("New Root");
        root.setChildren(List.of(child));

        var request = new TaxonomyMutationDto();
        request.setName("New Name");
        request.setRoot(root);

        var updated = TaxonomyManagementService.updateTaxonomy(client, taxonomy.getId(), request);

        assertThat(updated, is(taxonomy));
        assertThat(taxonomy.getName(), is("New Name"));
        assertThat(taxonomy.getRoot().getName(), is("New Name"));
        assertThat(taxonomy.getRoot().getChildren(), hasSize(1));
        assertThat(taxonomy.getRoot().getChildren().get(0).getName(), is("Bonds"));
    }

    @Test
    public void deletesTaxonomyFromClient()
    {
        var client = new Client();
        var taxonomy = new Taxonomy("To Delete");
        taxonomy.setRootNode(new Classification(java.util.UUID.randomUUID().toString(), "To Delete"));
        client.addTaxonomy(taxonomy);

        var deleted = TaxonomyManagementService.deleteTaxonomy(client, taxonomy.getId());

        assertThat(deleted, is(taxonomy));
        assertThat(client.getTaxonomies(), hasSize(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownTemplate()
    {
        var client = new Client();
        var request = new TaxonomyMutationDto();
        request.setName("Test");
        request.setTemplateId("does-not-exist");

        TaxonomyManagementService.createTaxonomy(client, request);
    }
}
