package gitlet;

import java.io.File;
import static gitlet.Utils.*;
import java.util.TreeMap;

// TODO: any imports you need here

/** Represents a gitlet repository.
 *  TODO: It's a good idea to give a description here of what else this Class
 *  does at a high level.
 *
 *  @author TODO
 */
public class Repository {
    /**
     * TODO: add instance variables here.
     *
     * List all instance variables of the Repository class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided two examples for you.
     */

    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    /** Directory for all objects (Commits/Blobs) */
    public static final File OBJECTS_DIR = join(GITLET_DIR, "objects");
    /** Directory for branch heads */
    public static final File HEADS_DIR = join(GITLET_DIR, "refs", "heads");

    /* TODO: fill in the rest of this class. */
    public static void init() {
        if (GITLET_DIR.exists()) {
            System.out.println("A Gitlet version-control system already exists in the current directory.");
            return;
        }

        // Create directory structure
        GITLET_DIR.mkdir();
        OBJECTS_DIR.mkdir();
        join(GITLET_DIR, "refs").mkdir();
        HEADS_DIR.mkdir();

        // 1. Create the Genesis Commit (No files, Unix Epoch time)
        Commit initialCommit = new Commit("initial commit", null, new TreeMap<>());
        
        // 2. Persist the commit using Utils.writeObject
        String commitHash = sha1(serialize(initialCommit));
        File commitFile = join(OBJECTS_DIR, commitHash);
        writeObject(commitFile, initialCommit);

        // 3. Create 'master' branch head
        File masterBranch = join(HEADS_DIR, "master");
        writeContents(masterBranch, commitHash);

        // 4. Create HEAD pointing to master branch
        File head = join(GITLET_DIR, "HEAD");
        writeContents(head, "ref: refs/heads/master");
    }
}
