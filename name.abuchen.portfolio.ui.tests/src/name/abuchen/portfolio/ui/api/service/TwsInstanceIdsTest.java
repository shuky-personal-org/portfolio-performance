package name.abuchen.portfolio.ui.api.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Set;

import org.junit.Test;

public class TwsInstanceIdsTest
{
    private static final Set<String> BUILT_IN = Set.of("default", "primary", "secondary");

    @Test
    public void unsetVariableAcceptsExactlyTheBuiltInIds()
    {
        assertThat(TwsInstanceIds.supported(null), is(BUILT_IN));
        assertThat(TwsInstanceIds.supported(""), is(BUILT_IN));
        assertThat(TwsInstanceIds.supported("  "), is(BUILT_IN));
    }

    @Test
    public void configuredIdsAreAddedToTheBuiltInOnes()
    {
        assertThat(TwsInstanceIds.supported("acct3"), is(Set.of("default", "primary", "secondary", "acct3")));
        assertThat(TwsInstanceIds.supported(" acct3 , acct4,,secondary "),
                        is(Set.of("default", "primary", "secondary", "acct3", "acct4")));
    }

    @Test
    public void builtInIdsCannotBeRemoved()
    {
        assertThat(TwsInstanceIds.supported("acct3").containsAll(BUILT_IN), is(true));
    }

    @Test
    public void invalidEntriesAreIgnored()
    {
        assertThat(TwsInstanceIds.supported("Acct3, my-account, acct 5, ../x, acct6"),
                        is(Set.of("default", "primary", "secondary", "acct6")));
    }

    @Test
    public void unknownIdsAreNotSupported()
    {
        assertThat(TwsInstanceIds.supported("acct3").contains("unknown"), is(false));
        assertThat(TwsInstanceIds.isSupported(null), is(false));
        assertThat(TwsInstanceIds.isSupported("default"), is(true));
    }
}
