package com.ercatcher.ConcurrencyAnalysis;

import com.ercatcher.LOG;
import com.ercatcher.Util;
import com.ercatcher.ConcurrencyAnalysis.C2G.CInvokeNode;
import com.ercatcher.ConcurrencyAnalysis.C2G.InterMethodBox;
import com.ercatcher.ConcurrencyAnalysis.CSF.MethodBox;
import com.ercatcher.memory.InitAllocMemLoc;
import com.ercatcher.ConcurrencyAnalysis.C3G.SInvokeNode;
import com.ercatcher.ConcurrencyAnalysis.CSF.UInvokeNode;
import com.ercatcher.memory.FieldMemLoc;
import com.ercatcher.memory.MemoryLocation;
import soot.*;
import soot.jimple.*;
import soot.jimple.spark.pag.AllocNode;
import soot.jimple.spark.pag.Node;
import soot.jimple.spark.sets.P2SetVisitor;
import soot.jimple.spark.sets.PointsToSetInternal;
import soot.toolkits.scalar.Pair;

import java.util.*;

public class ReceiverCaSFGenerator implements LibraryCaSFGenerator {

    public Set<MethodBox> getAddedMethodBoxes() {
        return addedMethodBoxes;
    }

    private Set<MethodBox> addedMethodBoxes = new HashSet<>();

    public ReceiverCaSFGenerator(SootClass sootClass) {
    }

    public boolean canDetectTarget(MethodBox sourceBox, UInvokeNode uInvokeNode, MethodBox targetBox){
        return Util.v().isSubclass(targetBox.getSource().getDeclaringClass().getType(), "android.content.Context") && targetBox.getSource().getName().equals("registerReceiver");
    }

    public List<Pair<InterMethodBox, Boolean>> detectTargets(MethodBox sourceBox, UInvokeNode uInvokeNode, MethodBox targetBox, Map<SootMethod, InterMethodBox> methodToInterMethodBox){
        if (!canDetectTarget(sourceBox, uInvokeNode, targetBox))
            throw new RuntimeException(String.format("I'm not interested in this Source: %s  UNode: %s Target: %s",sourceBox, uInvokeNode, targetBox));
        Stmt stmt = uInvokeNode.getSource();
        if(!(stmt.getInvokeExpr().getArg(0) instanceof Local))
            return null;
        Local receiverLocal = (Local) stmt.getInvokeExpr().getArg(0);
        if(!Util.v().isSubclass(receiverLocal.getType(), "android.content.BroadcastReceiver")){
            LOG.logln(String.format("The argument of registerReceiver %s is not BroadcastReceiver %s ", stmt, receiverLocal.getType()), LOG.ERROR);
            return null;
        }

        if(!receiverLocal.getType().toString().equals("android.content.BroadcastReceiver")){
            SootClass receiverClass = Scene.v().getSootClass(receiverLocal.getType().toString());
            SootMethod possibleTargetMethod = Util.v().findMethod(receiverClass, "onReceive");
            if(possibleTargetMethod != null){
                return Collections.singletonList(new Pair<>(methodToInterMethodBox.get(possibleTargetMethod), true));
            }
        }
        // Intra-Analysis
        if(sourceBox.getIntraAllocationAnalysis() != null) {
            for (MemoryLocation memoryLocation : sourceBox.getIntraAllocationAnalysis().getMemLocsBefore(stmt, receiverLocal)) {
                if (memoryLocation instanceof InitAllocMemLoc) {
                    InitAllocMemLoc initAllocMemLoc = (InitAllocMemLoc) memoryLocation;
                    SpecialInvokeExpr initExpr = initAllocMemLoc.getInitExpr();
                    SootClass receiverClass = initExpr.getMethod().getDeclaringClass();
                    List<Pair<InterMethodBox, Boolean>> possibleTargetMethod = getPossibleTarget(methodToInterMethodBox, receiverClass);
                    if (possibleTargetMethod != null)
                        return possibleTargetMethod;
                }
                if (memoryLocation instanceof FieldMemLoc) {
                    FieldMemLoc fieldMemLoc = (FieldMemLoc) memoryLocation;
                    SootClass receiverClass = Scene.v().getSootClassUnsafe(fieldMemLoc.getField().getType().toString());
                    List<Pair<InterMethodBox, Boolean>> possibleTargetMethod = getPossibleTarget(methodToInterMethodBox, receiverClass);
                    if (possibleTargetMethod != null)
                        return possibleTargetMethod;
                }
            }
        }
        // Inter-Analysis
        PointsToSetInternal pts = (PointsToSetInternal) Scene.v().getPointsToAnalysis().reachingObjects(receiverLocal);
        final SootClass[] receiverClass = {null};
        if (!pts.isEmpty()) {
            pts.forall(new P2SetVisitor() {
                @Override
                public void visit(Node node) {
                    AllocNode allocNode = (AllocNode) node;
                    NewExpr newHandlerExpr = (NewExpr) allocNode.getNewExpr();
                    receiverClass[0] = newHandlerExpr.getBaseType().getSootClass();
                }
            });
        }
        List<Pair<InterMethodBox, Boolean>> possibleTargetMethod = getPossibleTarget(methodToInterMethodBox, receiverClass[0]);
        if (possibleTargetMethod != null)
            return possibleTargetMethod;
        return null;
    }

    private List<Pair<InterMethodBox, Boolean>> getPossibleTarget(Map<SootMethod, InterMethodBox> methodToInterMethodBox, SootClass receiverClass) {
        if(receiverClass == null || receiverClass.toString().equals("android.content.BroadcastReceiver"))
            return null;
        SootMethod possibleTargetMethod = Util.v().findMethod(receiverClass, "onReceive");
        if(possibleTargetMethod != null)
            return Collections.singletonList(new Pair<>(methodToInterMethodBox.get(possibleTargetMethod), true));
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
        if (Util.v().isSubclass(invokeExpr.getMethod().getDeclaringClass().getType(), "android.content.BroadcastReceiver")) {
            if(invokeExpr.getMethod().getName().equals("onServiceDisconnected")) {
                sInvokeNode.setDelay(2);
                return ThreadLatticeManager.getUIThreadLattice();
            }
        }
        else if (invokeExpr.getMethod().getName().equals("registerReceiver")) {
            sInvokeNode.setDelay(2);
            return ThreadLatticeManager.getUIThreadLattice();
        }
        return result;
    }
}
