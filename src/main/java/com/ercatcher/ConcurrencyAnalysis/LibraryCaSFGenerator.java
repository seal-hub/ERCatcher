package com.ercatcher.ConcurrencyAnalysis;

import com.ercatcher.ConcurrencyAnalysis.C3G.SInvokeNode;
import com.ercatcher.ConcurrencyAnalysis.C2G.InterMethodBox;
import com.ercatcher.ConcurrencyAnalysis.CSF.MethodBox;
import com.ercatcher.ConcurrencyAnalysis.CSF.UInvokeNode;
import soot.SootMethod;
import soot.toolkits.scalar.Pair;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface LibraryCaSFGenerator {
    public Set<MethodBox> getAddedMethodBoxes();
    public boolean canDetectTarget(MethodBox sourceBox, UInvokeNode uInvokeNode, MethodBox targetBox);
    public List<Pair<InterMethodBox, Boolean>> detectTargets(MethodBox sourceBox, UInvokeNode uInvokeNode, MethodBox targetBox, Map<SootMethod, InterMethodBox> methodToInterMethodBox);
    public ThreadLattice determineThread(SInvokeNode sInvokeNode);
}
