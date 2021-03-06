package at.ac.tuwien.infosys.www.pixy.analysis.dependency.transferfunction;

import at.ac.tuwien.infosys.www.pixy.analysis.AbstractLatticeElement;
import at.ac.tuwien.infosys.www.pixy.analysis.AbstractTransferFunction;
import at.ac.tuwien.infosys.www.pixy.analysis.dependency.DependencyAnalysis;
import at.ac.tuwien.infosys.www.pixy.analysis.dependency.DependencyLatticeElement;
import at.ac.tuwien.infosys.www.pixy.conversion.*;
import at.ac.tuwien.infosys.www.pixy.conversion.cfgnodes.AbstractCfgNode;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * @author Nenad Jovanovic <enji@seclab.tuwien.ac.at>
 */
public class CallPreparation extends AbstractTransferFunction {
    private List<TacActualParameter> actualParams;
    private List<TacFormalParameter> formalParams;
    private TacFunction caller;
    private TacFunction callee;
    private DependencyAnalysis dependencyAnalysis;
    private AbstractCfgNode cfgNode;

//  *********************************************************************************
//  CONSTRUCTORS ********************************************************************
//  *********************************************************************************

    public CallPreparation(
        List<TacActualParameter> actualParams, List<TacFormalParameter> formalParams, TacFunction caller, TacFunction callee,
        DependencyAnalysis dependencyAnalysis, AbstractCfgNode cfgNode
    ) {
        this.actualParams = actualParams;
        this.formalParams = formalParams;
        this.caller = caller;
        this.callee = callee;
        this.dependencyAnalysis = dependencyAnalysis;
        this.cfgNode = cfgNode;
    }

//  *********************************************************************************
//  OTHER ***************************************************************************
//  *********************************************************************************

    public AbstractLatticeElement transfer(AbstractLatticeElement inX) {
        DependencyLatticeElement in = (DependencyLatticeElement) inX;
        DependencyLatticeElement out = new DependencyLatticeElement(in);

        // set formal params...

        // use a ListIterator for formals because we might need to step back (see below)
        ListIterator<TacFormalParameter> formalIter = formalParams.listIterator();
        Iterator<TacActualParameter> actualIter = actualParams.iterator();

        // for each formal parameter...
        while (formalIter.hasNext()) {
            TacFormalParameter formalParam = formalIter.next();

            if (actualIter.hasNext()) {
                // there is a corresponding actual parameter; advance iterator
                actualIter.next();

                // set the formal
                out.setFormal(formalParam, cfgNode);
            } else {
                // there is no corresponding actual parameter, use default values
                // for the remaining formal parameters

                // make one step back (so we can use a while loop)
                formalIter.previous();

                while (formalIter.hasNext()) {
                    formalParam = formalIter.next();

                    if (formalParam.hasDefault()) {
                        ControlFlowGraph defaultControlFlowGraph = formalParam.getDefaultControlFlowGraph();

                        // default CFG's have no branches;
                        // start at the CFG's head and apply all transfer functions
                        AbstractCfgNode defaultNode = defaultControlFlowGraph.getHead();
                        while (defaultNode != null) {
                            AbstractTransferFunction tf = this.dependencyAnalysis.getTransferFunction(defaultNode);
                            out = (DependencyLatticeElement) tf.transfer(out);
                            defaultNode = defaultNode.getSuccessor(0);
                        }
                    } else {
                        // missing actual parameter;
                        // we have already generated a warning for this during conversion;
                        // simply ignore it (=ok, is exactly what PHP does)
                    }
                }
            }
        }

        // reset all local variables that belong to the symbol table of the
        // caller; shortcut: if the caller is main, we don't have to do
        // this (since there are no real local variables in the main function)
        SymbolTable callerSymTab = this.caller.getSymbolTable();
        if (!callerSymTab.isMain()) {
            // only do this for non-recursive calls;
            // EFF: it might be better to reset everything except the formal params;
            // TODO: also think about correctness
            if (!(callee == caller)) {
                out.resetVariables(callerSymTab);
            }
        } else {
            // for the main function, we can at least reset the temporary variables
            out.resetTemporaries(callerSymTab);
        }

        return out;
    }
}