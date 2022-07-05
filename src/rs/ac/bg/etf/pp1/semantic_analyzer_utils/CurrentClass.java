package rs.ac.bg.etf.pp1.semantic_analyzer_utils;

import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class CurrentClass {
    private Obj currClass;

    public CurrentClass(Obj currClass) {
        this.currClass = currClass;
    }

    public Obj getCurrClass() {
        return currClass;
    }

    public boolean shouldImportSuperMethods() {
        return this.hasSuperClass();
    }

    public boolean hasSuperClass() {
        return this.currClass.getType().getElemType() != null;
    }

    public Struct getSuperClass() {
        return this.currClass.getType().getElemType();
    }
}
