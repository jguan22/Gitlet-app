package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public class Stage implements Serializable {
    /** Map of fileName -> blob SHA-1 for addition */
    private TreeMap<String, String> addedFiles = new TreeMap<>();
    /** Set of files to be removed (for the 'rm' command) */
    private TreeSet<String> removedFiles = new TreeSet<>();

    public void add(String fileName, String blobHash) {
        addedFiles.put(fileName, blobHash);
        removedFiles.remove(fileName); // Undo a removal if staged for addition
    }

    public void stageForRemoval(String fileName) {
        removedFiles.add(fileName);
    }

    public TreeSet<String> getRemovedFiles() {
        return removedFiles;
    }

    public void removeFromAddition(String fileName) {
        addedFiles.remove(fileName);
    }

    public Map<String, String> getAddedFiles() {
        return addedFiles;
    }

    public boolean isClean() {
        return addedFiles.isEmpty() && removedFiles.isEmpty();
    }

    public void clear() {
        addedFiles.clear();
        removedFiles.clear();
    }

    /** Persistence: Saves the staging area to the .gitlet directory */
    public void save() {
        File index = Utils.join(Repository.GITLET_DIR, "index");
        Utils.writeObject(index, this);
    }

    /** Persistence: Loads the staging area from disk */
    public static Stage load() {
        File index = Utils.join(Repository.GITLET_DIR, "index");
        if (!index.exists()) {
            return new Stage();
        }
        return Utils.readObject(index, Stage.class);
    }
}