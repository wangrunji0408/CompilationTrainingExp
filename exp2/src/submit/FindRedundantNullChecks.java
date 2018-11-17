package submit;

import flow.*;
import joeq.Class.jq_Class;
import joeq.Compiler.Quad.*;
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
        NonNull analyzer = new NonNull(extra);
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
        final boolean extra;

		NonNull(boolean extra) {
			this.extra = extra;
		}

		@Override
        public void preprocess(ControlFlowGraph cfg) {
		    if(extra) {
                // Modify CFG first
                createNullCheckAfterIfCmp(cfg);
            }

            super.preprocess(cfg);

            int numargs = cfg.getMethod().getParamTypes().length;
            for (int i = 0; i < numargs; i++) {
                super.entry.set.remove("R" + i);
            }

            redundantNullChecks = new TreeSet<Integer>();
        }

        static void createNullCheckAfterIfCmp(ControlFlowGraph cfg) {
            QuadIterator iter = new QuadIterator(cfg);
            while(iter.hasNext()) {
                Quad q = iter.next();
                if(!(q.getOperator() instanceof Operator.IntIfCmp
                        && q.getAllOperands().get(1).isSimilar(new Operand.AConstOperand(null)))) {
                    continue;
                }
                boolean isEQ = ((Operand.ConditionOperand)q.getAllOperands().get(2)).getCondition() == 0;
                BasicBlock fallBB = iter.getCurrentBasicBlock().getFallthroughSuccessor();
                BasicBlock branchBB = ((Operand.TargetOperand)q.getAllOperands().get(3)).getTarget();
                BasicBlock nonNullBB = isEQ? fallBB: branchBB;

                // insert a BasicBlock with NullCheck before the nonNullBB
                BasicBlock from = iter.getCurrentBasicBlock();
                BasicBlock to = nonNullBB;
                BasicBlock newb = cfg.createBasicBlock(1, 1, 1, null);
                newb.addQuad(0, createANullCheckQuad(cfg, q.getUsedRegisters().get(0)));
                // modify the link
                from.removeSuccessor(to);
                from.addSuccessor(newb);
                to.removePredecessor(from);
                to.addPredecessor(newb);
                newb.addPredecessor(from);
                newb.addSuccessor(to);
                // change current Quad's target
                if(!isEQ) {
                    q.setOp4(new Operand.TargetOperand(newb));
                }
            }
//            System.out.println(cfg.fullDump());
        }

        static Quad createANullCheckQuad(ControlFlowGraph cfg, Operand.RegisterOperand reg) {
		    // trick: find a NullCheck and copy it
            QuadIterator iter = new QuadIterator(cfg);
            while(iter.hasNext()) {
                Quad q = iter.next();
                if(q.getOperator() instanceof Operator.NullCheck) {
                    Quad newQuad = q.copy(cfg.getNewQuadID());
                    newQuad.setOp2(reg);
                    return newQuad;
                }
            }
            throw new RuntimeException("NullCheck not found");
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
