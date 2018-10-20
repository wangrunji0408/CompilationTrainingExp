package submit;

// some useful things to import. add any additional imports you need.
import flow.Liveness;
import joeq.Compiler.Quad.*;
import flow.Flow;

/**
 * Skeleton class for implementing a faint variable analysis
 * using the Flow.Analysis interface.
 */
public class Faintness extends Liveness implements Flow.Analysis {

    @Override
    public void postprocess(ControlFlowGraph cfg) {
        for(VarSet v: super.in)
            v._setToCompliment();
        for(VarSet v: super.out)
            v._setToCompliment();
        super.entry._setToCompliment();
        super.exit._setToCompliment();

        super.postprocess(cfg);
    }

    @Override
    public void processQuad(Quad q) {
        in[q.getID()].copy(out[q.getID()]);
        transfer(q, in[q.getID()]);
    }

    private void transfer(Quad q, VarSet val) {
        for (Operand.RegisterOperand def : q.getDefinedRegisters()) {
            val.killVar(def.getRegister().toString());
        }
        boolean realUse = true;
        if(q.getOperator() instanceof Operator.Move
        || q.getOperator() instanceof Operator.Binary) {
            String defVar = q.getDefinedRegisters().get(0).getRegister().toString();
            realUse = val.contains(defVar);
        }
        if(realUse) {
            for (Operand.RegisterOperand use : q.getUsedRegisters()) {
                val.genVar(use.getRegister().toString());
            }
        }
    }
}
