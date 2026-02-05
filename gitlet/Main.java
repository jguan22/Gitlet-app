package gitlet;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author Jiehao Guan
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Please enter a command.");
            return;
        }

        String firstArg = args[0];
        switch (firstArg) {
            case "init":
                validateArgs(args, 1);
                Repository.init();
                break;
            case "add":
                validateArgs(args, 2);
                Repository.add(args[1]);
                break;
            case "commit":
                if (args.length < 2 || args[1].isEmpty()) {
                    System.out.println("Please enter a commit message.");
                    System.exit(0);
                }
                validateArgs(args, 2);
                Repository.commit(args[1]);
                break;
            case "rm":
                validateArgs(args, 2);
                Repository.rm(args[1]);
                break;
            case "log":
                validateArgs(args, 1);
                Repository.log();
                break;
            case "global-log":
                validateArgs(args, 1);
                Repository.globalLog();
                break;
            case "find":
                validateArgs(args, 2);
                Repository.find(args[1]);
                break;
            case "status":
                validateArgs(args, 1);
                Repository.status();
                break;
            case "checkout":
                handleCheckout(args);
                break;
            case "branch":
                validateArgs(args, 2);
                Repository.branch(args[1]);
                break;
            case "rm-branch":
                validateArgs(args, 2);
                Repository.rmBranch(args[1]);
                break;
            case "reset":
                validateArgs(args, 2);
                Repository.reset(args[1]);
                break;
            case "merge":
                validateArgs(args, 2);
                Repository.merge(args[1]);
                break;
            default:
                System.out.println("No command with that name exists.");
                System.exit(0);
        }
    }

    /** Helper to check for correct number of arguments. */
    private static void validateArgs(String[] args, int n) {
        if (args.length != n) {
            System.out.println("Incorrect operands.");
            System.exit(0);
        }
    }

    /** Handles the three different types of checkout. */
    private static void handleCheckout(String[] args) {
        if (args.length == 3 && args[1].equals("--")) {
            Repository.checkoutFile(args[2]);
        } else if (args.length == 4 && args[2].equals("--")) {
            Repository.checkoutFileFromCommit(args[1], args[3]);
        } else if (args.length == 2) {
            Repository.checkoutBranch(args[1]);
        } else {
            System.out.println("Incorrect operands.");
            System.exit(0);
        }
    }
}
