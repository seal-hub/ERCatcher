package com.ercatcher.ConcurrencyAnalysis;

import com.ercatcher.LOG;
import com.ercatcher.Util;
import com.ercatcher.ConcurrencyAnalysis.C3G.C3GTask;
import com.ercatcher.ConcurrencyAnalysis.C2G.InterMethodBox;
import com.ercatcher.ConcurrencyAnalysis.C3G.SInvokeNode;
import com.ercatcher.ConcurrencyAnalysis.C2G.CInvokeNode;
import com.ercatcher.ConcurrencyAnalysis.CSF.MethodBox;
import com.ercatcher.ConcurrencyAnalysis.CSF.UInvokeNode;
import soot.*;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.toolkits.scalar.Pair;

import java.util.*;

public class AndroidFrameworkCaSFGenerator implements LibraryCaSFGenerator {

    public Set<MethodBox> getAddedMethodBoxes() {
        return null;
    }

    private AndroidFrameworkCaSFGenerator() { }
    private static AndroidFrameworkCaSFGenerator instance = null;
    public static AndroidFrameworkCaSFGenerator v(){
        if (instance == null)
            instance = new AndroidFrameworkCaSFGenerator();
        return instance;
    }

    public boolean canDetectTarget(MethodBox sourceBox, UInvokeNode uInvokeNode, MethodBox targetBox){
        return false;
    }

    public List<Pair<InterMethodBox, Boolean>> detectTargets(MethodBox sourceBox, UInvokeNode uInvokeNode, MethodBox targetBox, Map<SootMethod, InterMethodBox> methodToInterMethodBox){
        if (!canDetectTarget(sourceBox, uInvokeNode, targetBox))
            throw new RuntimeException(String.format("I'm not interested in this Source: %s  UNode: %s Target: %s",sourceBox, uInvokeNode, targetBox));
        return null;
    }

    @Override
    public ThreadLattice determineThread(SInvokeNode sInvokeNode) {
        ThreadLattice result = ThreadLatticeManager.getUNDETERMINED();
        CInvokeNode cInvokeNode = (CInvokeNode) sInvokeNode.getSource();
        UInvokeNode uInvokeNode = (UInvokeNode) cInvokeNode.getSource();
        C3GTask parent = sInvokeNode.getMyC3GTask();
        C3GTask child = sInvokeNode.getTarget();
        if(uInvokeNode == null){
            LOG.logln(String.format("Why UInvokeNode of %s is null? Android Framework", cInvokeNode), LOG.ERROR);
            return result;
        }
        Stmt stmt = uInvokeNode.getSource();
        if(stmt == null)
            return result;
        if(!stmt.containsInvokeExpr())
            return result;
        InvokeExpr invokeExpr = stmt.getInvokeExpr();
        if (invokeExpr.getMethod().getName().equals("runOnUiThread")) {
            return ThreadLatticeManager.getUIThreadLattice();
        }
        if(invokeExpr instanceof InstanceInvokeExpr) {
            InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr) invokeExpr;
            Local base = (Local) instanceInvokeExpr.getBase();
            SootClass baseClass = Scene.v().getSootClass(base.getType().toString());
            while(baseClass != null){
                for(SootClass inter : baseClass.getInterfaces()){
                    if(inter.getName().startsWith("android.view") || inter.getName().startsWith("android.widget"))
                        return ThreadLatticeManager.getUIThreadLattice();
                }
                if(baseClass.getName().startsWith("android.view") || baseClass.getName().startsWith("android.widget"))
                    return ThreadLatticeManager.getUIThreadLattice();
                if(baseClass.hasSuperclass())
                    baseClass = baseClass.getSuperclass();
                else
                    break;
            }
            if (Util.v().isSubclass(base.getType(), "android.view.View")) {
                return ThreadLatticeManager.getUIThreadLattice();
            }
        }
        if (parent.getSource().getSource().getSource().getDeclaringClass().toString().equals("dummyMainClass")) {
            for(CInvokeNode callerSite :sInvokeNode.getTarget().getCallerSites()){
                if(callerSite.toString().contains("onCreate") || callerSite.toString().contains("onStart") || callerSite.toString().contains("onResume"))
                    return ThreadLatticeManager.getUNDETERMINED();
            }
            LOG.logln(String.format("Unkown call from dummyMainClass to %s through %s on unit %s", child, cInvokeNode, stmt), LOG.SUPER_VERBOSE);
            return ThreadLatticeManager.getUIThreadLattice();
        }
        return result;
    }
}
