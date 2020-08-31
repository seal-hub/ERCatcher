package com.ercatcher.RaceDetector;

import com.ercatcher.ConcurrencyAnalysis.C3G.C3GTask;
import com.ercatcher.ConcurrencyAnalysis.C2G.CInvokeNode;
import soot.SootMethod;
import soot.jimple.Stmt;

import java.util.List;

public class CventContextMethod extends ContextMethod {
    public C3GTask getMyC3GTask() {
        return myC3GTask;
    }

    private C3GTask myC3GTask;

    CventContextMethod(SootMethod method, C3GTask c3GTask) {
        super(method, c3GTask.getStart(), c3GTask.getEnd());
        this.myC3GTask = c3GTask;
    }

    @Override
    public String toString() {
        return "CventCM " + myC3GTask.completeToString()+"->"+getMethod();
    }

    @Override
    public String getDetailedContextString() {
        C3GTask target = myC3GTask;
        StringBuilder result = new StringBuilder();
        List<CInvokeNode> callerSites = target.getCallerSites();
        for (int i = 0; i < callerSites.size()-1; i++) {
            CInvokeNode callerNode = callerSites.get(i);
            Stmt stmt = callerNode.getSource().getSource();
            String stmtLine = "";
            if(stmt != null && stmt.getJavaSourceStartLineNumber() > 0){
                stmtLine = Integer.toString(stmt.getJavaSourceStartLineNumber());
            }
            if(i>0)
                stmtLine =  ": " +stmtLine +"\n ~> ";
            result.append(stmtLine).append(callerNode.getTarget().getSource().toString());
        }
        if(callerSites.size() > 0)
            result.append("\n");
        result.append(getMethod().toString()).append(" @ ").append(myC3GTask.getThreadLattice());
        return result.toString();
    }
}
