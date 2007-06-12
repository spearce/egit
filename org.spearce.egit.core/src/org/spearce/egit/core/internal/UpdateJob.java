/*
 *  Copyrighy (C) 2007  Robin Rosenberg
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License, version 2.1, as published by the Free Software Foundation.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 */
package org.spearce.egit.core.internal;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.spearce.egit.core.Activator;
import org.spearce.egit.core.CoreText;
import org.spearce.egit.core.project.GitProjectData;
import org.spearce.egit.core.project.RepositoryMapping;
import org.spearce.jgit.lib.GitIndex;
import org.spearce.jgit.lib.GitIndex.Entry;

public class UpdateJob extends Job {

	private final Collection rsrcList;

	public UpdateJob(Collection rsrcList) {
		super("Update index");
		this.rsrcList = rsrcList;
		setPriority(Job.LONG);
	}

	protected IStatus run(IProgressMonitor m) {
		if (m == null) {
			m = new NullProgressMonitor();
		}

		trace("running");
		try {
			final IdentityHashMap<RepositoryMapping, Boolean> tomerge = new IdentityHashMap<RepositoryMapping, Boolean>();
			try {
				final int[] count=new int[1];
				long t0=System.currentTimeMillis();
				for (Object obj : rsrcList) {
					if (obj instanceof IContainer) {
						((IContainer)obj).accept(new IResourceProxyVisitor() {
							public boolean visit(IResourceProxy rp) throws CoreException {
								if (rp.getType() == IResource.FILE) {
									count[0]++;
								}
								return true;
							}
						}, IContainer.EXCLUDE_DERIVED);
					} else if (obj instanceof IResource) {
							count[0]++;
					}
				}
				long t1=System.currentTimeMillis();
				System.out.println("Counted "+count[0]+" items to update in "+(t1-t0)/1000.0+"s");
				m.beginTask(CoreText.UpdateOperation_updating, count[0]);
				final IProgressMonitor fm = m;
				for (Object obj : rsrcList) {
					if (obj instanceof IResource) {
						final IResource r = (IResource)obj;
						final IProject p = r.getProject();					
						final GitProjectData pd = GitProjectData.get(r.getProject());
						final RepositoryMapping rm = pd.getRepositoryMapping(p);
						final GitIndex index = rm.getRepository().getIndex();
						tomerge.put(rm, Boolean.TRUE);
						String prefix = rm.getSubset();
						if (prefix == null)
							prefix = "";
						else
							prefix = prefix + "/";
						final String fprefix = prefix;
						if (r instanceof IContainer) {
							((IContainer)r).accept(new IResourceVisitor() {
								public boolean visit(IResource resource) throws CoreException {
									try {
										if (resource.getType() == IResource.FILE) {
											String path = fprefix + resource.getProjectRelativePath();
											Entry entry = index.getEntry(path);
											if (entry != null) {
												entry.update(new File(rm.getWorkDir(),path), rm.getRepository());
											}
											fm.worked(1);
										}
									} catch (IOException e) {
										e.printStackTrace();
										throw Activator.error(CoreText.UpdateOperation_failed, e);
									}
									return true;
								}
							},IResource.DEPTH_INFINITE, IContainer.EXCLUDE_DERIVED);
						} else {
							String path = fprefix + r.getProjectRelativePath();
							Entry entry = index.getEntry(path);
							if (entry != null) {
								entry.update(new File(rm.getWorkDir(),path), rm.getRepository());
							}
							m.worked(1);
						}
					}
				}
				for (RepositoryMapping rm : tomerge.keySet()) {
					m.setTaskName("Writing index for "+rm.getRepository().getDirectory());
					rm.getRepository().getIndex().write();
				}
			} catch (RuntimeException e) {
				e.printStackTrace();
				return Activator.error(CoreText.UpdateOperation_failed, e).getStatus();
			} catch (IOException e) {
				e.printStackTrace();
				return Activator.error(CoreText.UpdateOperation_failed, e).getStatus();
			} catch (CoreException e) {
				e.printStackTrace();
				return Activator.error(CoreText.UpdateOperation_failed, e).getStatus();
			} finally {
				try {
					final Iterator i = tomerge.keySet().iterator();
					while (i.hasNext()) {
						final RepositoryMapping r = (RepositoryMapping) i.next();
						r.getRepository().getIndex().read();
						r.recomputeMerge();
					}
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					m.done();
				}
			}
		} finally {
			trace("done");
			m.done();
		}

		return Status.OK_STATUS;
	}

	private void trace(final String m) {
		Activator.trace("(UpdateJob)"+m);
	}

}