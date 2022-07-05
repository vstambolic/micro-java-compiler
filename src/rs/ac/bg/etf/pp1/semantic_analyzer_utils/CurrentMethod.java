package rs.ac.bg.etf.pp1.semantic_analyzer_utils;

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
}
