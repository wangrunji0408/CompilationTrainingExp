package submit;

// some useful things to import. add any additional imports you need.
import joeq.Compiler.Quad.*;
import flow.Flow;

import java.util.Collection;
import java.util.HashSet;

/**
 * Skeleton class for implementing the Flow.Solver interface.
 */
public class MySolver implements Flow.Solver {

    protected Flow.Analysis analysis;

    /**
     * Sets the analysis.  When visitCFG is called, it will
     * perform this analysis on a given CFG.
     *
     * @param analyzer The analysis to run
     */
    public void registerAnalysis(Flow.Analysis analyzer) {
        this.analysis = analyzer;
    }

    /**
     * Runs the solver over a given control flow graph.  Prior
     * to calling this, an analysis must be registered using
     * registerAnalysis
     *
     * @param cfg The control flow graph to analyze.
     */
    public void visitCFG(ControlFlowGraph cfg) {

        // this needs to come first.
        analysis.preprocess(cfg);

//        System.err.println(cfg.fullDump());

		// Make quad sets
		HashSet<Quad> allQuads = new HashSet<Quad>();
		for(QuadIterator iter = new QuadIterator(cfg); iter.hasNext(); ) {
			Quad q = iter.next();
			allQuads.add(q);
		}
		Quad entryQuad = cfg.entry().getSuccessors().get(0).getQuad(0);
		HashSet<Quad> exitQuads = findAllExitQuad(cfg);

        if(analysis.isForward()) {
			// Out[NOT ENTRY] = TOP
			for(Quad q: allQuads) {
				analysis.setOut(q, newTop());
			}
			// Out[ENTRY] = V_entry

			for(boolean changed = true; changed; ) {
				changed = false;
				for(QuadIterator iter = new QuadIterator(cfg); iter.hasNext(); ) {
					Quad q = iter.next();
					// In
					Flow.DataflowObject newIn = meetAll(iter.predecessors1(), true, analysis.getEntry());
					changed |= !newIn.equals(analysis.getIn(q));
					analysis.setIn(q, newIn);
					// Out
					Flow.DataflowObject oldOut = analysis.getOut(q);
					analysis.processQuad(q);
					changed |= !oldOut.equals(analysis.getOut(q));
				}
				// Exit
				Flow.DataflowObject newIn = meetAll(exitQuads, true, analysis.getEntry());
				changed |= !newIn.equals(analysis.getExit());
				analysis.setExit(newIn);
			}
        } else {
            // In[NOT EXIT] = TOP
            for(Quad q: allQuads) {
                analysis.setIn(q, newTop());
            }
            // In[EXIT] = V_exit

			for(boolean changed = true; changed; ) {
				changed = false;
                for(QuadIterator iter = new QuadIterator(cfg); iter.hasNext(); ) {
                    Quad q = iter.next();
                    // Out
                    Flow.DataflowObject newOut = meetAll(iter.successors1(), false, analysis.getExit());
                    changed |= !newOut.equals(analysis.getOut(q));
                    analysis.setOut(q, newOut);
                    // In
                    Flow.DataflowObject oldIn = analysis.getIn(q);
                    analysis.processQuad(q);
                    changed |= !oldIn.equals(analysis.getIn(q));
                }
				// Entry
				Flow.DataflowObject newOut = analysis.getIn(entryQuad);
				changed |= !newOut.equals(analysis.getEntry());
				analysis.setEntry(newOut);
            }
        }

        // this needs to come last.
        analysis.postprocess(cfg);
    }

    Flow.DataflowObject newTop() {
		Flow.DataflowObject top = analysis.newTempVar();
		top.setToTop();
		return top;
	}

	// nullVal = in? getEntry(): getExit()
	Flow.DataflowObject meetAll(Collection<Quad> quads, boolean out, Flow.DataflowObject nullVal) {
		Flow.DataflowObject val = newTop();
		for(Quad s: quads) {
			// 's == null' => s is ENTRY or EXIT => meetWith(nullVal)
			val.meetWith(s == null? nullVal: out? analysis.getOut(s): analysis.getIn(s));
		}
		return val;
	}

    static HashSet<Quad> findAllExitQuad(ControlFlowGraph cfg) {
        HashSet<Quad> exitQuads = new HashSet<Quad>();
		for(QuadIterator iter = new QuadIterator(cfg); iter.hasNext(); ) {
			Quad q = iter.next();
			if(iter.successors1().contains(null))
				exitQuads.add(q);
		}
        return exitQuads;
    }
}
