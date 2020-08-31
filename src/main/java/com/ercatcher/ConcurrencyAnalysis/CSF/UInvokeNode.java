package com.ercatcher.ConcurrencyAnalysis.CSF;

import com.ercatcher.ConcurrencyAnalysis.InvokeNode;
import soot.Unit;
import soot.jimple.Stmt;

import java.util.HashSet;
import java.util.Set;

public class UInvokeNode extends UNode implements InvokeNode<MethodBox> {

    private boolean asynch = false;
    private Set<MethodBox> targets = new HashSet<>();
    private boolean pendingTarget = false;

    UInvokeNode(Unit unit) {
        super((Stmt) unit);
    }

    public UInvokeNode(Unit unit, boolean asynch) {
        this(unit);
        this.asynch = asynch;
    }

    public UInvokeNode(Unit unit, boolean asynch, boolean pendingTarget) {
        this(unit, asynch);
        this.pendingTarget = pendingTarget;
    }

    boolean hasPendingTarget() {
        return pendingTarget;
    }

    @Override
    public MethodBox getTarget() {
        return null;
    }

    public Set<MethodBox> getTargets() {
        return targets;
    }

    public void addTarget(MethodBox target) {
        if (target == null)
            throw new RuntimeException("Why my target is null?");
        this.targets.add(target);
    }

    @Override
    public boolean isAsync() {
        return asynch;
    }
}
