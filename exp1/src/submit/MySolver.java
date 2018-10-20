package submit;

// some useful things to import. add any additional imports you need.
import joeq.Compiler.Quad.*;
import flow.Flow;

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

        System.err.println(cfg.fullDump());

        if(analysis.isForward()) {

        } else {
            // Make quad sets
            HashSet<Quad> allQuads = new HashSet<Quad>();
            for(QuadIterator iter = new QuadIterator(cfg); iter.hasNext(); ) {
                Quad q = iter.next();
                allQuads.add(q);
            }
            HashSet<Quad> exitQuads = findAllExitQuad(cfg);
            // In[NOT EXIT] = TOP
            for(Quad q: allQuads) {
                Flow.DataflowObject top = analysis.newTempVar();
                top.setToTop();
                analysis.setIn(q, top);
            }
            // In[EXIT] = V_exit
            for(Quad q: exitQuads) {
                analysis.setIn(q, analysis.getExit());
            }
            boolean changed = true;
            while(changed) {
                changed = false;
                for(QuadIterator iter = new QuadIterator(cfg); iter.hasNext(); ) {
                    Quad q = iter.next();
                    if(exitQuads.contains(q))
                        continue;
                    // Out
                    Flow.DataflowObject newOut = analysis.newTempVar();
                    newOut.setToTop();
                    for(Quad s: iter.successors1()) {
                        if(s == null)
                            continue;
                        newOut.meetWith(analysis.getIn(s));
                    }
                    changed |= !newOut.equals(analysis.getOut(q));
                    analysis.setOut(q, newOut);
                    // In
                    Flow.DataflowObject oldIn = analysis.getIn(q);
                    analysis.processQuad(q);
                    changed |= !oldIn.equals(analysis.getIn(q));
                }
                // TODO: entry
            }
        }

        // this needs to come last.
        analysis.postprocess(cfg);
    }

    static HashSet<Quad> findAllExitQuad(ControlFlowGraph cfg) {
        HashSet<Quad> exitQuads = new HashSet<Quad>();

        HashSet<BasicBlock> emptyExitBlocks = new HashSet<BasicBlock>();
        emptyExitBlocks.add(cfg.exit());
        while(!emptyExitBlocks.isEmpty()) {
            BasicBlock b = emptyExitBlocks.iterator().next();
            emptyExitBlocks.remove(b);
            for(BasicBlock bb: b.getPredecessors()) {
                Quad lastQuad = bb.getLastQuad();
                if(lastQuad == null) {
                    emptyExitBlocks.add(bb);
                } else {
                    exitQuads.add(lastQuad);
                }
            }
        }
        return exitQuads;
    }
}
