package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static rs.ac.bg.etf.pp1.SemanticAnalyzer.*;

public class CodeGenerator extends VisitorAdaptor {

    private static final String MAIN = "main";

    private int varCount;

    private int paramCnt;

    private int mainPc;
    private Map<Struct, Integer> vmtMap = new HashMap<>();
    private Struct newInstanceType = null;

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

    // Standard functions ----------------------------------------------------------------------------------------------

    public void visit(ProgramDecl programDecl) {
        this.generateStandardFunctionsCode();
    }

    private void generateStandardFunctionsCode() {
        this.generateChrOrdCode();
        this.generateLenCode();
    }


    // Same code for chr and ord
    private void generateChrOrdCode() {
        Tab.find("chr").setAdr(0); // set address to 0
        Tab.find("ord").setAdr(0); // set address to 0

        Code.put(Code.enter);
        Code.put(1);
        Code.put(1);
        Code.put(Code.load_n);
        Code.put(Code.exit);
        Code.put(Code.return_);
    }

    private void generateLenCode() {
        Tab.find("len").setAdr(Code.pc);
        Code.put(Code.enter);
        Code.put(1);
        Code.put(1);
        Code.put(Code.load_n);
        Code.put(Code.arraylength);
        Code.put(Code.exit);
        Code.put(Code.return_);
    }


    // Scope -----------------------------------------------------------------------------------------------------------
    private boolean insideClass = false;

    public void visit(ClassDeclStart classDeclStart) {
        insideClass = true;
    }

    public void visit(ClassDeclValid classDeclValid) {
        insideClass = false;
        Struct classStruct = classDeclValid.getClassDeclStart().obj.getType();
        CodeGenerator.this.vmtMap.put(classStruct, Code.dataSize);
        Code.dataSize += this.calculateVmtSize(classStruct); // Allocate space for vmt
    }

    private int calculateVmtSize(Struct classStruct) {
        // VMT size = sum(len(methName) + 2) + 1
        return classStruct.getMembers()
                .stream()
                .filter(localObj -> localObj.getKind() == Obj.Meth)
                .mapToInt(methodObj -> methodObj.getName().length() + 2)
                .sum() + 1;
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

    private Set<Integer> ifNullAddressesThatNeedFixing = new HashSet<>();
    public void visit(Expr expr) {
        if (expr.getIfNullExprOrNothing() instanceof NoIfNullExpr && expr.getParent() instanceof IfNullExprIndeed) {
            ifNullAddressesThatNeedFixing.forEach(Code::fixup);
            ifNullAddressesThatNeedFixing = new HashSet<>();
        }
    }
    public void visit(IfNullOp ifNullOp) {
        Code.put(Code.dup);
        Code.loadConst(0);
        Code.put(Code.jcc + Code.ne);
        ifNullAddressesThatNeedFixing.add(Code.pc);
        Code.put2(0);
        Code.put(Code.pop);
    }

    public void visit(AddopTermListIndeed addopTermListIndeed) {
        Addop addop = addopTermListIndeed.getAddop();
        if (addop instanceof Add)
            Code.put(Code.add);
        else
            Code.put(Code.sub);
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


    // Print / read ----------------------------------------------------------------------------------------------------
    public void visit(PrintStatement printStatement) {
        // Determine width
        if (printStatement.getPrintWidth() instanceof PrintWidthIndeed)
            Code.load(new Obj(Obj.Con, "", Tab.intType, ((PrintWidthIndeed) printStatement.getPrintWidth()).getN1(), 0));
        else
            Code.put(Code.const_4);
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

    public void visit(ReturnStatement returnStatement) {
        Code.put(Code.exit);
        Code.put(Code.return_);
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
                this.newInstanceType = type;

        }
    }

    // Designators -----------------------------------------------------------------------------------------------------

    public void visit(DesignatorIdent designator) {
        if (this.insideClass && (designator.obj.getKind() == Obj.Fld || (designator.obj.getKind() == Obj.Meth &&
                designator.obj
                        .getLocalSymbols()
                        .stream()
                        .findFirst()
                        .map(obj -> obj.getName().equals(THIS))
                        .orElse(false))))
            Code.put(Code.load_n);
        if (!(designator.getParent() instanceof DesignatorStatement) // load value if designator is not from the left side of assign statement
                && !(designator.getParent() instanceof ReadStatement)   // load value if designator is not a part of the read statement
                && designator.obj.getKind() != Obj.Meth) {              // load value if designator is not a method
            Code.load(designator.obj);
        }
    }

    public void visit(DesignatorMemberReference designator) {
        if (!(designator.getParent() instanceof DesignatorStatement)    // load value if designator is not from the left side of assign statement
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
        Designator designator = ((DesignatorStatement) designatorAssignOperation.getParent()).getDesignator();
        Code.store(designator.obj);

        // if assigning a new class instance, update VMT pointer
        if (this.newInstanceType != null) {
            // visit designator again to get a value
            designator.traverseBottomUp(this);

            Code.load(designator.obj);
            Code.loadConst(this.vmtMap.get(newInstanceType));
            Code.put(Code.putfield);
            Code.put2(0);


            Obj constructorObj = this.getConstructor(this.newInstanceType);
            if (constructorObj != null) {
                // visit designator again to get a value
                designator.traverseBottomUp(this);
                Code.load(designator.obj);

                designator.traverseBottomUp(this);
                Code.load(designator.obj);
                Code.put(Code.getfield); // Load VMT pointer: load this, getfield 0
                Code.put2(0);

                Code.put(Code.invokevirtual);
                constructorObj.getName().chars().forEach(Code::put4);
                Code.put4(-1);
            }


            this.newInstanceType = null;

        }
    }

    private Obj getConstructor(Struct classStruct) {
        Obj typeObj = Tab.currentScope()
                .getLocals()
                .symbols()
                .stream()
                .filter(obj -> obj.getKind() == Obj.Prog)
                .findFirst()
                .get()
                .getLocalSymbols()
                .stream()
                .filter(obj -> obj.getKind() == Obj.Type)
                .filter(obj -> obj.getType().equals(classStruct))
                .findFirst()
                .get();
        return typeObj.getType()
                .getMembers()
                .stream()
                .filter(obj -> obj.getKind() == Obj.Meth)
                .filter(obj -> obj.getName().equals(typeObj.getName()))
                .findFirst()
                .orElse(null);
    }

    public void visit(DesignatorIncOperation designatorIncOperation) {
        Designator designator = ((DesignatorStatement) designatorIncOperation.getParent()).getDesignator();
        // visit designator again to get a value
        designator.traverseBottomUp(this);

        Code.load(designator.obj);
        Code.put(Code.const_1);
        Code.put(Code.add);
        Code.store(designator.obj);
    }

    public void visit(DesignatorDecOperation designatorDecOperation) {
        Designator designator = ((DesignatorStatement) designatorDecOperation.getParent()).getDesignator();
        // visit designator again to get a value
        designator.traverseBottomUp(this);

        Code.load(designator.obj);
        Code.put(Code.const_1);
        Code.put(Code.sub);
        Code.store(((DesignatorStatement) designatorDecOperation.getParent()).getDesignator().obj);
    }

    // Functions and methods -------------------------------------------------------------------------------------------
    private void generateVirtualMethodTable() {
        this.vmtMap.forEach((classStruct, vmtAddress) -> {
            AtomicInteger vmtPointer = new AtomicInteger(vmtAddress);
            classStruct.getMembers()
                    .stream()
                    .filter(localObj -> localObj.getKind() == Obj.Meth)
                    .forEachOrdered(methodObj -> {
                        methodObj.getName()
                                .chars()
                                .forEach(c -> {
                                    Code.loadConst(c);
                                    Code.put(Code.putstatic);
                                    Code.put2(vmtPointer.getAndIncrement());
                                });
                        Code.put(Code.const_m1);
                        Code.put(Code.putstatic);
                        Code.put2(vmtPointer.getAndIncrement());

                        Code.loadConst(methodObj.getAdr());
                        Code.put(Code.putstatic);
                        Code.put2(vmtPointer.getAndIncrement());

                    });
            Code.loadConst(-2);
            Code.put(Code.putstatic);
            Code.put2(vmtPointer.getAndIncrement());
        });
    }

    public void visit(MethodDeclStart methodDeclStart) {
        if (methodDeclStart.getIdent().equals(MAIN))
            Code.mainPc = this.mainPc = Code.pc;

        methodDeclStart.obj.setAdr(Code.pc);

        Code.put(Code.enter);
        Code.put(methodDeclStart.obj.getLevel());
        Code.put(methodDeclStart.obj.getLocalSymbols().size());

        if (methodDeclStart.getIdent().equals(MAIN))
            this.generateVirtualMethodTable();
    }

    public void visit(MethodDecl methodDecl) {
        Code.put(Code.exit);
        Code.put(Code.return_);
    }
    // Constructors -----------------------------------------------------------------------------------------------------

    public void visit(ConstructorDeclStart constructorDeclStart) {
        constructorDeclStart.obj.setAdr(Code.pc);

        Code.put(Code.enter);
        Code.put(constructorDeclStart.obj.getLevel());
        Code.put(constructorDeclStart.obj.getLocalSymbols().size());
    }

    public void visit(ConstructorDecl constructorDecl) {
        Code.put(Code.exit);
        Code.put(Code.return_);
    }

    // Function calls --------------------------------------------------------------------------------------------------

    // function call as an expression
    public void visit(BaseExpDesignator baseExpDesignator) {
        if (baseExpDesignator.getFuncCallOrNothing() instanceof FuncCallIndeed) {
            this.visitMethodCall(baseExpDesignator.getDesignator());
        }
    }

    // function call per se
    public void visit(DesignatorFuncCallOperation designatorFuncCallOperation) {
        Designator designator = ((DesignatorStatement) designatorFuncCallOperation.getParent()).getDesignator();
        this.visitMethodCall(designator);
        if (!designator.obj.getType().equals(Tab.noType))
            Code.put(Code.pop);
    }

    private void visitMethodCall(Designator methodDesignator) {
        Obj firstArgument = methodDesignator.obj.getLocalSymbols().stream().findFirst().orElse(null);
        boolean isMethod = (firstArgument != null && firstArgument.getName().equals(SemanticAnalyzer.THIS));
        if (isMethod) {
            methodDesignator.traverseBottomUp(this); // Load thisPointer
            //   Obj thisPointer = methodDesignator.obj.getLocalSymbols().stream().findFirst().get();
            //  Code.load(thisPointer);
            Code.put(Code.getfield); // Load VMT pointer: load this, getfield 0
            Code.put2(0);

            Code.put(Code.invokevirtual);
            methodDesignator.obj.getName().chars().forEach(Code::put4);
            Code.put4(-1);
        }
        else {
            int offset = methodDesignator.obj.getAdr() - Code.pc;
            Code.put(Code.call);
            Code.put2(offset);
        }
    }


    /*
     * TODO
     * super calls
     * expr ?? expr
     * optargs
     * if then else
     * do while
     *
     */
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
