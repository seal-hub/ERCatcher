package com.ercatcher.ConcurrencyAnalysis;

import com.ercatcher.LOG;
import com.ercatcher.Util;
import com.ercatcher.ConcurrencyAnalysis.C3G.SInvokeNode;
import com.ercatcher.ConcurrencyAnalysis.C2G.CInvokeNode;
import com.ercatcher.ConcurrencyAnalysis.C2G.InterMethodBox;
import com.ercatcher.ConcurrencyAnalysis.CSF.MethodBox;
import com.ercatcher.ConcurrencyAnalysis.CSF.UInvokeNode;
import com.ercatcher.memory.InitAllocMemLoc;
import com.ercatcher.memory.MemoryLocation;
import soot.*;
import soot.jimple.*;
import soot.jimple.spark.pag.AllocNode;
import soot.jimple.spark.pag.Node;
import soot.jimple.spark.sets.P2SetVisitor;
import soot.jimple.spark.sets.PointsToSetInternal;
import soot.toolkits.scalar.Pair;

import java.util.*;

public class IntentServiceCaSFGenerator implements LibraryCaSFGenerator {

    public Set<MethodBox> getAddedMethodBoxes() {
        return addedMethodBoxes;
    }

    private Set<MethodBox> addedMethodBoxes = new HashSet<>();

    public IntentServiceCaSFGenerator(SootClass sootClass) {
    }

    public boolean canDetectTarget(MethodBox sourceBox, UInvokeNode uInvokeNode, MethodBox targetBox){
//        return false;
        return Util.v().isSubclass(targetBox.getSource().getDeclaringClass().getType(), "android.content.Context") && targetBox.getSource().getName().equals("startService");
    }

    public List<Pair<InterMethodBox, Boolean>> detectTargets(MethodBox sourceBox, UInvokeNode uInvokeNode, MethodBox targetBox, Map<SootMethod, InterMethodBox> methodToInterMethodBox){
        if (!canDetectTarget(sourceBox, uInvokeNode, targetBox))
            throw new RuntimeException(String.format("I'm not interested in this Source: %s  UNode: %s Target: %s",sourceBox, uInvokeNode, targetBox));
        Stmt stmt = uInvokeNode.getSource();
        Local intentLocal = (Local) stmt.getInvokeExpr().getArg(0);
        // Intra-Analysis
        if(sourceBox.getIntraAllocationAnalysis() != null) {
            for (MemoryLocation memoryLocation : sourceBox.getIntraAllocationAnalysis().getMemLocsBefore(stmt, intentLocal)) {
                if (memoryLocation instanceof InitAllocMemLoc) {
                    InitAllocMemLoc initAllocMemLoc = (InitAllocMemLoc) memoryLocation;
                    SpecialInvokeExpr initExpr = initAllocMemLoc.getInitExpr();
                    List<Pair<InterMethodBox, Boolean>> possibleTargetMethod = findBindServiceTargets(methodToInterMethodBox, stmt, initExpr);
                    if (possibleTargetMethod != null)
                        return possibleTargetMethod;
                } else {
                    LOG.logln(String.format("How it is not an InitAllocMemLoc? %s MemLoc: %s", stmt, memoryLocation), LOG.ERROR);
                }
            }
        }
        // Inter-Analysis
        PointsToSetInternal pts = (PointsToSetInternal) Scene.v().getPointsToAnalysis().reachingObjects(intentLocal);
        final SpecialInvokeExpr[] initExpr = {null};
        if (!pts.isEmpty()) {
            pts.forall(new P2SetVisitor() {
                @Override
                public void visit(Node node) {
                    AllocNode allocNode = (AllocNode) node;
                    NewExpr newThreadExpr = (NewExpr) allocNode.getNewExpr();
                    Local threadLocal = null;
                    for(Unit unit: allocNode.getMethod().getActiveBody().getUnits()){
                        Stmt aStmt = (Stmt) unit;
                        if(aStmt instanceof AssignStmt){
                            AssignStmt assignStmt = (AssignStmt) unit;
                            if(assignStmt.getRightOp().equals(newThreadExpr)){
                                threadLocal = (Local) assignStmt.getLeftOp();
                            }
                        }
                        if(threadLocal != null && aStmt.containsInvokeExpr() && aStmt.getInvokeExpr() instanceof SpecialInvokeExpr){
                            SpecialInvokeExpr specialInvokeExpr = (SpecialInvokeExpr) aStmt.getInvokeExpr();
                            if(specialInvokeExpr.getBase().equals(threadLocal)){
                                initExpr[0] = specialInvokeExpr;
                                return;
                            }
                        }
                    }
                }
            });
            if(initExpr[0] != null){
                List<Pair<InterMethodBox, Boolean>> possibleTargetMethod = findBindServiceTargets(methodToInterMethodBox, stmt, initExpr[0]);
                if (possibleTargetMethod != null)
                    return possibleTargetMethod;
            }
        }
        return null;
    }

    private List<Pair<InterMethodBox, Boolean>> findBindServiceTargets(Map<SootMethod, InterMethodBox> methodToInterMethodBox, Stmt stmt, SpecialInvokeExpr initExpr) {
        for (Value arg : initExpr.getArgs()) {
            SootClass possibleTargetClass = null;
            if (arg instanceof ClassConstant) {
                Type argType = ((ClassConstant) arg).toSootType();
                possibleTargetClass = Scene.v().getSootClass(argType.toString());
            }
            if(possibleTargetClass != null && !possibleTargetClass.toString().equals("android.app.IntentService")){
                SootMethod possibleTargetMethod = Util.v().findMethod(possibleTargetClass, "onHandleIntent");
                if (possibleTargetMethod == null || methodToInterMethodBox.get(possibleTargetMethod) == null) {
                    LOG.logln(String.format("The CaSF of onHandleIntnet %s is not initialized", stmt), LOG.ERROR);
                    continue;
                }
                return Collections.singletonList(new Pair<>(methodToInterMethodBox.get(possibleTargetMethod), true));
            }
        }
        return null;
    }

    @Override
    public ThreadLattice determineThread(SInvokeNode sInvokeNode) {
        ThreadLattice result = ThreadLatticeManager.getUNDETERMINED();
        CInvokeNode cInvokeNode = (CInvokeNode) sInvokeNode.getSource();
        UInvokeNode uInvokeNode = (UInvokeNode) cInvokeNode.getSource();
        if(uInvokeNode == null){
            LOG.logln(String.format("Why UInvokeNode of %s is null? Receiver CaSF", cInvokeNode), LOG.ERROR);
            return result;
        }
        Stmt stmt = uInvokeNode.getSource();
        if(stmt == null)
            return result;
        if(!stmt.containsInvokeExpr())
            return result;
        InvokeExpr invokeExpr = stmt.getInvokeExpr();
        if(sInvokeNode.getTarget().getSource().toString().contains("onHandleIntent"))
            return ThreadLatticeManager.getIntentServiceThreadLattice();
//        if (Util.v().isSubclass(invokeExpr.getMethod().getDeclaringClass().getType(), "android.app.IntentService")) {
//            if(invokeExpr.getMethod().getName().equals("onHandleIntent")) {
//                return ThreadLatticeManager.getIntentServiceThreadLattice();
//            }
//        }
        return result;
    }
}
