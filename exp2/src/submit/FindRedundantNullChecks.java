package submit;

import flow.*;
import joeq.Class.jq_Class;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Main.Helper;
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

        jq_Class clazz = (jq_Class)Helper.load(_args[0]);
        Flow.Solver solver = new FlowSolver();
        NonNull analyzer = new NonNull();
        solver.registerAnalysis(analyzer);
        Helper.runPass(clazz, solver);
    }

    // `submit.Faintness` is just a copy of `flow.Faintness` with :
    //  1. some fields changed to protected
    //  2. comment origin output
    static class NonNull extends submit.Faintness {
        /**
         * Transfer function:   ...
         * Meet operation:      and
         */

        Set<Integer> redundantNullChecks;

        @Override
        public void preprocess(ControlFlowGraph cfg) {
            super.preprocess(cfg);

            int numargs = cfg.getMethod().getParamTypes().length;
            for (int i = 0; i < numargs; i++) {
                super.entry.set.remove("R" + i);
            }

            redundantNullChecks = new TreeSet<Integer>();
        }

        @Override
        public void postprocess(ControlFlowGraph cfg) {
            System.out.print(cfg.getMethod().getName());
            for (Integer id: redundantNullChecks)
                System.out.print(" " + id.toString());
            System.out.println();
        }

        @Override
        public boolean isForward() {
            return true;
        }

        @Override
        public void processQuad(Quad q) {
            VarSet val = (VarSet) getIn(q);
            // kill all defs
            for(Operand.RegisterOperand r: q.getDefinedRegisters()) {
                val.set.remove(r.getRegister().toString());
            }
            if(q.getOperator() instanceof Operator.NullCheck) {
                String reg = q.getUsedRegisters().get(0).getRegister().toString();
                // check redundant
                if(val.set.contains(reg)) {
                    redundantNullChecks.add(q.getID());
                } else {
                    redundantNullChecks.remove(q.getID());
                }
                // gen non nulls
                val.set.add(reg);
            }
            setOut(q, val);
        }
    }
}
