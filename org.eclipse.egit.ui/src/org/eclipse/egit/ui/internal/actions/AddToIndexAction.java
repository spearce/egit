/*******************************************************************************
 * Copyright (C) 2010, Jens Baumgart <jens.baumgart@sap.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.actions;

import java.util.Collection;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.egit.core.op.AddToIndexOperation;
import org.eclipse.egit.core.op.IEGitOperation;
import org.eclipse.egit.ui.UIText;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;

/**
 * An action to add files to a Git index.
 *
 * @see AddToIndexOperation
 */
public class AddToIndexAction extends AbstractResourceOperationAction {
	private AddToIndexOperation operation = null;

	protected IEGitOperation createOperation(final List<IResource> sel) {
		if (sel.isEmpty()) {
			return null;
		} else {
			operation = new AddToIndexOperation(sel);
			return operation;
		}
	}

	@Override
	protected void postOperation() {
		Collection<IFile> notAddedFiles = operation.getNotAddedFiles();
		if (notAddedFiles.size()==0)
			return;
		final String title = UIText.AddToIndexAction_addingFilesFailed;
		String message = UIText.AddToIndexAction_indexesWithUnmergedEntries;
		message += "\n\n";  //$NON-NLS-1$
		message += getFileList(notAddedFiles);
		final String fMessage = message;
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				MessageDialog.openWarning(wp.getSite().getShell(), title, fMessage);
			}
		});

	}

	private static String getFileList(Collection<IFile> notAddedFiles) {
		String result = "";  //$NON-NLS-1$
		for (IFile file : notAddedFiles) {
			result += file.getName();
			result += "\n"; //$NON-NLS-1$
		}
		return result;
	}

	@Override
	protected String getJobName() {
		return UIText.AddToIndexAction_addingFiles;
	}


}
