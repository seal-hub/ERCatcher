package com.ercatcher.ConcurrencyAnalysis;

import com.ercatcher.LOG;
import com.ercatcher.Util;
import com.ercatcher.ConcurrencyAnalysis.C3G.SInvokeNode;
import com.ercatcher.ConcurrencyAnalysis.C2G.CInvokeNode;
import com.ercatcher.ConcurrencyAnalysis.C2G.InterMethodBox;
import com.ercatcher.ConcurrencyAnalysis.CSF.MethodBox;
import com.ercatcher.ConcurrencyAnalysis.CSF.UInvokeNode;
import soot.*;
import soot.jimple.*;
import soot.toolkits.scalar.Pair;

import java.util.*;

public class TimerCaSFGenerator implements LibraryCaSFGenerator {

    public Set<MethodBox> getAddedMethodBoxes() {
        return addedMethodBoxes;
    }

    private Set<MethodBox> addedMethodBoxes = new HashSet<>();

    public TimerCaSFGenerator(SootClass sootClass) { }

    public boolean canDetectTarget(MethodBox sourceBox, UInvokeNode uInvokeNode, MethodBox targetBox){
        if(!targetBox.getSource().getName().equals("run"))
            return false;
        Stmt stmt = uInvokeNode.getSource();
        if(!stmt.containsInvokeExpr() || !(stmt.getInvokeExpr() instanceof InstanceInvokeExpr))
            return false;
        InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr) stmt.getInvokeExpr();
        Local base = (Local) instanceInvokeExpr.getBase();
        return Util.v().isSubclass(base.getType(), "java.util.TimerTask");
    }

    public List<Pair<InterMethodBox, Boolean>> detectTargets(MethodBox sourceBox, UInvokeNode uInvokeNode, MethodBox targetBox, Map<SootMethod, InterMethodBox> methodToInterMethodBox){
        if (!canDetectTarget(sourceBox, uInvokeNode, targetBox))
            throw new RuntimeException(String.format("I'm not interested in this Source: %s  UNode: %s Target: %s",sourceBox, uInvokeNode, targetBox));
        return Collections.singletonList(new Pair<>(methodToInterMethodBox.get(targetBox.getSource()), true));
    }

    public ThreadLattice determineThread(SInvokeNode sInvokeNode){
//        // TODO: it's not context sensitive
        ThreadLattice result = ThreadLatticeManager.getUNDETERMINED();
        CInvokeNode cInvokeNode = (CInvokeNode) sInvokeNode.getSource();
        UInvokeNode uInvokeNode = (UInvokeNode) cInvokeNode.getSource();
        if(uInvokeNode == null){
            LOG.logln(String.format("Why UInvokeNode of %s is null? Thread CaSFGenerator", cInvokeNode), LOG.ERROR);
            return result;
        }
        Stmt stmt = uInvokeNode.getSource();
        if(stmt == null)
            return result;
        if(!stmt.containsInvokeExpr() || !(stmt.getInvokeExpr() instanceof InstanceInvokeExpr))
            return result;
        InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr) stmt.getInvokeExpr();
        Local base = (Local) instanceInvokeExpr.getBase();
        if (Util.v().isSubclass(base.getType(), "java.util.TimerTask")){
            result = ThreadLatticeManager.getUNKNOWNThreadLattice();
        }
        return result;
    }
}
