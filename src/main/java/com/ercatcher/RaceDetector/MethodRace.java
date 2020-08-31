package com.ercatcher.RaceDetector;

import soot.SootMethod;
import soot.jimple.Stmt;

public class MethodRace extends Race<SootMethod, FieldId> {
    MethodRace(SootMethod writeEvent, SootMethod readEvent, FieldId fieldId) {
        this(writeEvent, null, readEvent, null, fieldId);
    }

    MethodRace(SootMethod writeEvent, Stmt writeStmt, SootMethod readEvent, Stmt readStmt, FieldId fieldId) {
        super(writeEvent, readEvent, fieldId);
        this.writeStmt = writeStmt;
        this.readStmt = readStmt;
    }

    MethodRace cloneMe(){
        MethodRace ret = new MethodRace(getWriteEvent(), writeStmt, getReadEvent(), readStmt, getMemoryId())
                .setUAF(this.isUAF())
                .setIfGuard(this.isIfGuard())
                .setNullAtEnd(this.isNullAtEnd());
        return ret;
    }

    public Stmt getWriteStmt() {
        return writeStmt;
    }

    public Stmt getReadStmt() {
        return readStmt;
    }

    private Stmt writeStmt;
    private Stmt readStmt;

    public final static int PRIORITY_DEFAULT = 0;
    public final static int PRIORITY_REACHABLE = 1<<1;
    public final static int PRIORITY_NOT_INITIATED_BY_INVOKE = 1<<0;
    public final static int PRIORITY_UNDETERMINED = 1<<2;
    public final static int PRIORITY_UNKNOWN = 1<<3;

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority |= priority;
//        if(this.priority < priority)
//            this.priority = priority;
    }

    private int priority = PRIORITY_DEFAULT;

    public boolean isUAF() {
        return isUAF;
    }

    public MethodRace setUAF(boolean UAF) {
        isUAF = UAF;
        return this;
    }

    public MethodRace setWW(boolean WW) {
        writeWrite = WW;
        return this;
    }

    public boolean isIfGuard() {
        return ifGuard;
    }

    public MethodRace setIfGuard(boolean ifGuard) {
        this.ifGuard = ifGuard;
        return this;
    }

    public boolean isNullAtEnd() {
        return nullAtEnd;
    }

    public MethodRace setNullAtEnd(boolean nullAtEnd) {
        this.nullAtEnd = nullAtEnd;
        return this;
    }

    private boolean isUAF;
    private boolean ifGuard;
    private boolean nullAtEnd;

    public boolean isInitiatedByInvoke() {
        return initiatedByInvoke;
    }

    public MethodRace setInitiatedByInvoke(boolean initiatedByInvoke) {
        this.initiatedByInvoke = initiatedByInvoke;
        return this;
    }

    private boolean initiatedByInvoke;

    @Override
    public String toString() {
        return String.format("MR: W-> %s, R-> %s, M-> %s", getWriteEvent(), getReadEvent(), getMemoryId());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        if(isWW()){
            result = prime * result + getWriteEvent().hashCode() + getReadEvent().hashCode();
            result = prime * result + writeStmt.hashCode() + readStmt.hashCode();
        }
        else{
            result = prime * result + getWriteEvent().hashCode();
            result = prime * result + getReadEvent().hashCode();
            result = prime * result + writeStmt.hashCode();
            result = prime * result + readStmt.hashCode();
        }

        result = prime * result + getMemoryId().getLatestField().hashCode();

        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        MethodRace other = (MethodRace) obj;
        if(isWW()){
            if (!(this.getMemoryId().equals(other.getMemoryId())
                    && ((this.getWriteEvent().equals(other.getWriteEvent()) && this.getReadEvent().equals(other.getReadEvent()))
                    || (this.getWriteEvent().equals(other.getReadEvent()) && this.getReadEvent().equals(other.getWriteEvent())))))
                return false;
            return (this.getWriteStmt().equals(other.getWriteStmt()) && this.getReadStmt().equals(other.getReadStmt()))
                    || (this.getWriteStmt().equals(other.getReadStmt()) && this.getReadStmt().equals(other.getWriteStmt()));
        }
        if(!(this.getWriteEvent().equals(other.getWriteEvent()) && this.getReadEvent().equals(other.getReadEvent()) && this.getMemoryId().getLatestField().equals(other.getMemoryId().getLatestField())))
            return false;
        return this.writeStmt.equals(other.writeStmt) && this.readStmt.equals(other.readStmt);
    }
}
