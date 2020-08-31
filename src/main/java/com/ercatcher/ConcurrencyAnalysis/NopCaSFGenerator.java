package com.ercatcher.ConcurrencyAnalysis;

import com.ercatcher.ConcurrencyAnalysis.C3G.SInvokeNode;
import com.ercatcher.ConcurrencyAnalysis.C2G.InterMethodBox;
import com.ercatcher.ConcurrencyAnalysis.CSF.MethodBox;
import com.ercatcher.ConcurrencyAnalysis.CSF.UInvokeNode;
import soot.*;
import soot.jimple.*;
import soot.toolkits.scalar.Pair;

import java.util.*;

public class NopCaSFGenerator implements LibraryCaSFGenerator {
    private final static String nopMethodName = "NOP_ASYNC_METHOD";
    public static SootMethod nopAsyncMethod;
    public static MethodBox nopAsyncMethodBox;

    public Set<MethodBox> getAddedMethodBoxes() {
        return addedMethodBoxes;
    }

    private Set<MethodBox> addedMethodBoxes = new HashSet<>();

    public NopCaSFGenerator(SootClass dummyMainClass) {
        addedMethodBoxes.add(addNopMethod(dummyMainClass));
    }

    private static MethodBox addNopMethod(SootClass dummyMainClass) {
        nopAsyncMethod = new SootMethod(nopMethodName, null, VoidType.v(), Modifier.STATIC + Modifier.PUBLIC);
        JimpleBody body = new JimpleBody(nopAsyncMethod);
        nopAsyncMethod.setActiveBody(body);
        dummyMainClass.addMethod(nopAsyncMethod);
        nopAsyncMethodBox =  new MethodBox(nopAsyncMethod, HashSet::new);
        return nopAsyncMethodBox;
    }

    public boolean canDetectTarget(MethodBox sourceBox, UInvokeNode uInvokeNode, MethodBox targetBox){
        return false;
    }

    public List<Pair<InterMethodBox, Boolean>> detectTargets(MethodBox sourceBox, UInvokeNode uInvokeNode, MethodBox targetBox, Map<SootMethod, InterMethodBox> methodToInterMethodBox){
        if (!canDetectTarget(sourceBox, uInvokeNode, targetBox))
            throw new RuntimeException(String.format("I'm NOP and not interested in this Source: %s  UNode: %s Target: %s",sourceBox, uInvokeNode, targetBox));
        return null;
    }
    @Override
    public ThreadLattice determineThread(SInvokeNode sInvokeNode) {
        return ThreadLatticeManager.getUNDETERMINED();
    }
}
