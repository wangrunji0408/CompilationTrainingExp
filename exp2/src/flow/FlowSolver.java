package flow;

// some useful things to import. add any additional imports you need.

import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.QuadIterator;

import java.util.Collection;

/**
 * Skeleton class for implementing the Flow.Solver interface.
 */
public class FlowSolver implements Flow.Solver {

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
     * Computes the meet over all predecessors/successors
     */
    private Flow.DataflowObject computeConfluence(Collection<Quad> quads, boolean direction) {
        Flow.DataflowObject temp = analysis.newTempVar();
        for (Quad P : quads)
            if (P != null) {
                if (direction)
                    temp.meetWith(analysis.getOut(P));
                else
                    temp.meetWith(analysis.getIn(P));
            } else {
                if (direction)
                    temp.meetWith(analysis.getEntry());
                else
                    temp.meetWith(analysis.getExit());
            }
        return temp;
    }

    /**
     * Processes a quad in the specified direction.
     * Return value is true if changes were made
     */
    private boolean transfer(Quad quad, boolean direction) {
        Flow.DataflowObject original, modified;
        if (direction)
            original = analysis.getOut(quad);
        else
            original = analysis.getIn(quad);
        analysis.processQuad(quad);

        if (direction)
            modified = analysis.getOut(quad);
        else
            modified = analysis.getIn(quad);

        return !modified.equals(original);
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
        boolean changesMade = true;
        while (changesMade) {
            changesMade = false;
            QuadIterator iter = new QuadIterator(cfg, analysis.isForward());
            if (analysis.isForward())
                while (iter.hasNext()) // Iterate forward
                {
                    Quad quad = iter.next();
                    analysis.setIn(quad, computeConfluence(iter.predecessors1(), true));
                    changesMade = changesMade || transfer(quad, true);

                    // Check if we need to update the exit
                    if (iter.successors1().contains(null)) {
                        Flow.DataflowObject temp = analysis.getExit();
                        temp.meetWith(analysis.getOut(quad));
                        analysis.setExit(temp);
                    }
                }
            else
                while (iter.hasPrevious()) // Iterate backward
                {
                    Quad quad = iter.previous();
                    analysis.setOut(quad, computeConfluence(iter.successors1(), false));
                    changesMade = changesMade || transfer(quad, false);

                    // Check if we need to update the entry
                    if (iter.predecessors1().contains(null)) {
                        Flow.DataflowObject temp = analysis.getEntry();
                        temp.meetWith(analysis.getIn(quad));
                        analysis.setEntry(temp);
                    }
                }
        }
        // this needs to come last.
        analysis.postprocess(cfg);
    }
}
