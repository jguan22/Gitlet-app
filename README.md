# Gitlet - Distributed Version Control System

A fully functional, local version-control system built from scratch in Java. Gitlet mimics the core functionality of Git, managing file backups, maintaining a historical log of commits, supporting branching and merging, and enabling time-travel capabilities through repository state restoration.

---

## 🛠️ Architecture & Data Design

Gitlet is engineered using a content-addressable storage filesystem and reference-based tracking. Instead of copying entire directories, it maps file snapshots to immutable objects using cryptographic hashing.

### Design Patterns & Core Structures
* **Commit Graph (DAG):** Commits form a **Directed Acyclic Graph** where each node points to its first parent (history tracking) and optionally a second parent (merge tracking).
* **Content-Addressable Blobs:** Files are saved uniquely in an objects directory by their SHA-1 hash values, maximizing data deduplication across commits.
* **Separation of Concerns:** The engine decouples pointer management (`HEAD` and branch refs) from the filesystem state synchronization module, providing modular execution loops.

---

## 🚀 Supported Commands

### Core Workspace Management
* `init`: Initializes a new Gitlet version-control structure in the current directory.
* `add [file name]`: Stages a file for addition by hashing its contents and mapping it to the staging area.
* `rm [file name]`: Unstages a file or schedules a tracked file for removal in the next commit, deleting it from the working directory.
* `commit [message]`: Packages staged additions and removals into a persistent commit node, updating the current branch pointer.

### Repository Inspection
* `log`: Displays history backwards starting from the current branch's head commit following first-parent links.
* `global-log`: Displays data for all commits ever created across all timelines in an unordered scan.
* `find [message]`: Queries the object space for commits matching a specific text footprint and outputs their SHA-1 identifiers.
* `status`: Displays current active branches, staged files, and files marked for deletion in lexicographical order.

### Graph & Branch Navigation
* `branch [branch name]`: Spawns a lightweight pointer to the current head commit under a new reference name.
* `rm-branch [branch name]`: Destroys a branch reference pointer without purging the underlying commit nodes from disk.
* `checkout`:
  * `checkout -- [file name]`: Overwrites the file in the working directory with the version found in the `HEAD` commit.
  * `checkout [commit id] -- [file name]`: Restores a file to the working directory from an arbitrary historical commit (supports abbreviated 6+ character SHA-1 prefixes).
  * `checkout [branch name]`: Safely transitions the entire workspace environment to another branch timeline.
* `reset [commit id]`: A hard reset mechanism that synchronizes the working directory to an arbitrary commit status and moves the active branch head to that node.
* `merge [branch name]`: Executes a automated three-way merge against the **Latest Common Ancestor (LCA)** split point, resolving conflicts safely via inline markers (`<<<<<<< HEAD`).

---

## 🛡️ Defensive Programming & Edge Handling

* **Atomicity & Fail-Safes:** Destructive operations like `checkout`, `reset`, and `merge` run comprehensive pre-execution workspace audits. They abort immediately—preserving local data—if untracked files risk being accidentally overwritten or deleted.
* **Null-Safety:** High-risk multi-version file evaluations utilize null-safe operations (`java.util.Objects.equals`) to avoid `NullPointerExceptions` when dealing with files that do not exist across divergent branches.
* **I/O Management:** Object states are handled via custom serialization protocols, saving and deserializing Java objects directly to disk on-demand to keep memory consumption low.

---

## 💻 Tech Stack

* **Language:** Java
* **Data Persistence:** Custom Serialization & File I/O Engine
* **Cryptography:** SHA-1 Hashing Algorithms
