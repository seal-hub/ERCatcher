package com.ercatcher.RaceDetector;
import soot.jimple.Stmt;

public class EventRace extends Race<ContextMethod, FieldId>{
    EventRace(ContextMethod writeEvent, ContextMethod readEvent, FieldId fieldId) {
        this(writeEvent, null, readEvent, null, fieldId);
    }

    EventRace(ContextMethod writeEvent, Stmt writeStmt, ContextMethod readEvent, Stmt readStmt, FieldId fieldId) {
        super(writeEvent, readEvent, fieldId);
        this.writeStmt = writeStmt;
        this.readStmt = readStmt;
    }

    public Stmt getWriteStmt() {
        return writeStmt;
    }

    public Stmt getReadStmt() {
        return readStmt;
    }

    private Stmt writeStmt;
    private Stmt readStmt;

    @Override
    public String toString() {
        return "CMR: Write: " + getWriteEvent().toString() + "-" + getWriteStmt().toString() + " Read: " +getReadEvent() +"-"+getReadStmt().toString() + " MemId: " +  getMemoryId();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + writeStmt.hashCode();
        result = prime * result + readStmt.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if(!super.equals(obj))
            return false;
        EventRace other = (EventRace) obj;
        return this.writeStmt.equals(other.writeStmt) && this.readStmt.equals(other.readStmt);
    }
}
