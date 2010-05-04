/*******************************************************************************
 * Copyright (C) 2010, Jens Baumgart <jens.baumgart@sap.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core.op;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

/**
 * interface for EGit operations
 *
 */
public interface IEGitOperation {
	/**
	 * Executes the operation
	 * @param monitor
	 * @throws CoreException
	 */
	void execute(IProgressMonitor monitor) throws CoreException;
}