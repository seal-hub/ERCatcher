package com.ercatcher.ConcurrencyAnalysis;

import com.ercatcher.LOG;
import com.ercatcher.Util;
import com.ercatcher.ConcurrencyAnalysis.C3G.SInvokeNode;
import com.ercatcher.ConcurrencyAnalysis.C2G.CInvokeNode;
import com.ercatcher.ConcurrencyAnalysis.C2G.InterMethodBox;
import com.ercatcher.ConcurrencyAnalysis.CSF.MethodBox;
import com.ercatcher.memory.FieldMemLoc;
import com.ercatcher.memory.InitAllocMemLoc;
import com.ercatcher.memory.MemoryLocation;
import com.ercatcher.ConcurrencyAnalysis.CSF.UInvokeNode;
import soot.*;
import soot.jimple.*;
import soot.toolkits.scalar.Pair;

import java.util.*;

public class AsyncTaskCaSFGenerator implements LibraryCaSFGenerator {
    public final static String interimDoInBGMethodName = "interimDoInBG";
    public final static String executeMethodName = "execute";
    public final static String executeOnExecutorMethodName = "executeOnExecutor";
    public final static String executorClassName = "java.util.concurrent.Executor";

    public Set<MethodBox> getAddedMethodBoxes() {
        return addedMethodBoxes;
    }

    private Set<MethodBox> addedMethodBoxes = new HashSet<>();

    public AsyncTaskCaSFGenerator(SootClass sootClass) { }

    public boolean canDetectTarget(MethodBox sourceBox, UInvokeNode uInvokeNode, MethodBox targetBox){
        return Util.v().isSubclass(targetBox.getSource().getDeclaringClass().getType(),"android.os.AsyncTask") && targetBox.getSource().getName().equals("executeOnExecutor");
    }

    public List<Pair<InterMethodBox, Boolean>> detectTargets(MethodBox sourceBox, UInvokeNode uInvokeNode, MethodBox targetBox, Map<SootMethod, InterMethodBox> methodToInterMethodBox){
        if (!canDetectTarget(sourceBox, uInvokeNode, targetBox))
            throw new RuntimeException(String.format("I'm not interested in this Source: %s  UNode: %s Target: %s",sourceBox, uInvokeNode, targetBox));
        Stmt stmt = uInvokeNode.getSource();
        if(!stmt.containsInvokeExpr() || !(stmt.getInvokeExpr() instanceof InstanceInvokeExpr)){
            throw new RuntimeException("How it is executeOnExecutor?");
        }
        else {
            InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr) stmt.getInvokeExpr();
            Local base = (Local) instanceInvokeExpr.getBase();
            if(!base.getType().toString().equals("android.os.AsyncTask")){
                SootClass targetClass = Scene.v().getSootClass(base.getType().toString());
                SootMethod sootMethod = targetClass.getMethodUnsafe("java.lang.Object doInBackground(java.lang.Object[])");
                if(sootMethod != null)
                    return Collections.singletonList(new Pair<>(methodToInterMethodBox.get(sootMethod), true));
            }
            if(sourceBox.getIntraAllocationAnalysis() != null) {
                for (MemoryLocation memoryLocation : sourceBox.getIntraAllocationAnalysis().getMemLocsBefore(stmt, base)) {
                    if (memoryLocation instanceof InitAllocMemLoc) {
                        InitAllocMemLoc initAllocMemLoc = (InitAllocMemLoc) memoryLocation;
                        SpecialInvokeExpr initExpr = initAllocMemLoc.getInitExpr();
                        SootMethod initMethod = initExpr.getMethod();
                        if (!initMethod.getDeclaringClass().toString().equals("android.os.AsyncTask")) {
                            SootClass targetClass = initMethod.getDeclaringClass();
                            SootMethod sootMethod = targetClass.getMethodUnsafe("java.lang.Object doInBackground(java.lang.Object[])");
                            if(sootMethod != null)
                                return Collections.singletonList(new Pair<>(methodToInterMethodBox.get(sootMethod), true));
                        }
                    } else {
                        LOG.logln(String.format("How it is not an InitAllocMemLoc in AsyncTask? %s MemLoc: %s", stmt, memoryLocation), LOG.ERROR);
                    }
                }
            }
        }
        return null;
    }

    @Override
    public ThreadLattice determineThread(SInvokeNode sInvokeNode) {
        ThreadLattice result = ThreadLatticeManager.getUNDETERMINED();
        CInvokeNode cInvokeNode = (CInvokeNode) sInvokeNode.getSource();
        // TODO: this is because of the way onCancelled and the rest ar added. It should be fixed one the interimDoInBackground method is created.
        String cInvokeMethodName = cInvokeNode.getTarget().getSource().getSource().getName();
        if (Util.v().isSubclass(cInvokeNode.getTarget().getSource().getSource().getDeclaringClass().getType(), "android.os.AsyncTask")
                && (cInvokeMethodName.equals("onCancelled") || cInvokeMethodName.equals("onPostExecute") || cInvokeMethodName.equals("onProgressUpdate"))){
            return ThreadLatticeManager.getUIThreadLattice();
        }
        UInvokeNode uInvokeNode = (UInvokeNode) cInvokeNode.getSource();
        if(uInvokeNode == null){
            LOG.logln(String.format("Why UInvokeNode of %s is null? AsyncTaskCaSFGenerator", cInvokeNode), LOG.ERROR);
            return result;
        }
        Stmt stmt = uInvokeNode.getSource();
        if(stmt == null)
            return result;
        if(!stmt.containsInvokeExpr())
            return result;
        InvokeExpr invokeExpr = stmt.getInvokeExpr();
        if(invokeExpr instanceof InstanceInvokeExpr) {
            InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr) invokeExpr;
            Local base = (Local) instanceInvokeExpr.getBase();
            if (Util.v().isSubclass(base.getType(), "android.os.AsyncTask")) {
                String methodName = instanceInvokeExpr.getMethod().getName();
                if (methodName.equals("execute"))
                    return ThreadLatticeManager.getAsyncSerialThreadLattice();
                else if (methodName.equals("executeOnExecutor")) {
                    Value executorValue = instanceInvokeExpr.getArg(0);
                    ThreadLattice threadLattice = ThreadLatticeManager.getAsyncParallelThreadLattice();
                    if(executorValue instanceof Local){
                        if(cInvokeNode.getMyInterMethodBox().getSource().getIntraAllocationAnalysis() != null) {
                            for (MemoryLocation memoryLocation : cInvokeNode.getMyInterMethodBox().getSource().getIntraAllocationAnalysis().getMemLocsBefore(stmt, (Local) executorValue)){
                                if(memoryLocation instanceof FieldMemLoc){
                                    if(((FieldMemLoc)memoryLocation).getField().toString().contains("SERIAL_EXECUTOR"))
                                        threadLattice = ThreadLatticeManager.getAsyncSerialThreadLattice();
                                }
                            }
                        }
                    }
                    else if(executorValue instanceof FieldRef){
                        if(((FieldRef) executorValue).getField().toString().contains("SERIAL_EXECUTOR"))
                            threadLattice = ThreadLatticeManager.getAsyncSerialThreadLattice();
                    }
                    // TODO: check if the mem loc of executorValue is android.os.AsyncTask.THREAD_POOL_EXECUTOR
                    return threadLattice;
                } else {
                    LOG.logln(String.format("Unrecognized source of AsyncCall: %s in %s", instanceInvokeExpr, cInvokeNode), LOG.ERROR);
                }
            }
        }
        else {
            if(Util.v().isSubclass(invokeExpr.getMethod().getDeclaringClass().getType(), "android.os.AsyncTask"))
                LOG.logln(String.format("Static invocation Unrecognized source of Async Task: %s in %s", invokeExpr, cInvokeNode), LOG.ERROR);
        }
        return result;
    }
}
