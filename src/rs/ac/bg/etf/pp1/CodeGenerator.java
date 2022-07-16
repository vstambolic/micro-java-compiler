package rs.ac.bg.etf.pp1;

import com.sun.deploy.net.MessageHeader;
import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import static rs.ac.bg.etf.pp1.SemanticAnalyzer.RECORD_STRUCT;
import static rs.ac.bg.etf.pp1.SemanticAnalyzer.VIRTUAL_METHOD_TABLE;

public class CodeGenerator extends VisitorAdaptor {

    private static final String MAIN = "main";

    private int varCount;

    private int paramCnt;

    private int mainPc;
    private Map<Struct, Integer> vmtMap = new HashMap<>();
    private boolean baseExpNewInstanceVisited = false;

    public int getMainPc() {
        return mainPc;
    }

    // Singleton ------------------------------------
    private static CodeGenerator codeGeneratorInstance;

    private CodeGenerator() {
    }

    public static CodeGenerator getInstance() {
        if (codeGeneratorInstance == null)
            codeGeneratorInstance = new CodeGenerator();
        return codeGeneratorInstance;
    }


    // Scope -----------------------------------------------------------------------------------------------------------
    private boolean insideClass = false;

    public void visit(ClassDeclStart classDeclStart) {
        insideClass = true;
    }

    public void visit(ClassDeclValid classDeclValid) {
        insideClass = false;
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

    public void visit(Term term) {
        if (term.getParent() instanceof NegativeExpr) {
            Code.put(Code.neg);
        }
    }

    public void visit(AddopTermListIndeed addopTermListIndeed) {
        Addop addop = addopTermListIndeed.getAddop();
        if (addop instanceof Add)
            Code.put(Code.add);
        else
            Code.put(Code.sub);
    }

    // Print / read ----------------------------------------------------------------------------------------------------
    public void visit(PrintStatement printStatement) {
        // Determine width
        if (printStatement.getPrintWidth() instanceof PrintWidthIndeed)
            Code.load(new Obj(Obj.Con, "", Tab.intType, ((PrintWidthIndeed) printStatement.getPrintWidth()).getN1(), 0));
        else
            Code.put(Code.const_1);
        // Determine type
        if (printStatement.getExpr().struct.equals(Tab.charType))
            Code.put(Code.bprint);
        else
            Code.put(Code.print);
    }

    public void visit(ReadStatement readStatement) {
        Code.put(readStatement.getDesignator().obj.getType().equals(Tab.charType) ? Code.bread : Code.read);
        Code.store(readStatement.getDesignator().obj);
    }

    // New instance expression -----------------------------------------------------------------------------------------
    public void visit(BaseExpNewInstance baseExpNewInstance) {
        BracketsWithExprOrNothing bracketsWithExprOrNothing = baseExpNewInstance.getBracketsWithExprOrNothing();
        Struct type = baseExpNewInstance.getType().struct;
        if (bracketsWithExprOrNothing instanceof BracketsWithExprIndeed) {
            Code.put(Code.newarray);  // new array
            Code.put(type.equals(Tab.charType) ? 0 : 1);
        }
        else {
            Code.put(Code.new_);
            int size = type.getMembers()
                    .stream()
                    .filter(obj -> obj.getKind() == Obj.Fld)
                    .mapToInt(obj -> obj.getType().equals(Tab.charType) ? 1 : 4)
                    .sum();
            Code.put2(size);

            // if not record
            if (type.getElemType() == null || !type.getElemType().equals(RECORD_STRUCT))
                this.baseExpNewInstanceVisited = true;
        }
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
    // Designators -----------------------------------------------------------------------------------------------------

    public void visit(DesignatorIdent designator) {
        if (this.insideClass && (designator.obj.getKind() == Obj.Fld || designator.obj.getKind() == Obj.Meth))
            Code.put(Code.load_n);
        if (!(designator.getParent() instanceof DesignatorStatement) // load value if designator is not from the left side of assign statement
                && !(designator.getParent() instanceof ReadStatement)   // load value if designator is not a part of the read statement
                && designator.obj.getKind() != Obj.Meth) {              // load value if designator is not a method
            Code.load(designator.obj);
        }
    }

    public void visit(DesignatorMemberReference designator) {
        if (!(designator.getParent() instanceof DesignatorStatement) // load value if designator is not from the left side of assign statement
                && !(designator.getParent() instanceof ReadStatement)   // load value if designator is not a part of the read statement
                && designator.obj.getKind() != Obj.Meth) {              // load value if designator is not a method
            Code.load(designator.obj);
        }
    }

    public void visit(DesignatorArrayReference designator) {
        if (!(designator.getParent() instanceof DesignatorStatement) && !(designator.getParent() instanceof ReadStatement)) {
            Code.load(designator.obj);
        }
    }

    public void visit(DesignatorAssignOperation designatorAssignOperation) {
        Obj designatorObj = ((DesignatorStatement) designatorAssignOperation.getParent()).getDesignator().obj;
        Code.store(designatorObj);

        // if assigning a new class instance, update VMT pointer
        if (this.baseExpNewInstanceVisited) {
            this.baseExpNewInstanceVisited = false;

            Code.load(designatorObj);
            Code.loadConst(this.vmtMap.get(designatorObj.getType()));
            Code.put(Code.putfield);
            Code.put2(0);
        }
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

    // Functions and methods -------------------------------------------------------------------------------------------
    public void visit(DeclListIndeed declListIndeed) {
        // After all classes have been declared, generate Virtual Method Table (VMT)
        if (declListIndeed.getParent() instanceof Program) {
            ((Program) declListIndeed.getParent()).getProgramDecl().obj
                    .getLocalSymbols()
                    .stream()
                    .filter(obj -> obj.getType().getKind() == Struct.Class && (obj.getType().getElemType() == null || !obj.getType().getElemType().equals(RECORD_STRUCT)))
                    .map(Obj::getType)
                    .forEachOrdered(classStruct -> {
                        // map VMT address
                        CodeGenerator.this.vmtMap.put(classStruct, Code.dataSize);
                        classStruct.getMembers()
                                .stream()
                                .filter(localObj -> localObj.getKind() == Obj.Meth)
                                .forEachOrdered(methodObj -> {
                                    methodObj.getName()
                                            .chars()
                                            .forEach(c -> {
                                                Code.loadConst(c);
                                                Code.put(Code.putstatic);
                                                Code.put2(Code.dataSize++);
                                            });
                                    Code.put(Code.const_m1);
                                    Code.put(Code.putstatic);
                                    Code.put2(Code.dataSize++);

                                    Code.loadConst(methodObj.getAdr());
                                    Code.put(Code.putstatic);
                                    Code.put2(Code.dataSize++);

                                });
                        Code.loadConst(-2);
                        Code.put(Code.putstatic);
                        Code.put2(Code.dataSize++);
                    });
        }
    }

    public void visit(MethodDeclStart methodDeclStart) {
        if (methodDeclStart.getIdent().equals(MAIN))
            Code.mainPc = this.mainPc = Code.pc;

        methodDeclStart.obj.setAdr(Code.pc);
        Code.put(Code.enter);
        Code.put(methodDeclStart.obj.getLevel());
        Code.put(methodDeclStart.obj.getLocalSymbols().size());
    }

    public void visit(MethodDecl methodDecl) {
        Code.put(Code.exit);
        Code.put(Code.return_);
    }

    // function call as an expression
    public void visit(BaseExpDesignator baseExpDesignator) {
        if (baseExpDesignator.getFuncCallOrNothing() instanceof FuncCallIndeed) {
            Obj methodObj = baseExpDesignator.getDesignator().obj;
            this.visitMethodCall(methodObj);
        }
    }

    private void visitMethodCall(Obj methodObj) {
        Obj firstArgument = methodObj.getLocalSymbols().stream().findFirst().orElse(null);
        boolean isMethod = (firstArgument != null && firstArgument.getName().equals(SemanticAnalyzer.THIS));
        if (isMethod) {
            Obj thisPointer = methodObj.getLocalSymbols().stream().findFirst().get();
            Code.load(thisPointer);  // Load VMT pointer: load this, getfield 0
            Code.put(Code.getfield);
            Code.put2(0);

            Code.put(Code.invokevirtual);
            methodObj.getName().chars().forEach(Code::put4);
            Code.put4(-1);
        }
        else {
            int offset = methodObj.getAdr() - Code.pc;
            Code.put(Code.call);
            Code.put2(offset);
        }
    }

    // function call per se
    public void visit(DesignatorFuncCallOperation designatorFuncCallOperation) {
        Obj methodObj = ((DesignatorStatement) designatorFuncCallOperation.getParent()).getDesignator().obj;
        this.visitMethodCall(methodObj);
        Code.put(Code.pop);
    }
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
