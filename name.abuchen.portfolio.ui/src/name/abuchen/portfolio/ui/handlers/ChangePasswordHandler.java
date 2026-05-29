package name.abuchen.portfolio.ui.handlers;

import jakarta.inject.Named;

import org.eclipse.e4.core.di.annotations.CanExecute;
import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.services.IServiceConstants;
import org.eclipse.swt.widgets.Shell;

import name.abuchen.portfolio.model.ClientFactory;
import name.abuchen.portfolio.ui.editor.ClientInput;

public class ChangePasswordHandler
{
    @CanExecute
    boolean isVisible(@Named(IServiceConstants.ACTIVE_PART) MPart part)
    {
        return MenuHelper.getActiveClientInput(part, false) //
                        .map(ClientInput::getFile) //
                        .filter(ClientFactory::isEncrypted) //
                        .isPresent();
    }

    @Execute
    public void execute(@Named(IServiceConstants.ACTIVE_PART) MPart part,
                    @Named(IServiceConstants.ACTIVE_SHELL) Shell shell)
    {
        MenuHelper.getActivePortfolioPart(part, true).ifPresent(p -> {
            var file = p.getClientInput().getFile();
            if (file != null && ClientFactory.isEncrypted(file))
                p.doChangePassword(shell);
        });
    }
}
