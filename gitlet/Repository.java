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

    /** Init command */
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

        // 1. Create the Genesis Commit
        Commit initialCommit = new Commit("initial commit", null, new TreeMap<>());
        
        // 2. Persist the commit
        String commitHash = sha1((Object) serialize(initialCommit));
        File commitFile = join(OBJECTS_DIR, commitHash);
        writeObject(commitFile, initialCommit);

        // 3. Create 'master' branch head
        File masterBranch = join(HEADS_DIR, "master");
        writeContents(masterBranch, commitHash);

        // 4. Create HEAD pointing to master branch
        File head = join(GITLET_DIR, "HEAD");
        writeContents(head, "ref: refs/heads/master");
    }
    
    /** Add command */
    public static void add(String fileName) {
        File file = join(CWD, fileName);
        if (!file.exists()) {
            System.out.println("File does not exist.");
            return;
        }

        // 1. Create a Blob of the current file
        byte[] contents = readContents(file);
        String blobHash = sha1((Object) contents);
    
        // 2. Load the current commit (HEAD) and the current staging area
        Commit head = getHeadCommit();
        Stage stagingArea = Stage.load();

        // 3. If file matches HEAD, remove from staging
        if (blobHash.equals(head.getSnapshots().get(fileName))) {
            stagingArea.remove(fileName);
        } else {
            // Create the blob file in objects folder
            Utils.writeContents(Utils.join(OBJECTS_DIR, blobHash), contents);
            stagingArea.add(fileName, blobHash);
        }
        stagingArea.save();
    }

    /** Commit command */
    public static void commit(String message) {
        if (message.isEmpty()) {
            System.out.println("Please enter a commit message.");
            return;
        }

        Stage stage = Stage.load();
        if (stage.isClean()) {
            System.out.println("No changes added to the commit.");
            return;
        }

        Commit head = getHeadCommit();
        // Copy the parent's snapshots as a starting point
        TreeMap<String, String> newSnapshots = new TreeMap<>(head.getSnapshots());
        
        // Add/Update files from the staging area
        newSnapshots.putAll(stage.getAddedFiles());
        
        // Create the new commit
        String parentHash = getHeadHash();
        Commit newCommit = new Commit(message, parentHash, newSnapshots);
        
        // Save the commit
        newCommit.save();
        
        // Move the branch pointer (e.g., refs/heads/master) to this new commit
        updateBranchPointer(newCommit.getHash());
        
        // Clear the stage for the next round
        stage.clear();
        stage.save();
    }

    /** Helper method to get the head */
    public static Commit getHeadCommit() {
        // 1. Read the HEAD file to see which branch we are on
        // Contents of HEAD: "ref: refs/heads/master"
        String headContent = Utils.readContentsAsString(Utils.join(GITLET_DIR, "HEAD"));
        
        // 2. Extract the path to the branch file
        String branchPath = headContent.replace("ref: ", "");
        
        // 3. Read the Branch file to get the SHA-1 of the latest commit
        String headHash = Utils.readContentsAsString(Utils.join(GITLET_DIR, branchPath));
        
        // 4. Load the Commit object from the objects folder
        File commitFile = Utils.join(OBJECTS_DIR, headHash);
        return Utils.readObject(commitFile, Commit.class);
    }

    /** Returns the SHA-1 hash of the current HEAD commit */
    private static String getHeadHash() {
        File headFile = Utils.join(GITLET_DIR, "HEAD");
        String headContent = Utils.readContentsAsString(headFile);
        
        if (headContent.startsWith("ref: ")) {
            // We are on a branch (normal state)
            File branchFile = Utils.join(GITLET_DIR, headContent.replace("ref: ", ""));
            return Utils.readContentsAsString(branchFile);
        } else {
            // We are in 'detached HEAD' state (hash is directly in HEAD)
            return headContent;
        }
    }

    /** Moves the current branch pointer to the new commit hash */
    private static void updateBranchPointer(String newCommitHash) {
        File headFile = Utils.join(GITLET_DIR, "HEAD");
        String headContent = Utils.readContentsAsString(headFile);
        
        if (headContent.startsWith("ref: ")) {
            File branchFile = Utils.join(GITLET_DIR, headContent.replace("ref: ", ""));
            Utils.writeContents(branchFile, newCommitHash);
        } else {
            // Update HEAD directly if detached
            Utils.writeContents(headFile, newCommitHash);
        }
    }
}
