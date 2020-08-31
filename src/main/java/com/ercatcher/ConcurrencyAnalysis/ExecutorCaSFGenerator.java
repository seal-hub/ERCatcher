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

public class ExecutorCaSFGenerator implements LibraryCaSFGenerator {

    public Set<MethodBox> getAddedMethodBoxes() {
        return addedMethodBoxes;
    }

    private Set<MethodBox> addedMethodBoxes = new HashSet<>();

    public ExecutorCaSFGenerator(SootClass sootClass) { }

    public boolean canDetectTarget(MethodBox sourceBox, UInvokeNode uInvokeNode, MethodBox targetBox){
        Stmt stmt = uInvokeNode.getSource();
        if(!stmt.containsInvokeExpr() || !(stmt.getInvokeExpr() instanceof InstanceInvokeExpr))
            return false;
        InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr) stmt.getInvokeExpr();
        Local base = (Local) instanceInvokeExpr.getBase();
        if(Util.v().isSubclass(base.getType(), "java.util.concurrent.Executor"))
            return true;
        return false;
    }

    public List<Pair<InterMethodBox, Boolean>> detectTargets(MethodBox sourceBox, UInvokeNode uInvokeNode, MethodBox targetBox, Map<SootMethod, InterMethodBox> methodToInterMethodBox){
        if (!canDetectTarget(sourceBox, uInvokeNode, targetBox))
            throw new RuntimeException(String.format("I'm not interested in this Source: %s  UNode: %s Target: %s",sourceBox, uInvokeNode, targetBox));
        Stmt stmt = uInvokeNode.getSource();
        InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr) stmt.getInvokeExpr();
        Local base = (Local) instanceInvokeExpr.getBase();
        // Intra-Analysis
        if(sourceBox.getIntraAllocationAnalysis() != null) {
            for (MemoryLocation memoryLocation : sourceBox.getIntraAllocationAnalysis().getMemLocsBefore(stmt, base, true)) {
                if (memoryLocation instanceof InitAllocMemLoc) {
                    InitAllocMemLoc initAllocMemLoc = (InitAllocMemLoc) memoryLocation;
                    SpecialInvokeExpr initExpr = initAllocMemLoc.getInitExpr();
                    List<Pair<InterMethodBox, Boolean>> runnableMethod = findThreadRunnableMethods(methodToInterMethodBox, initExpr);
                    if (runnableMethod != null)
                        return runnableMethod;
                }
            }
        }
        // Inter-Analysis
        PointsToSetInternal pts = (PointsToSetInternal) Scene.v().getPointsToAnalysis().reachingObjects(base);
        final SpecialInvokeExpr[] initExpr = {null};
        if (!pts.isEmpty()) {
            pts.forall(new P2SetVisitor() {
                @Override
                public void visit(Node node) {
                    AllocNode allocNode = (AllocNode) node;
                    if(allocNode.getNewExpr() instanceof NewExpr) {
                        NewExpr newThreadExpr = (NewExpr) allocNode.getNewExpr();
                        Local threadLocal = null;
                        for (Unit unit : allocNode.getMethod().getActiveBody().getUnits()) {
                            Stmt aStmt = (Stmt) unit;
                            if (aStmt instanceof AssignStmt) {
                                AssignStmt assignStmt = (AssignStmt) unit;
                                if (assignStmt.getRightOp().equals(newThreadExpr)) {
                                    threadLocal = (Local) assignStmt.getLeftOp();
                                }
                            }
                            if (threadLocal != null && aStmt.containsInvokeExpr() && aStmt.getInvokeExpr() instanceof SpecialInvokeExpr) {
                                SpecialInvokeExpr specialInvokeExpr = (SpecialInvokeExpr) aStmt.getInvokeExpr();
                                if (specialInvokeExpr.getBase().equals(threadLocal)) {
                                    initExpr[0] = specialInvokeExpr;
                                    return;
                                }
                            }
                        }
                    }
                }
            });
            if(initExpr[0] != null){
                List<Pair<InterMethodBox, Boolean>> runnableMethod = findThreadRunnableMethods(methodToInterMethodBox, initExpr[0]);
                if (runnableMethod != null)
                    return runnableMethod;
            }
        }
        return null;
    }

    private List<Pair<InterMethodBox, Boolean>> findThreadRunnableMethods(Map<SootMethod, InterMethodBox> methodToInterMethodBox, SpecialInvokeExpr initExpr) {
        // TODO: this is imprecise
        for (int i = 0; i < initExpr.getMethod().getParameterCount(); i++) {
            Type type = initExpr.getMethod().getParameterType(i);
            if (Util.v().isSubclass(type, "java.lang.Runnable")) {
                Local runnableLocal = (Local) initExpr.getArg(i);
                if (!runnableLocal.getType().toString().equals("java.lang.Runnable")) {
                    SootClass runnableClass = Scene.v().getSootClass(runnableLocal.getType().toString());
                    if (!runnableClass.isPhantom() && runnableClass.declaresMethod("void run()")) {
                        SootMethod runnableMethod = runnableClass.getMethod("void run()");
                        if (!runnableMethod.isPhantom()) {
                            return Collections.singletonList(new Pair<>(methodToInterMethodBox.get(runnableMethod), true));
                        }
                    }
                }
                break;
            }
        }
        Local threadLocal = (Local) initExpr.getBase();
        SootClass threadClass = Scene.v().getSootClassUnsafe(threadLocal.getType().toString());
        if(threadClass != null ){
            SootMethod runnableMethod = threadClass.getMethodUnsafe("void run()");
            if (runnableMethod != null) {
                return Collections.singletonList(new Pair<>(methodToInterMethodBox.get(runnableMethod), true));
            }
        }
        return null;
    }

    public ThreadLattice determineThread(SInvokeNode sInvokeNode){
        // TODO: it's not context sensitive
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
        if (Util.v().isSubclass(base.getType(), "java.lang.Thread")){
            PointsToSetInternal pts = (PointsToSetInternal) Scene.v().getPointsToAnalysis().reachingObjects(base);
            if(pts.size() == 1) {
                final ThreadLattice[] threadLattice = {null};
                if (!pts.isEmpty()) {
                    pts.forall(new P2SetVisitor() {
                        @Override
                        public void visit(Node node) {
                            AllocNode allocNode = (AllocNode) node;
                            threadLattice[0] = ThreadLatticeManager.getNewAllocThreadLattice(allocNode);
                        }
                    });
                }
                if(threadLattice[0] != null)
                    return threadLattice[0];
            }
            Set<MemoryLocation> threadSources = cInvokeNode.getMyInterMethodBox().getSource().getIntraAllocationAnalysis().getMemLocsBefore(stmt, base, true);
            if (threadSources.size() != 1) {
                LOG.logln(String.format("More than one sources or no source for the origin thread in CInvokeNode %s and unit %s", cInvokeNode, stmt), LOG.ERROR);
                return ThreadLatticeManager.getUNKNOWNThreadLattice();
            } else {
                return ThreadLatticeManager.getNewThreadLattice((MemoryLocation) threadSources.toArray()[0]);
            }
        }
        return result;
    }
}
