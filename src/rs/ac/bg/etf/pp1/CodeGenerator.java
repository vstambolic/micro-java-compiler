package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;

public class CodeGenerator extends VisitorAdaptor {

    private int varCount;

    private int paramCnt;

    private int mainPc;

    public int getMainPc() {
        return mainPc;
    }

    // Basic arithmetic expressions ------------------------------------------------------------------------------------
    public void visit(BaseExpNumber baseExpNumber) {
        Code.load(new Obj(Obj.Con, "", Tab.intType, baseExpNumber.getN1(), 0));
    }

    public void visit(BaseExpBool baseExpBool) {
        Code.load(new Obj(Obj.Con, "", SemanticAnalyzer.BOOL_STRUCT, baseExpBool.getB1() ? 1 : 0, 0));
    }

    public void visit(BaseExpChar baseExpChar) {
        Code.load(new Obj(Obj.Con, "", Tab.charType, baseExpChar.getC1(), 0));
    }

    public void visit(MulopFactorListIndeed mulopFactorListIndeed) {
        Mulop mulop = mulopFactorListIndeed.getMulop();
        if (mulop instanceof Mul)
            Code.put(Code.mul);
        else
            if (mulop instanceof Div)
                Code.put(Code.div);
            else
                Code.put(Code.rem);
    }

    public void visit(AddopTermListIndeed addopTermListIndeed) {
        Addop addop = addopTermListIndeed.getAddop();
        if (addop instanceof Add)
            Code.put(Code.add);
        else
            Code.put(Code.sub);
    }

    // Designators -----------------------------------------------------------------------------------------------------

    private boolean assignOpVisited = false;
    public void visit(DesignatorIdent designator) {
        if (/*this.assignOpVisited ||*/ !(designator.getParent() instanceof DesignatorStatement)) {
            Code.load(designator.obj);
        }
    }
    public void visit(DesignatorMemberReference designator) {
        if (/*this.assignOpVisited ||*/ !(designator.getParent() instanceof DesignatorStatement)) {
            Code.load(designator.obj);
        }
    }
    public void visit(DesignatorArrayReference designator) {
        if (/*this.assignOpVisited ||*/ !(designator.getParent() instanceof DesignatorStatement)) {
            Code.load(designator.obj);
        }
    }

    public void visit(DesignatorAssignOperation designatorAssignOperation) {
        Code.store(((DesignatorStatement) designatorAssignOperation.getParent()).getDesignator().obj);
    }
    public void visit(DesignatorIncOperation designatorIncOperation) {
        Code.put(Code.const_1);
        Code.put(Code.add);
        Code.store(((DesignatorStatement) designatorIncOperation.getParent()).getDesignator().obj);
    }

    public void visit(DesignatorDecOperation designatorDecOperation) {
        Code.put(Code.const_1);
        Code.put(Code.sub);
        Code.store(((DesignatorStatement) designatorDecOperation.getParent()).getDesignator().obj);
    }
    // todo designator u klasama, negative expression, designator kao funkcijski poziv
//
//	@Override
//	public void visit(MethodTypeName MethodTypeName) {
//		if ("main".equalsIgnoreCase(MethodTypeName.getMethName())) {
//			mainPc = Code.pc;
//		}
//		MethodTypeName.obj.setAdr(Code.pc);
//
//		// Collect arguments and local variables.
//		SyntaxNode methodNode = MethodTypeName.getParent();
//		VarCounter varCnt = new VarCounter();
//		methodNode.traverseTopDown(varCnt);
//		FormParamCounter fpCnt = new FormParamCounter();
//		methodNode.traverseTopDown(fpCnt);
//
//		// Generate the entry.
//		Code.put(Code.enter);
//		Code.put(fpCnt.getCount());
//		Code.put(varCnt.getCount() + fpCnt.getCount());
//	}
//
//	@Override
//	public void visit(VarDecl VarDecl) {
//		varCount++;
//	}
//
//	@Override
//	public void visit(FormalParamDecl FormalParam) {
//		paramCnt++;
//	}
//
//	@Override
//	public void visit(MethodDecl MethodDecl) {
//		Code.put(Code.exit);
//		Code.put(Code.return_);
//	}
//
//	@Override
//	public void visit(ReturnExpr ReturnExpr) {
//		Code.put(Code.exit);
//		Code.put(Code.return_);
//	}
//
//	@Override
//	public void visit(ReturnNoExpr ReturnNoExpr) {
//		Code.put(Code.exit);
//		Code.put(Code.return_);
//	}
//
//	@Override
//	public void visit(Assignment Assignment) {
//		Code.store(Assignment.getDesignator().obj);
//	}
//
//	@Override
//	public void visit(Const Const) {
//		Code.load(new Obj(Obj.Con, "$", Const.struct, Const.getN1(), 0));
//	}
//
//	@Override
//	public void visit(Designator Designator) {
//		SyntaxNode parent = Designator.getParent();
//		if (Assignment.class != parent.getClass() && FuncCall.class != parent.getClass()) {
//			Code.load(Designator.obj);
//		}
//	}
//
//	@Override
//	public void visit(FuncCall FuncCall) {
//		Obj functionObj = FuncCall.getDesignator().obj;
//		int offset = functionObj.getAdr() - Code.pc;
//		Code.put(Code.call);
//		Code.put2(offset);
//	}
//
//	@Override
//	public void visit(PrintStmt PrintStmt) {
//		Code.put(Code.const_5);
//		Code.put(Code.print);
//	}
//
//	@Override
//	public void visit(AddExpr AddExpr) {
//		Code.put(Code.add);
//	}
}
