package submit;

// some useful things to import. add any additional imports you need.
import joeq.Compiler.Quad.*;
import flow.Flow;
import joeq.Main.Helper;

import java.util.*;

/**
 * Skeleton class for implementing a reaching definition analysis
 * using the Flow.Analysis interface.
 */
public class ReachingDefs implements Flow.Analysis {

    /**
     * Class for the dataflow objects in the ReachingDefs analysis.
     * You are free to change this class or move it to another file.
     */
    public static class RdSet implements Flow.DataflowObject {
        private Set<Integer> rdSet;
        static Set<Integer> rdCompleteSet;

        RdSet() {
            rdSet = new TreeSet<Integer>();
        }

        /**
         * Methods from the Flow.DataflowObject interface.
         * See Flow.java for the meaning of these methods.
         * These need to be filled in.
         */
        public void setToTop() {
            rdSet = new TreeSet<Integer>();
        }
        public void setToBottom() {
            rdSet = new TreeSet<Integer>(rdCompleteSet);
        }
        public void meetWith (Flow.DataflowObject o) {
            rdSet.addAll(((RdSet)o).rdSet);
        }
        public void copy (Flow.DataflowObject o) {
            rdSet = new TreeSet<Integer>(((RdSet)o).rdSet);
        }

        /**
         * toString() method for the dataflow objects which is used
         * by postprocess() below.  The format of this method must
         * be of the form "[ID0, ID1, ID2, ...]", where each ID is
         * the identifier of a quad defining some register, and the
         * list of IDs must be sorted.  See src/test/Test.rd.out
         * for example output of the analysis.  The output format of
         * your reaching definitions analysis must match this exactly.
         */
        @Override
        public String toString() { return rdSet.toString(); }

        @Override
        public boolean equals(Object o) {
            if (o instanceof RdSet) {
                return rdSet.equals(((RdSet) o).rdSet);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return rdSet.hashCode();
        }
    }

    /**
     * Dataflow objects for the interior and entry/exit points
     * of the CFG. in[ID] and out[ID] store the entry and exit
     * state for the input and output of the quad with identifier ID.
     *
     * You are free to modify these fields, just make sure to
     * preserve the data printed by postprocess(), which relies on these.
     */
    private RdSet[] in, out;
    private RdSet entry, exit;

    /**
     * This method initializes the datflow framework.
     *
     * @param cfg  The control flow graph we are going to process.
     */
    public void preprocess(ControlFlowGraph cfg) {
        // this line must come first.
        System.out.println("Method: "+cfg.getMethod().getName().toString());

        // allocate the in and out arrays.
        in = new RdSet[cfg.getMaxQuadID() + 1];
        out = new RdSet[cfg.getMaxQuadID() + 1];

        // initialize the contents of in and out.
        QuadIterator qit = new QuadIterator(cfg);
        while (qit.hasNext()) {
            int id = qit.next().getID();
            in[id] = new RdSet();
            out[id] = new RdSet();
        }

        // initialize the entry and exit points.
        entry = new RdSet();
        exit = new RdSet();

        // Init complete set
        TreeSet<Integer> s = new TreeSet<Integer>();
        for(int i=1; i<=cfg.getMaxQuadID(); ++i)
            s.add(i);
        RdSet.rdCompleteSet = s;

        // Init varToIds
        Map<String, Collection<Integer>> m = new HashMap<String, Collection<Integer>>();
        for(qit = new QuadIterator(cfg); qit.hasNext(); ) {
            Quad q = qit.next();
            for(Operand.RegisterOperand rr: q.getDefinedRegisters()) {
                String r = rr.getRegister().toString();
                if(!m.containsKey(r))
                    m.put(r, new TreeSet<Integer>());
                m.get(r).add(q.getID());
            }
        }
        TransferFunction.varToIds = m;
    }

    /**
     * This method is called after the fixpoint is reached.
     * It must print out the dataflow objects associated with
     * the entry, exit, and all interior points of the CFG.
     * Unless you modify in, out, entry, or exit you shouldn't
     * need to change this method.
     *
     * @param cfg  Unused.
     */
    public void postprocess (ControlFlowGraph cfg) {
        System.out.println("entry: " + entry.toString());
        for (int i=0; i<in.length; i++) {
            if (in[i] != null) {
                System.out.println(i + " in:  " + in[i].toString());
                System.out.println(i + " out: " + out[i].toString());
            }
        }
        System.out.println("exit: " + exit.toString());
    }

    /**
     * Other methods from the Flow.Analysis interface.
     * See Flow.java for the meaning of these methods.
     * These need to be filled in.
     */
    public boolean isForward () { return true; }
    public Flow.DataflowObject getEntry()
    {
        Flow.DataflowObject result = newTempVar();
        result.copy(entry);
        return result;
    }
    public Flow.DataflowObject getExit()
    {
        Flow.DataflowObject result = newTempVar();
        result.copy(exit);
        return result;
    }
    public Flow.DataflowObject getIn(Quad q)
    {
        Flow.DataflowObject result = newTempVar();
        result.copy(in[q.getID()]);
        return result;
    }
    public Flow.DataflowObject getOut(Quad q)
    {
        Flow.DataflowObject result = newTempVar();
        result.copy(out[q.getID()]);
        return result;
    }
    public void setIn(Quad q, Flow.DataflowObject value)
    {
        in[q.getID()].copy(value);
    }
    public void setOut(Quad q, Flow.DataflowObject value)
    {
        out[q.getID()].copy(value);
    }
    public void setEntry(Flow.DataflowObject value)
    {
        entry.copy(value);
    }
    public void setExit(Flow.DataflowObject value)
    {
        exit.copy(value);
    }
    public Flow.DataflowObject newTempVar() { return new RdSet(); }
    public void processQuad(Quad q) {
        // Actually perform the transfer operation on the relevant quad.
        TransferFunction transferfn = new TransferFunction();
        transferfn.val.copy(in[q.getID()]);
        Helper.runPass(q, transferfn);
        out[q.getID()].copy(transferfn.val);
    }

    /* The QuadVisitor that actually does the computation */
    public static class TransferFunction extends QuadVisitor.EmptyVisitor {
        RdSet val = new RdSet();
        static Map<String, Collection<Integer>> varToIds;

        @Override
        public void visitQuad(Quad q) {
            // Kill
            for (Operand.RegisterOperand def : q.getDefinedRegisters()) {
                val.rdSet.removeAll(varToIds.get(def.getRegister().toString()));
            }
            // Gen
            if(!q.getDefinedRegisters().isEmpty())
                val.rdSet.add(q.getID());
        }
    }
}
