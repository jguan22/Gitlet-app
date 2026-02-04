package gitlet;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author TODO
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
        try {
            switch (firstArg) {
                case "init":
                    Repository.init();
                    break;
                case "add":
                    checkArgs(args, 2);
                    Repository.add(args[1]);
                    break;
                case "commit":
                    checkArgs(args, 2);
                    Repository.commit(args[1]);
                    break;
                // Add more cases as you go (log, checkout, branch)
                default:
                    System.out.println("No command with that name exists.");
            }
        } catch (GitletException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void checkArgs(String[] args, int expectedCount) {
        if (args.length != expectedCount) {
            throw new GitletException("Incorrect operands.");
        }
    }
}
