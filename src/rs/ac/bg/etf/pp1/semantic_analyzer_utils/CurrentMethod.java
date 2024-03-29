package rs.ac.bg.etf.pp1.semantic_analyzer_utils;

import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;

public class CurrentMethod {
    private Obj currMethod;
    private int formalParameterCnt = 0;

    public CurrentMethod(Obj currMethod) {
        this.currMethod = currMethod;
    }

    public Obj getCurrMethod() {
        return currMethod;
    }

    public void incFormalParameterCnt() {
        this.formalParameterCnt++;
    }

    public void setFormalParameterCnt() {
        this.getCurrMethod().setLevel(this.formalParameterCnt);
    }

    public int getFormalParameterCnt() {
        return this.formalParameterCnt;
    }

    public boolean isVoid() {
        return this.getCurrMethod().getType().equals(Tab.noType);
    }

    public void setCurrMethod(Obj currMethod) {
        this.currMethod = currMethod;
    }

    public void incOptArgsCnt() {
        this.currMethod.setFpPos(this.currMethod.getFpPos()+1);
    }
}
