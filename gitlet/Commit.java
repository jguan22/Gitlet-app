package gitlet;

// TODO: any imports you need here
import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Represents a gitlet commit object.
 *  TODO: It's a good idea to give a description here of what else this Class
 *  does at a high level.
 *
 *  @author TODO
 */
public class Commit implements Serializable {
    /**
     * TODO: add instance variables here.
     *
     * List all instance variables of the Commit class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided one example for `message`.
     */

    /** The message of this Commit. */
    private String message;
    private String parent;
    private String secondParent;
    private java.util.Date timestamp;
    private TreeMap<String, String> snapshots;

    public Commit(String message, String parent, TreeMap<String, String> snapshots) {
        this.message = message;
        this.parent = parent;
        this.snapshots = snapshots;
        this.timestamp = (parent == null) ? new java.util.Date(0) : new java.util.Date();
    }

    public String getMessage() { return message; }

    public String getParent() { return parent; }

    public String getSecondParent() { return secondParent; }

    public java.util.Date getTimestamp() { return timestamp; }

    public Map<String, String> getSnapshots() {
        return Collections.unmodifiableMap(snapshots);
    }

    public boolean isMergeCommit() {
        return secondParent != null;
    }

    /** Returns the SHA-1 hash of this commit object. */
    public String getHash() {
        // We serialize the entire commit object to bytes, then hash those bytes
        return Utils.sha1((Object) Utils.serialize(this));
    }

    /** Saves this commit to the objects directory. */
    public void save() {
        String hash = getHash();
        // The filename is the SHA-1 hash
        File commitFile = Utils.join(Repository.OBJECTS_DIR, hash);
        Utils.writeObject(commitFile, this);
    }
}
