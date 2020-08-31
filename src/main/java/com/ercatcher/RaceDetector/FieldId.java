package com.ercatcher.RaceDetector;

import com.ercatcher.memory.MemoryId;
import soot.SootField;
import soot.jimple.FieldRef;
import soot.jimple.spark.pag.AllocNode;

import java.util.ArrayList;
import java.util.List;

public class FieldId implements MemoryId {
    AllocNode allocNode;
    ArrayList<FieldRef> fieldRefs = new ArrayList<>();
    SootField sootField;
    FieldId(AllocNode allocNode, List<FieldRef> fieldRefs){
        this.allocNode = allocNode;
        this.fieldRefs = new ArrayList<>(fieldRefs);
    }
    FieldId(SootField sootField, List<FieldRef> fieldRefs){
        this.sootField = sootField;
        this.fieldRefs = new ArrayList<>(fieldRefs);
    }
    FieldId(SootField sootField){
        this.sootField = sootField;
        this.fieldRefs = new ArrayList<>(fieldRefs);
    }
    FieldId getOverApproximate(){
        return new FieldId(getLatestField(), new ArrayList<>());
    }

    public SootField getLatestField(){
        if(allocNode != null)
            return fieldRefs.get(0).getField();
        if(fieldRefs.size() > 0)
            return fieldRefs.get(0).getField();
        return sootField;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        if(allocNode != null){
            result = prime * result + allocNode.hashCode();
            for (FieldRef fieldRef : fieldRefs)
                result = prime * result + fieldRef.getField().hashCode();
        }
        else{
            result = prime * result + sootField.hashCode();
            for (FieldRef fieldRef : fieldRefs)
                result = prime * result + fieldRef.getField().hashCode();
        }
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
        FieldId other = (FieldId) obj;
        if( this.allocNode != null){
            if(other.allocNode == null || !this.allocNode.equals(other.allocNode))
                return false;
        }
        else if (other.allocNode != null)
            return false;
        else if(!this.sootField.equals(other.sootField))
            return false;
        if (fieldRefs.size() != other.fieldRefs.size())
            return false;
        for (int i = 0; i < this.fieldRefs.size(); i++)
            if (!this.fieldRefs.get(i).getField().equals(other.fieldRefs.get(i).getField()))
                return false;
        return true;
    }

    @Override
    public String toString() {
        return getLatestField().toString();
    }
}