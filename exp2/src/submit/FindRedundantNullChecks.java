package submit;

import java.util.*;

public class FindRedundantNullChecks {

    /**
     * Main method of FindRedundantNullChecks.
     * This method should print out a list of quad ids of redundant null checks for each function.
     * The format should be "method_name id0 id1 id2", integers for each id separated by spaces.
     *
     * @param args an array of class names. If "-e" presented, do extra analysing.
     */
    public static void main(String[] _args) {
        List<String> args = new ArrayList<String>(Arrays.asList(_args));
        boolean extra = args.contains("-e");
        if (extra)
            args.remove("-e");
        // TODO: Fill in this
    }
}
