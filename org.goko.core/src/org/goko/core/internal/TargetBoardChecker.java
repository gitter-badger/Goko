/**
 * 
 */
package org.goko.core.internal;

import java.util.List;

import javax.inject.Inject;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.ui.internal.workbench.swt.PartRenderingEngine;
import org.eclipse.e4.ui.workbench.lifecycle.PostContextCreate;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.goko.core.common.exception.GkException;
import org.goko.core.config.GokoPreference;
import org.goko.core.feature.IFeatureSetManager;
import org.goko.core.feature.TargetBoard;

/**
 * @author PsyKo
 *
 */
public class TargetBoardChecker {
	@Inject
	IFeatureSetManager featureSetManager;

	@PostContextCreate
	public void startup(IEclipseContext context) throws GkException {
		String targetBoard = GokoPreference.getInstance().getTargetBoard();
		if (StringUtils.isEmpty(targetBoard)
				|| !featureSetManager.existTargetBoard(targetBoard)) {
			List<TargetBoard> lstSupportedBoard = featureSetManager.getSupportedBoards();
			if(CollectionUtils.size(lstSupportedBoard) == 1){
				GokoPreference.getInstance().setTargetBoard(lstSupportedBoard.get(0).getId());
			}else{
				openTargetBoardSelection(context);	
			}
			
		}		
	}

	private void openTargetBoardSelection(IEclipseContext context) {
		final Shell shell = new Shell(SWT.INHERIT_NONE);

		final TargetBoardSelectionDialog dialog = new TargetBoardSelectionDialog(shell);
		dialog.setLstTargetBoard(featureSetManager.getSupportedBoards());
		dialog.create();
		
		PartRenderingEngine.initializeStyling(shell.getDisplay(), context);

		
		if (dialog.open() != Window.OK) {
			System.exit(0);
		}else{
			GokoPreference.getInstance().setTargetBoard(dialog.getTargetBoard());
		}
	}
}
