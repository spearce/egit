/*******************************************************************************
 * Copyright (c) 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Stefan Lay (SAP AG) - initial implementation
 *******************************************************************************/

package org.eclipse.egit.ui.internal.actions;

import java.io.IOException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.core.op.MergeOperation;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.internal.dialogs.MergeTargetSelectionDialog;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.osgi.util.NLS;
import org.eclipse.team.internal.ui.Utils;

/**
 * Action for selecting a commit and merging it with the current branch.
 */
public class MergeAction extends RepositoryAction {

	@Override
	public void execute(IAction action) {
		final Repository repository = getRepository(true);
		if (repository == null)
			return;

		if (!canMerge(repository))
			return;

		MergeTargetSelectionDialog mergeTargetSelectionDialog = new MergeTargetSelectionDialog(
				getShell(), repository);
		if (mergeTargetSelectionDialog.open() == IDialogConstants.OK_ID) {

			final String refName = mergeTargetSelectionDialog.getRefName();

			String jobname = NLS.bind(UIText.MergeAction_JobNameMerge, refName);
			Job job = new Job(jobname) {
				@Override
				protected IStatus run(IProgressMonitor monitor) {
					try {
						new MergeOperation(repository, refName).execute(monitor);
					} catch (final CoreException e) {
						getShell().getDisplay().asyncExec(new Runnable(){
							public void run() {
								Utils.handleError(getShell(), e, "Merge impossible", "Unsupported Operation"); //$NON-NLS-1$ //$NON-NLS-2$
							}});
					}
					return Status.OK_STATUS;
				}
			};
			job.setUser(true);
			job.schedule();

		}

	}

	private boolean canMerge(final Repository repository) {
		String message = null;
		try {
			Ref head = repository.getRef(Constants.HEAD);
			if (head == null || !head.isSymbolic())
				message = UIText.MergeAction_HeadIsNoBranch;
			else if (!repository.getRepositoryState().equals(
					RepositoryState.SAFE))
				message = NLS.bind(
						UIText.MergeAction_WrongRepositoryState,
						repository.getRepositoryState());
		} catch (IOException e) {
			Activator.logError(e.getMessage(), e);
			message = e.getMessage();
		}

		if (message != null) {
			MessageDialog.openError(getShell(),
					UIText.MergeAction_CannotMerge, message);
		}
		return (message == null);
	}

	@Override
	public boolean isEnabled() {
		boolean enabled = true;
		Repository repository = getRepository(false);
		if (repository == null)
			enabled = false;
		return enabled;
	}

}
