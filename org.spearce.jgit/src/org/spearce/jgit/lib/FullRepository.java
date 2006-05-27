package org.spearce.jgit.lib;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FullRepository implements Repository {
    private static final String[] refSearchPaths = { "", "refs/", "refs/tags/",
            "refs/heads/", };

    private final File gitDir;

    private final File objectsDir;

    private final File refsDir;

    private final List packs;

    public FullRepository(final File d) {
        gitDir = d.getAbsoluteFile();
        objectsDir = new File(gitDir, "objects");
        refsDir = new File(gitDir, "refs");
        packs = new ArrayList();
        if (objectsDir.exists()) {
            scanForPacks();
        }
    }

    public void create() throws IOException {
        final FileWriter cfg;

        if (gitDir.exists()) {
            throw new IllegalStateException("Repository already exists: "
                    + gitDir);
        }

        gitDir.mkdirs();

        objectsDir.mkdirs();
        new File(objectsDir, "pack").mkdir();
        new File(objectsDir, "info").mkdir();

        refsDir.mkdir();
        new File(refsDir, "heads").mkdir();
        new File(refsDir, "tags").mkdir();

        new File(gitDir, "branches").mkdir();
        new File(gitDir, "remotes").mkdir();
        writeSymref("HEAD", "refs/heads/master");

        // TODO: Implement a real config file reader/writer
        cfg = new FileWriter(new File(gitDir, "config"));
        try {
            cfg.write("[core]\n");
            cfg.write("\trepositoryformatversion = 0\n");
            cfg.write("\tfilemode = true\n");
        } finally {
            cfg.close();
        }
    }

    public File getDirectory() {
        return gitDir;
    }

    public File getObjectsDirectory() {
        return objectsDir;
    }

    public File toFile(final ObjectId objectId) {
        final String n = objectId.toString();
        return new File(new File(objectsDir, n.substring(0, 2)), n.substring(2));
    }

    public boolean hasObject(final ObjectId objectId) {
        if (toFile(objectId).isFile()) {
            return true;
        }

        final Iterator i = packs.iterator();
        while (i.hasNext()) {
            final PackReader p = (PackReader) i.next();
            try {
                final ObjectReader o = p.get(objectId);
                if (o != null) {
                    o.close();
                    return true;
                }
            } catch (IOException ioe) {
                // This shouldn't happen unless the pack was corrupted after we
                // opened it. We'll ignore the error as though the object does
                // not exist in this pack.
                //
            }
        }
        return false;
    }

    public ObjectReader openObject(final ObjectId id) throws IOException {
        final InputStream fis = openObjectStream(id);
        if (fis != null) {
            try {
                return new UnpackedObjectReader(id, fis);
            } catch (IOException ioe) {
                fis.close();
                throw ioe;
            }
        } else {
            return objectInPack(id);
        }
    }

    public ObjectReader openBlob(final ObjectId id) throws IOException {
        final ObjectReader or = openObject(id);
        if (or == null) {
            return null;
        } else if (Constants.TYPE_BLOB.equals(or.getType())) {
            return or;
        } else {
            or.close();
            throw new CorruptObjectException(id, "not a blob");
        }
    }

    public ObjectReader openTree(final ObjectId id) throws IOException {
        final ObjectReader or = openObject(id);
        if (or == null) {
            return null;
        } else if (Constants.TYPE_TREE.equals(or.getType())) {
            return or;
        } else {
            or.close();
            throw new CorruptObjectException(id, "not a tree");
        }
    }

    public Commit mapCommit(final ObjectId id) throws IOException {
        final ObjectReader or = openObject(id);
        if (or == null) {
            return null;
        } else if (Constants.TYPE_COMMIT.equals(or.getType())) {
            return new Commit(this, id, or.getBufferedReader());
        } else {
            or.close();
            throw new CorruptObjectException(id, "not a commit");
        }
    }

    public Tree mapTree(final ObjectId id) throws IOException {
        final ObjectReader or = openObject(id);
        if (or == null) {
            return null;
        } else if (Constants.TYPE_TREE.equals(or.getType())) {
            return new Tree(this, id, or.getInputStream());
        } else if (Constants.TYPE_COMMIT.equals(or.getType())) {
            return new Commit(this, id, or.getBufferedReader()).getTree();
        } else {
            or.close();
            throw new CorruptObjectException(id, "not a tree-ish");
        }
    }

    public ObjectId resolveRevision(final String r) throws IOException {
        ObjectId id = null;

        if (ObjectId.isId(r)) {
            id = new ObjectId(r);
        }
        if (id == null) {
            for (int k = 0; k < refSearchPaths.length; k++) {
                id = readRef(refSearchPaths[k] + r);
                if (id != null) {
                    break;
                }
            }
        }
        return id;
    }

    public void close() throws IOException {
        closePacks();
    }

    public void closePacks() throws IOException {
        final Iterator i = packs.iterator();
        while (i.hasNext()) {
            final PackReader pr = (PackReader) i.next();
            pr.close();
        }
        packs.clear();
    }

    public void scanForPacks() {
        final File packDir = new File(objectsDir, "pack");
        final File[] list = packDir.listFiles(new FileFilter() {
            public boolean accept(final File f) {
                final String n = f.getName();
                if (!n.endsWith(".pack")) {
                    return false;
                }
                final String nBase = n.substring(0, n.lastIndexOf('.'));
                final File idx = new File(packDir, nBase + ".idx");
                return f.isFile() && f.canRead() && idx.isFile()
                        && idx.canRead();
            }
        });
        for (int k = 0; k < list.length; k++) {
            try {
                packs.add(new PackReader(this, list[k]));
            } catch (IOException ioe) {
                // Whoops. That's not a pack!
                //
            }
        }
    }

    private InputStream openObjectStream(final ObjectId objectId)
            throws IOException {
        try {
            return new FileInputStream(toFile(objectId));
        } catch (FileNotFoundException fnfe) {
            return null;
        }
    }

    private ObjectReader objectInPack(final ObjectId objectId) {
        final Iterator i = packs.iterator();
        while (i.hasNext()) {
            final PackReader p = (PackReader) i.next();
            try {
                final ObjectReader o = p.get(objectId);
                if (o != null) {
                    return o;
                }
            } catch (IOException ioe) {
                // This shouldn't happen unless the pack was corrupted after we
                // opened it. We'll ignore the error as though the object does
                // not exist in this pack.
                //
            }
        }
        return null;
    }

    private void writeSymref(final String name, final String target)
            throws IOException {
        final File s = new File(gitDir, name);
        final File t = File.createTempFile("srf", null, gitDir);
        FileWriter w = new FileWriter(t);
        try {
            w.write("ref: ");
            w.write(target);
            w.write('\n');
            w.close();
            w = null;
            if (!t.renameTo(s)) {
                s.getParentFile().mkdirs();
                if (!t.renameTo(s)) {
                    t.delete();
                    throw new WritingNotSupportedException("Unable to"
                            + " write symref " + name + " to point to "
                            + target);
                }
            }
        } finally {
            if (w != null) {
                w.close();
                t.delete();
            }
        }
    }

    private ObjectId readRef(final String name) throws IOException {
        final File f = new File(gitDir, name);
        if (!f.isFile()) {
            return null;
        }
        final BufferedReader fr = new BufferedReader(new FileReader(f));
        try {
            final String line = fr.readLine();
            if (line == null || line.length() == 0) {
                return null;
            }
            if (line.startsWith("ref: ")) {
                return readRef(line.substring("ref: ".length()));
            }
            if (ObjectId.isId(line)) {
                return new ObjectId(line);
            }
            throw new IOException("Not a ref: " + name + ": " + line);
        } finally {
            fr.close();
        }
    }

    public String toString() {
        return "FullRepository[" + getDirectory() + "]";
    }
}