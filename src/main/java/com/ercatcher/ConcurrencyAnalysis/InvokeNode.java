package com.ercatcher.ConcurrencyAnalysis;

public interface InvokeNode<T extends CBox> {
    public T getTarget();
    public boolean isAsync();
}
