package com.ercatcher.RaceDetector;

import com.ercatcher.ConcurrencyAnalysis.C3G.C3GTask;
import com.ercatcher.ConcurrencyAnalysis.C3G.SNode;
import com.ercatcher.ConcurrencyAnalysis.C2G.CInvokeNode;
import com.ercatcher.ConcurrencyAnalysis.C2G.InterMethodBox;
import soot.SootMethod;
import soot.jimple.Stmt;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SynchCallContextMethod extends ContextMethod {
    public SNode getAfterMeSNode() {
        return afterMeSNode;
    }

    private SNode afterMeSNode;
    private SynchCallContextMethod(SootMethod method, SNode beforeMethodNode, SNode afterMeSNode) {
        super(method, beforeMethodNode, afterMeSNode);
        this.afterMeSNode = afterMeSNode;
    }

    @Override
    public String getDetailedContextString() {
        C3GTask target = getLastCvent();
        StringBuilder result = new StringBuilder();
        List<CInvokeNode> callerSites = target.getCallerSites();
        for (int i = 0; i < callerSites.size(); i++) {
            CInvokeNode callerNode = callerSites.get(i);
            Stmt stmt = null;
            if(callerNode.getSource() != null)
                stmt = callerNode.getSource().getSource();
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
        result.append(getMethod().toString()).append(" @ ").append(getBeforeMethodNode().getMyC3GTask().getThreadLattice());
        return result.toString();

    }

    static Set<SynchCallContextMethod> makeSynchCallContextMethods(SootMethod sootMethod, SNode afterMeSNode, Map<SootMethod, InterMethodBox> methodToInterMethodBox){
        Set<SynchCallContextMethod> result = new HashSet<>();
//        for(AbstractNode parent : afterMeSNode.getParents()){
//            SNode sParent = (SNode)parent;
//            result.add(new SynchCallContextMethod(sootMethod, sParent, afterMeSNode));
//        }
        for(SNode parent : afterMeSNode.mayBeCalledBeforeMeMap.keySet()){
            if(afterMeSNode.mayBeCalledBeforeMeMap.get(parent).contains(methodToInterMethodBox.get(sootMethod)))
                result.add(new SynchCallContextMethod(sootMethod, parent, afterMeSNode));
        }
        return result;
    }

    @Override
    public String toString() {
        return "SyncCallCM " + afterMeSNode.getMyC3GTask().completeToString()+"->"+getMethod().getName();
    }

}
