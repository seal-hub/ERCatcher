package com.ercatcher.RaceDetector;

import com.ercatcher.ConcurrencyAnalysis.C3G.C3GTask;
import com.ercatcher.ConcurrencyAnalysis.C3G.SInvokeNode;
import com.ercatcher.ConcurrencyAnalysis.C2G.CInvokeNode;
import soot.SootMethod;
import soot.jimple.Stmt;

import java.util.List;

public class CallerContextMethod extends ContextMethod {
    private SInvokeNode myInvoker;

    CallerContextMethod(SootMethod method, SInvokeNode myInvoker) {
        super(method, myInvoker, (myInvoker.getTarget()).getEnd());
        this.myInvoker = myInvoker;
    }

    public SInvokeNode getMyInvoker() {
        return myInvoker;
    }

    @Override
    public String toString() {
        return "CallerCM " + (myInvoker.getTarget()).completeToString();
    }

    @Override
    public String getDetailedContextString() {
        C3GTask target = myInvoker.getTarget();
        StringBuilder result = new StringBuilder();
        List<CInvokeNode> callerSites = target.getCallerSites();
        for (int i = 0; i < callerSites.size(); i++) {
            CInvokeNode callerNode = callerSites.get(i);
            if (callerNode.getTarget().getSource().getSource().equals(getMethod()))
                break;
            Stmt stmt = callerNode.getSource().getSource();
            String stmtLine = "";
            if (stmt != null && stmt.getJavaSourceStartLineNumber() > 0) {
                stmtLine = Integer.toString(stmt.getJavaSourceStartLineNumber());
            }
            if(i>0)
                stmtLine =  ": " +stmtLine +"\n ~> ";
            result.append(stmtLine).append(callerNode.getTarget().getSource().toString());
        }
        if(callerSites.size() > 0)
            result.append("\n");
        result.append(getMethod().toString()).append(" @ ").append(myInvoker.getMyC3GTask().getThreadLattice());
        return result.toString();
    }
}
