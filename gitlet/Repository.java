package gitlet;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;

import static gitlet.Utils.join;
import static gitlet.Utils.plainFilenamesIn;
import static gitlet.Utils.readContents;
import static gitlet.Utils.readContentsAsString;
import static gitlet.Utils.readObject;
import static gitlet.Utils.serialize;
import static gitlet.Utils.sha1;
import static gitlet.Utils.writeContents;
import static gitlet.Utils.writeObject;

/** Represents a gitlet repository.
 *  TODO: It's a good idea to give a description here of what else this Class
 *  does at a high level.
 *
 *  @author Jiehao Guan
 */
public class Repository {

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
            System.exit(0);
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
            stagingArea.removeFromAddition(fileName);
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

    /** Remove command */
    public static void rm(String fileName) {
        Stage stage = Stage.load();
        Commit head = getHeadCommit();
        boolean isStaged = stage.getAddedFiles().containsKey(fileName);
        boolean isTracked = head.getSnapshots().containsKey(fileName);

        if (!isStaged && !isTracked) {
            System.out.println("No reason to remove the file.");
            return;
        }

        // 1. Unstage if it's currently staged for addition
        if (isStaged) {
            stage.removeFromAddition(fileName);
        }

        // 2. If tracked in the current commit, stage for removal
        if (isTracked) {
            stage.stageForRemoval(fileName);
            // Use restrictedDelete to remove from Working Directory
            Utils.restrictedDelete(join(CWD, fileName));
        }

        stage.save();
    }

    /** Log command */
    public static void log() {
        Commit current = getHeadCommit();
        String currentHash = getHeadHash();

        while (current != null) {
            System.out.println("===");
            System.out.println("commit " + currentHash);
            
            // For Merges (Optional for now, but good to have)
            if (current.isMergeCommit()) {
                System.out.printf("Merge: %s %s%n", 
                    current.getParent().substring(0, 7), 
                    current.getSecondParent().substring(0, 7));
            }

            // Format: Thu Nov 9 20:00:05 2017 -0800
            // Using "EEE MMM d HH:mm:ss yyyy Z" pattern
            SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z", Locale.US);
            System.out.println("Date: " + sdf.format(current.getTimestamp()));
            System.out.println(current.getMessage());
            System.out.println();

            // Move to the parent
            currentHash = current.getParent();
            if (currentHash == null) {
                current = null;
            } else {
                current = Utils.readObject(join(OBJECTS_DIR, currentHash), Commit.class);
            }
        }
    }

    /** GlobalLog command */
    public static void globalLog() {
        List<String> allObjects = Utils.plainFilenamesIn(OBJECTS_DIR);
        for (String fileName : allObjects) {
            // Try to read each file as a Commit; ignore if it's a Blob
            try {
                Commit c = Utils.readObject(Utils.join(OBJECTS_DIR, fileName), Commit.class);
                printCommit(c, fileName);
            } catch (Exception e) {
                // Not a commit object, skip it
            }
        }
    }

    /** Find command */
    public static void find(String message) {
        List<String> allObjects = Utils.plainFilenamesIn(OBJECTS_DIR);
        boolean found = false;
        for (String fileName : allObjects) {
            try {
                Commit c = Utils.readObject(Utils.join(OBJECTS_DIR, fileName), Commit.class);
                if (c.getMessage().equals(message)) {
                    System.out.println(fileName);
                    found = true;
                }
            } catch (Exception e) { /* Skip non-commit objects */ }
        }
        if (!found) {
            System.out.println("Found no commit with that message.");
        }
    }

    /** Status command */
    public static void status() {
        if (!GITLET_DIR.exists()) {
            System.out.println("Not in an initialized Gitlet directory.");
            return;
        }

        // 1. Branches
        System.out.println("=== Branches ===");
        String currentBranch = getHeadBranchName();
        List<String> branches = Utils.plainFilenamesIn(HEADS_DIR);
        for (String b : branches) {
            if (b.equals(currentBranch)) System.out.print("*");
            System.out.println(b);
        }
        System.out.println();

        // 2. Staged Files
        System.out.println("=== Staged Files ===");
        Stage stage = Stage.load();
        for (String fileName : stage.getAddedFiles().keySet()) {
            System.out.println(fileName);
        }
        System.out.println();

        // 3. Removed Files
        System.out.println("=== Removed Files ===");
        for (String fileName : stage.getRemovedFiles()) {
            System.out.println(fileName);
        }
        System.out.println();

        // 4. Placeholder for Extra Credit
        System.out.println("=== Modifications Not Staged For Commit ===");
        System.out.println();
        System.out.println("=== Untracked Files ===");
        System.out.println();
    }

    /** Checkout command 1: checkout -- [file name] */
    public static void checkoutFile(String fileName) {
        checkoutFileFromCommit(getHeadHash(), fileName);
    }

    /** Checkout command 2: checkout [commit id] -- [file name] */
    public static void checkoutFileFromCommit(String commitId, String fileName) {
        // Handle shortened IDs (prefix search)
        String fullHash = findFullHash(commitId);
        if (fullHash == null) {
            System.out.println("No commit with that id exists.");
            return;
        }

        File commitFile = join(OBJECTS_DIR, fullHash);
        Commit c = readObject(commitFile, Commit.class);

        if (!c.getSnapshots().containsKey(fileName)) {
            System.out.println("File does not exist in that commit.");
            return;
        }

        // Get the blob hash and write its contents to the CWD
        String blobHash = c.getSnapshots().get(fileName);
        byte[] contents = readContents(join(OBJECTS_DIR, blobHash));
        writeContents(join(CWD, fileName), contents);
    }

    /** Checkout command 3: checkout [branchname] */
    public static void checkoutBranch(String branchName) {
        File branchFile = join(HEADS_DIR, branchName);
        if (!branchFile.exists()) {
            System.out.println("No such branch exists.");
            return;
        }
        if (branchName.equals(getHeadBranchName())) {
            System.out.println("No need to checkout the current branch.");
            return;
        }

        // Load the target commit
        String targetCommitHash = readContentsAsString(branchFile);
        Commit targetCommit = readObject(join(OBJECTS_DIR, targetCommitHash), Commit.class);

        // Handle the file swapping
        restoreSnapshot(targetCommit);

        // Update the HEAD pointer to point to the new branch
        writeContents(join(GITLET_DIR, "HEAD"), "ref: refs/heads/" + branchName);
        
        // Clear and save the staging area
        Stage stage = new Stage(); 
        stage.save();
    }

    /** Branch command */
    public static void branch(String branchName) {
        // 1. Setup the path to the new branch file
        File newBranchFile = Utils.join(HEADS_DIR, branchName);

        // 2. Failure Case: Check if it already exists
        if (newBranchFile.exists()) {
            System.out.println("A branch with that name already exists.");
            return;
        }

        // 3. Get the hash of the current HEAD commit
        String headHash = getHeadHash();

        // 4. Create the branch by writing the hash to the new file
        Utils.writeContents(newBranchFile, headHash);
    }

    /** Remove branch command */
    public static void rmBranch(String branchName) {
        // 1. Check if we are trying to delete the current branch
        if (branchName.equals(getHeadBranchName())) {
            System.out.println("Cannot remove the current branch.");
            return;
        }

        // 2. Locate the branch file in refs/heads/
        File branchFile = Utils.join(HEADS_DIR, branchName);

        // 3. Failure Case: Branch doesn't exist
        if (!branchFile.exists()) {
            System.out.println("A branch with that name does not exist.");
            return;
        }

        // 4. Delete the pointer file
        branchFile.delete();
    }

    /** Reset command */
    public static void reset(String commitId) {
        // 1. Find the full hash (handles abbreviated IDs)
        String fullHash = findFullHash(commitId);
        if (fullHash == null) {
            System.out.println("No commit with that id exists.");
            return;
        }

        // 2. Load the target commit
        Commit targetCommit = Utils.readObject(Utils.join(OBJECTS_DIR, fullHash), Commit.class);
        
        // 3. Reuse the "Untracked File" and "File Restoration" logic
        // This is the same logic used in checkout branch
        restoreSnapshot(targetCommit); 

        // 4. Move the current branch pointer to this commit
        updateBranchPointer(fullHash);

        // 5. Clear the staging area
        Stage stage = new Stage();
        stage.save();
    }

    /** Merge command */
    public static void merge(String branchName) {
        // 1. Validation (Staged changes, branch existence, etc.)
        validateMerge(branchName);

        String givenHash = readContentsAsString(join(HEADS_DIR, branchName));
        String headHash = getHeadHash();
        String splitHash = findSplitPoint(headHash, givenHash);

        // 2. Special Cases: Fast-forward or Ancestor
        if (splitHash.equals(givenHash)) {
            System.out.println("Given branch is an ancestor of the current branch.");
            return;
        }
        if (splitHash.equals(headHash)) {
            checkoutBranch(branchName);
            System.out.println("Current branch fast-forwarded.");
            return;
        }

        // 3. Collect all unique filenames across the three commits
        Commit split = getCommitFromHash(splitHash);
        Commit head = getCommitFromHash(headHash);
        Commit given = getCommitFromHash(givenHash);
        Set<String> allFiles = new HashSet<>();
        allFiles.addAll(split.getSnapshots().keySet());
        allFiles.addAll(head.getSnapshots().keySet());
        allFiles.addAll(given.getSnapshots().keySet());

        boolean conflictOccurred = false;

        for (String file : allFiles) {
            String sHash = split.getSnapshots().get(file);
            String hHash = head.getSnapshots().get(file);
            String gHash = given.getSnapshots().get(file);

            // Case 1 & 5: Modified in given only, or added in given only
            if (Objects.equals(sHash, hHash) && !Objects.equals(sHash, gHash)) {
                if (gHash == null) {
                    rm(file); // Case 6: Removed in given, unmodified in head
                } else {
                    checkoutFileFromCommit(givenHash, file);
                    add(file);
                }
            }
            // Case 8: Conflict logic
            else if (!Objects.equals(sHash, hHash) && !Objects.equals(sHash, gHash) 
                    && !Objects.equals(hHash, gHash)) {
                handleConflict(file, hHash, gHash);
                conflictOccurred = true;
            }
            // Other cases: No action needed (Keep current version)
        }

        // 4. Finalize the Merge Commit
        String msg = "Merged " + branchName + " into " + getHeadBranchName() + ".";
        finishMergeCommit(msg, headHash, givenHash, conflictOccurred);
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

    /** Returns the name of the branch currently pointed to by HEAD. */
    private static String getHeadBranchName() {
        // Read the contents of the HEAD file
        String headContent = Utils.readContentsAsString(Utils.join(GITLET_DIR, "HEAD"));
        
        // headContent looks like "ref: refs/heads/master"
        if (headContent.startsWith("ref: ")) {
            // Find the last index of the slash to get just "master"
            String[] pathParts = headContent.split("/");
            return pathParts[pathParts.length - 1];
        } else {
            // If HEAD contains a raw SHA-1 hash (detached state)
            return "detached";
        }
    }

    /** Prints a commit in the format specified by the 'log' command. */
    private static void printCommit(Commit c, String hash) {
        System.out.println("===");
        System.out.println("commit " + hash);

        // Only print 'Merge:' line if it has a second parent
        if (c.isMergeCommit()) {
            System.out.printf("Merge: %s %s%n", 
                c.getParent().substring(0, 7), 
                c.getSecondParent().substring(0, 7));
        }

        SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z", Locale.US);
        System.out.println("Date: " + sdf.format(c.getTimestamp()));
        System.out.println(c.getMessage());
        System.out.println();
    }

    /** Prefix matching. */
    private static String findFullHash(String prefix) {
        if (prefix.length() == 40) return prefix;
        List<String> allObjects = plainFilenamesIn(OBJECTS_DIR);
        for (String hash : allObjects) {
            if (hash.startsWith(prefix)) return hash;
        }
        return null;
    }

    /** A helper that synchronizes the Working Directory with a target commit. */
    private static void restoreSnapshot(Commit targetCommit) {
        Commit currentCommit = getHeadCommit();
        List<String> cwdFiles = Utils.plainFilenamesIn(CWD);

        // 1. Safety Check: Is there an untracked file that would be overwritten
        for (String file : cwdFiles) {
            // If file is NOT tracked by current commit BUT IS tracked by target commit
            if (!currentCommit.getSnapshots().containsKey(file) 
                && targetCommit.getSnapshots().containsKey(file)) {
                System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                System.exit(0);
            }
        }

        // 2. Delete files tracked in current but NOT in target
        for (String fileName : currentCommit.getSnapshots().keySet()) {
            if (!targetCommit.getSnapshots().containsKey(fileName)) {
                Utils.restrictedDelete(Utils.join(CWD, fileName));
            }
        }

        // 3. Write all files from target commit to CWD
        for (Map.Entry<String, String> entry : targetCommit.getSnapshots().entrySet()) {
            String fileName = entry.getKey();
            String blobHash = entry.getValue();
            byte[] contents = Utils.readContents(Utils.join(OBJECTS_DIR, blobHash));
            Utils.writeContents(Utils.join(CWD, fileName), contents);
        }
    }
    
    /** Find latest common ancestor using BFS */
    private static String findSplitPoint(String currentHash, String givenHash) {
        Set<String> ancestorsOfCurrent = new HashSet<>();
        Queue<String> q = new LinkedList<>();
        
        // 1. Traverse all ancestors of current branch and store in a Set
        q.add(currentHash);
        while (!q.isEmpty()) {
            String curr = q.poll();
            if (curr != null && ancestorsOfCurrent.add(curr)) {
                Commit c = getCommitFromHash(curr);
                q.add(c.getParent());
                if (c.getSecondParent() != null) q.add(c.getSecondParent());
            }
        }

        // 2. Traverse ancestors of given branch; the first one found in the Set is the LCA
        q.add(givenHash);
        while (!q.isEmpty()) {
            String curr = q.poll();
            if (ancestorsOfCurrent.contains(curr)) return curr;
            Commit c = getCommitFromHash(curr);
            if (c.getParent() != null) q.add(c.getParent());
            if (c.getSecondParent() != null) q.add(c.getSecondParent());
        }
        return null;
    }

    /** Helper method to load a Commit object from the objects directory */
    private static Commit getCommitFromHash(String hash) {
        if (hash == null) {
            return null;
        }
        File commitFile = Utils.join(OBJECTS_DIR, hash);
        if (!commitFile.exists()) {
            return null;
        }
        return Utils.readObject(commitFile, Commit.class);
    }

    private static void finishMergeCommit(String msg, String headHash, String givenHash, boolean conflict) {
        // 1. Load the current state
        Commit head = getHeadCommit();
        Stage stage = Stage.load();
        
        // 2. Prepare the new snapshot
        // Start with the current HEAD's files and apply staged changes
        TreeMap<String, String> newSnapshots = new TreeMap<>(head.getSnapshots());
        newSnapshots.putAll(stage.getAddedFiles());
        for (String fileName : stage.getRemovedFiles()) {
            newSnapshots.remove(fileName);
        }

        // 3. Create and save the Merge Commit
        Commit mergeCommit = new Commit(msg, headHash, newSnapshots);
        mergeCommit.setSecondParent(givenHash); // Link the second branch!
        mergeCommit.save();

        // 4. Update the current branch pointer to this new commit
        String currentBranch = getHeadBranchName();
        Utils.writeContents(Utils.join(HEADS_DIR, currentBranch), mergeCommit.getHash());

        // 5. Clean up
        stage.clear();
        stage.save();

        if (conflict) {
            System.out.println("Encountered a merge conflict.");
        }
    }

    /** Construct the file content when conflicts occur */
    private static void handleConflict(String fileName, String currentBlob, String givenBlob) {
        String headContent = (currentBlob == null) ? "" : Utils.readContentsAsString(join(OBJECTS_DIR, currentBlob));
        String givenContent = (givenBlob == null) ? "" : Utils.readContentsAsString(join(OBJECTS_DIR, givenBlob));

        String conflictText = "<<<<<<< HEAD\n" + headContent + "=======\n" + givenContent + ">>>>>>>\n";
        Utils.writeContents(join(CWD, fileName), conflictText);
        
        // Always stage the conflict result
        Stage stage = Stage.load();
        stage.add(fileName, Utils.sha1(conflictText)); 
        stage.save();
    }

    private static void validateMerge(String branchName) {
        // 1. Check for staged additions or removals
        Stage stage = Stage.load();
        if (!stage.getAddedFiles().isEmpty() || !stage.getRemovedFiles().isEmpty()) {
            System.out.println("You have uncommitted changes.");
            System.exit(0);
        }

        // 2. Check if the branch exists
        File branchFile = Utils.join(HEADS_DIR, branchName);
        if (!branchFile.exists()) {
            System.out.println("A branch with that name does not exist.");
            System.exit(0);
        }

        // 3. Check for merging a branch with itself
        if (branchName.equals(getHeadBranchName())) {
            System.out.println("Cannot merge a branch with itself.");
            System.exit(0);
        }

        // 4. Untracked file check (safety first!)
        String givenHash = Utils.readContentsAsString(branchFile);
        Commit givenCommit = getCommitFromHash(givenHash);
        Commit headCommit = getHeadCommit();
        
        List<String> cwdFiles = Utils.plainFilenamesIn(CWD);
        for (String file : cwdFiles) {
            // If file is untracked in current but tracked in the branch we're merging in
            if (!headCommit.getSnapshots().containsKey(file) 
                && givenCommit.getSnapshots().containsKey(file)) {
                System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                System.exit(0);
            }
        }
    }
}
