package com.ercatcher.ConcurrencyAnalysis;

import com.ercatcher.LOG;
import com.ercatcher.Util;
import com.ercatcher.ConcurrencyAnalysis.C3G.SInvokeNode;
import com.ercatcher.ConcurrencyAnalysis.C2G.CInvokeNode;
import com.ercatcher.ConcurrencyAnalysis.C2G.InterMethodBox;
import com.ercatcher.ConcurrencyAnalysis.CSF.MethodBox;
import com.ercatcher.ConcurrencyAnalysis.CSF.UNode;
import com.ercatcher.memory.InitAllocMemLoc;
import com.ercatcher.memory.MemoryLocation;
import com.ercatcher.ConcurrencyAnalysis.CSF.UInvokeNode;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JInterfaceInvokeExpr;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.JSpecialInvokeExpr;
import soot.jimple.internal.JVirtualInvokeExpr;
import soot.jimple.spark.pag.AllocNode;
import soot.jimple.spark.pag.Node;
import soot.jimple.spark.sets.P2SetVisitor;
import soot.jimple.spark.sets.PointsToSetInternal;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.scalar.Pair;

import java.util.*;

public class ServiceCaSFGenerator implements LibraryCaSFGenerator {
    private final static String interimStartMethodName = "interimStartMethod";
    private final static String interimBindMethodName = "interimBindMethod";

    public Set<MethodBox> getAddedMethodBoxes() {
        return addedMethodBoxes;
    }

    private Set<MethodBox> addedMethodBoxes = new HashSet<>();

    public ServiceCaSFGenerator(SootClass sootClass) {

        addStaticSelf(sootClass);
//        addedMethodBoxes.add(addInterimStartMethod(sootClass)); // TODO
        addedMethodBoxes.add(addInterimBindMethod(sootClass));
    }

    private static MethodBox addInterimBindMethod(SootClass serviceClass) {
        SootClass serviceConnectionClass = Scene.v().getSootClass("android.content.ServiceConnection");
        SootMethod interimBindMethod = new SootMethod(interimBindMethodName, Collections.singletonList(serviceConnectionClass.getType()), VoidType.v(), Modifier.STATIC + Modifier.PUBLIC);
        JimpleBody body = new JimpleBody(interimBindMethod);
        interimBindMethod.setActiveBody(body);
        serviceClass.addMethod(interimBindMethod);
        return new MethodBox(interimBindMethod, () -> {
            Set<UNode> result = new HashSet<>();
            Local tmpLocal = Jimple.v().newLocal("tmpLocal", VoidType.v());
            SootMethod initMethod = Util.v().findMethod(serviceClass, "<init>");
            Stmt initStmt = new JInvokeStmt(new JSpecialInvokeExpr(tmpLocal, initMethod.makeRef(), new ArrayList<>()));
            Scene.v().getCallGraph().addEdge(new Edge(interimBindMethod, initStmt, initMethod));
            UNode initNode = new UInvokeNode(initStmt, false);
            SootMethod onCreateMethod = Util.v().findMethod(serviceClass, "onCreate");
            Stmt onCreateStmt = new JInvokeStmt(new JVirtualInvokeExpr(tmpLocal, onCreateMethod.makeRef(), new ArrayList<>()));
            Scene.v().getCallGraph().addEdge(new Edge(interimBindMethod, onCreateStmt, onCreateMethod));
            UNode onCreateNode = new UInvokeNode(onCreateStmt, true);
            SootMethod onBindMethod = Util.v().findMethod(serviceClass, "onBind");
            Stmt onBindStmt = new JInvokeStmt(new JVirtualInvokeExpr(tmpLocal, onBindMethod.makeRef(), new ArrayList<>()));
            Scene.v().getCallGraph().addEdge(new Edge(interimBindMethod, onBindStmt, onBindMethod));
            UNode onBindNode = new UInvokeNode(onBindStmt, true);
            SootMethod onServiceConnectedMethod = Scene.v().grabMethod("<android.content.ServiceConnection: void onServiceConnected(android.content.ComponentName,android.os.IBinder)>");
            Stmt onServiceConnectedStmt = new JInvokeStmt(new JInterfaceInvokeExpr(tmpLocal, onServiceConnectedMethod.makeRef(), new ArrayList<>()));
            UNode onServiceConnectedNode = new UInvokeNode(onServiceConnectedStmt, true, true);
            SootMethod onServiceDisconnectedMethod = Scene.v().grabMethod("<android.content.ServiceConnection: void onServiceDisconnected(android.content.ComponentName)>");
            Stmt onServiceDisconnectedStmt = new JInvokeStmt(new JInterfaceInvokeExpr(tmpLocal, onServiceDisconnectedMethod.makeRef(), new ArrayList<>()));
            UNode onServiceDisconnectedNode = new UInvokeNode(onServiceDisconnectedStmt, true, true);
            result.add(initNode);
            initNode.addChild(onCreateNode);
//            onCreateNode.addChild(onBindNode); // TODO: in case of forward happens-before it should be uncommented
            initNode.addChild(onBindNode);
//            result.add(onBindNode); // TODO: in case of forward happens-before it should be commented
            onBindNode.addChild(onServiceConnectedNode);
//            result.add(onServiceConnectedNode); // TODO: if onBind is not important
            onServiceConnectedNode.addChild(onServiceDisconnectedNode);

            return result;
        });
    }

    public boolean canDetectTarget(MethodBox sourceBox, UInvokeNode uInvokeNode, MethodBox targetBox){

        return Util.v().isSubclass(targetBox.getSource().getDeclaringClass().getType(), "android.content.Context") && targetBox.getSource().getName().equals("bindService");
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
            if(possibleTargetClass != null){
                SootMethod possibleTargetMethod = Util.v().findMethod(possibleTargetClass, ServiceCaSFGenerator.interimBindMethodName);
                if (possibleTargetMethod == null || methodToInterMethodBox.get(possibleTargetMethod) == null) {
                    LOG.logln(String.format("The CaSF of bind service %s is not initialized", stmt), LOG.ERROR);
                    continue;
                }
                detectPendingMethods(methodToInterMethodBox, stmt, possibleTargetMethod);
                return Collections.singletonList(new Pair<>(methodToInterMethodBox.get(possibleTargetMethod), true));
            }
        }
        SootClass serviceClass = Scene.v().getSootClass("android.app.Service");
        SootMethod possibleTargetMethod = Util.v().findMethod(serviceClass, ServiceCaSFGenerator.interimBindMethodName);
        if(possibleTargetMethod != null){
            detectPendingMethods(methodToInterMethodBox, stmt, possibleTargetMethod);
            return Collections.singletonList(new Pair<>(methodToInterMethodBox.get(possibleTargetMethod), true));
        }
        return null;
    }

    private void detectPendingMethods(Map<SootMethod, InterMethodBox> methodToInterMethodBox, Stmt stmt, SootMethod possibleTargetMethod) {
        if (methodToInterMethodBox.get(possibleTargetMethod).getSource().getPendingNodes().size() > 0) {
            Local serviceConnectionLocal = (Local) stmt.getInvokeExpr().getArg(1);
            final SootMethod[] onServiceConnectedMethod = {null};
            final SootMethod[] onServiceDisconnectedMethod = {null};
            if (!serviceConnectionLocal.getType().toString().equals("android.content.ServiceConnection")) {
                if(Scene.v().getSootClass(serviceConnectionLocal.getType().toString()).declaresMethod("void onServiceConnected(android.content.ComponentName,android.os.IBinder)"))
                    onServiceConnectedMethod[0] = Scene.v().getSootClass(serviceConnectionLocal.getType().toString()).getMethod("void onServiceConnected(android.content.ComponentName,android.os.IBinder)");
                if(Scene.v().getSootClass(serviceConnectionLocal.getType().toString()).declaresMethod("void onServiceDisconnected(android.content.ComponentName)"))
                    onServiceDisconnectedMethod[0] = Scene.v().getSootClass(serviceConnectionLocal.getType().toString()).getMethod("void onServiceDisconnected(android.content.ComponentName)");
            } else {
                PointsToSetInternal pts = (PointsToSetInternal) Scene.v().getPointsToAnalysis().reachingObjects(serviceConnectionLocal);
                if (!pts.isEmpty()) {
                    pts.forall(new P2SetVisitor() {
                        @Override
                        public void visit(Node node) {
                            AllocNode allocNode = (AllocNode) node;
                            SootClass targetOnServiceConnectedClass = Scene.v().getSootClass(((NewExpr) allocNode.getNewExpr()).getBaseType().toString());
                            onServiceConnectedMethod[0] = targetOnServiceConnectedClass.getMethod("void onServiceConnected(android.content.ComponentName,android.os.IBinder)");
                            onServiceDisconnectedMethod[0] = targetOnServiceConnectedClass.getMethod("void onServiceDisconnected(android.content.ComponentName)");
                        }
                    });
                }
            }

            for (UInvokeNode pendingNode : methodToInterMethodBox.get(possibleTargetMethod).getSource().getPendingNodes()) {
                Stmt pendingStmt = pendingNode.getSource();
                if (pendingStmt.getInvokeExpr().getMethod().getName().equals("onServiceConnected")) {
                    if (onServiceConnectedMethod[0] != null) {
                        pendingNode.addTarget(methodToInterMethodBox.get(onServiceConnectedMethod[0]).getSource());
                    }
                } else if (pendingStmt.getInvokeExpr().getMethod().getName().equals("onServiceDisconnected")) {
                    if (onServiceDisconnectedMethod[0] != null)
                        pendingNode.addTarget(methodToInterMethodBox.get(onServiceDisconnectedMethod[0]).getSource());
                }
            }
        }
    }

    @Override
    public ThreadLattice determineThread(SInvokeNode sInvokeNode) {
        ThreadLattice result = ThreadLatticeManager.getUNDETERMINED();
        CInvokeNode cInvokeNode = (CInvokeNode) sInvokeNode.getSource();
        UInvokeNode uInvokeNode = (UInvokeNode) cInvokeNode.getSource();
        if(uInvokeNode == null){
            LOG.logln(String.format("Why UInvokeNode of %s is null? Service CaSF", cInvokeNode), LOG.ERROR);
            return result;
        }
        Stmt stmt = uInvokeNode.getSource();
        if(stmt == null)
            return result;
        if(!stmt.containsInvokeExpr())
            return result;
        InvokeExpr invokeExpr = stmt.getInvokeExpr();
        if (Util.v().isSubclass(invokeExpr.getMethod().getDeclaringClass().getType(), "android.app.Service")) {
            if (Util.v().isSubclass(invokeExpr.getMethod().getDeclaringClass().getType(), "android.app.IntentService")) {
                if (invokeExpr.getMethod().getName().equals("onHandleIntent"))
                    return ThreadLatticeManager.getIntentServiceThreadLattice();
                else
                    return ThreadLatticeManager.getUIThreadLattice();
            } else
                return ThreadLatticeManager.getUIThreadLattice();
        } else if (Util.v().isSubclass(invokeExpr.getMethod().getDeclaringClass().getType(), "android.content.ServiceConnection")) {
            if(invokeExpr.getMethod().getName().equals("onServiceDisconnected")) {
                sInvokeNode.setDelay(2);
                return ThreadLatticeManager.getUIThreadLattice();
            }
            return ThreadLatticeManager.getUIThreadLattice();
        }
        else if (invokeExpr.getMethod().getName().equals("startService")){
            if(sInvokeNode.getTarget().getSource().toString().contains("onHandleIntent"))
                return ThreadLatticeManager.getIntentServiceThreadLattice();
            else
                return ThreadLatticeManager.getUIThreadLattice();
        }
        else if(invokeExpr.getMethod().getName().equals("bindService")) {
            return ThreadLatticeManager.getUIThreadLattice();
        }
        return result;
    }

    private static SootMethod addInterimStartMethod(SootClass sootClass) {
//        SootMethod interimStartMethod = new SootMethod(interimStartMethodName, null, VoidType.v(), Modifier.STATIC + Modifier.PUBLIC);
//        JimpleBody body = new JimpleBody(interimStartMethod);
//        interimStartMethod.setActiveBody(body);
//        sootClass.addMethod(interimStartMethod);
//        Local tmpLocal = Jimple.v().newLocal("tmpRef_" + sootClass.toString().replace(".", "_"), RefType.v(sootClass.getName()));
//        body.getLocals().add(tmpLocal);
//        Stmt newStmt = new JAssignStmt(tmpLocal, new JNewExpr(RefType.v(sootClass.getName())));
//        SootMethod initMethod = Util.v().findMethod(sootClass, "<init>");
//        Stmt initStmt = new JInvokeStmt(new JSpecialInvokeExpr(tmpLocal, initMethod.makeRef(), new ArrayList<>()));
//        CallGraphUtil.addedEdges.add(new Edge(interimStartMethod, initStmt, initMethod));
//        SootMethod onCreateMethod = Util.v().findMethod(sootClass, "onCreate");
//        Stmt onCreateStmt = new JInvokeStmt(new JVirtualInvokeExpr(tmpLocal, onCreateMethod.makeRef(), new ArrayList<>()));
//        CallGraphUtil.addedEdges.add(new Edge(interimStartMethod, onCreateStmt, onCreateMethod));
//        SootMethod onStartMethod = Util.v().findMethod(sootClass, "onStartCommand");
//        Stmt onStartStmt = new JInvokeStmt(new JVirtualInvokeExpr(tmpLocal, onStartMethod.makeRef(), new ArrayList<>()));
//        CallGraphUtil.addedEdges.add(new Edge(interimStartMethod, onStartStmt, onStartMethod));
//        Stmt jmpStmt = new JIfStmt(Jimple.v().newNeExpr(tmpLocal, NullConstant.v()), onStartStmt);
//        Stmt retStmt = new JReturnVoidStmt();
//        body.getUnits().add(newStmt);
//        body.getUnits().add(initStmt);
//        body.getUnits().add(jmpStmt);
//        body.getUnits().add(onCreateStmt);
//        body.getUnits().add(onStartStmt);
//        body.getUnits().add(retStmt);
//        interimStartMethod.setActiveBody(body);
//        sootClass.addMethod(interimStartMethod);
//        return interimStartMethod;
        return null;
    }

    private static void addStaticSelf(SootClass sootClass) {
        SootField staticServiceField = new SootField("staticServiceField", sootClass.getType(), Modifier.STATIC + Modifier.PUBLIC);
        sootClass.addField(staticServiceField);
    }
}
