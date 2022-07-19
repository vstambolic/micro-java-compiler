package rs.ac.bg.etf.pp1;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;
import rs.ac.bg.etf.pp1.semantic_analyzer_utils.CurrentClass;
import rs.ac.bg.etf.pp1.semantic_analyzer_utils.CurrentMethod;
import rs.ac.bg.etf.pp1.semantic_analyzer_utils.Scope;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.structure.SymbolDataStructure;

import java.util.*;
import java.util.stream.Collectors;

public class SemanticAnalyzer extends VisitorAdaptor {

    // Constants ---------------------------------
    public static final String THIS = "this";
    public static final int RECORD = 8;
    public static final Struct RECORD_STRUCT = new Struct(RECORD);
    public static final Struct BOOL_STRUCT = new Struct(Struct.Bool);
    public static final String VIRTUAL_METHOD_TABLE = "VMT";

    // Helpers -----------------------------------
    private Stack<Scope> scopeStack = new Stack<>();
    private CurrentClass currentClass = null;
    private CurrentMethod currentMethod = null;
    private Struct currType = Tab.noType;
    private boolean errorDetected = false;
    private int globalVarCnt;

    private final Logger log = Logger.getLogger(getClass());

    public SemanticAnalyzer() {
        Tab.currentScope().addToLocals(new Obj(Obj.Type, "bool", BOOL_STRUCT));
    }

    public boolean semanticCheckPassed() {
        return !this.errorDetected;
    }

    public void report_error(String message, SyntaxNode info) {
        errorDetected = true;
        StringBuilder msg = new StringBuilder(message);
        int line = (info == null) ? 0 : info.getLine();
        if (line != 0)
            msg.append(" line ").append(line);
        log.error(msg.toString());
    }

    public void report_info(String message, SyntaxNode info) {
        StringBuilder msg = new StringBuilder(message);
        int line = (info == null) ? 0 : info.getLine();
        if (line != 0)
            msg.append(" line ").append(line);
        log.info(msg.toString());
    }

    private void report_info(String s, SyntaxNode syntaxNode, Obj obj) {
        report_info(s, syntaxNode);
        report_object(obj);
    }

    private void report_object(Obj obj) {
        // todo
    }


    // PROGRAM ---------------------------------------------------------------

    /**
     * Opening program scope
     *
     * @param programDecl
     */
    public void visit(ProgramDecl programDecl) {
        programDecl.obj = Tab.insert(Obj.Prog, programDecl.getIdent(), Tab.noType);
        Tab.openScope();
        this.scopeStack.push(Scope.PROGRAM);
    }

    /**
     * Poziva se nakon cele analize
     *
     * @param program
     */
    public void visit(Program program) {
        Code.dataSize = globalVarCnt = Tab.currentScope.getnVars();
        Tab.chainLocalSymbols(program.getProgramDecl().obj); // Iz currentScopa prebacuje sve u dati cvor kao locals
        Tab.closeScope();

        Obj mainMethod = program.getProgramDecl().obj.getLocalSymbols().stream().filter(obj -> obj.getName().equals("main") && obj.getKind() == Obj.Meth).findFirst().orElse(null);
        if (mainMethod == null) {
            report_error("Main method not found ", null);
            return;
        }
        if (!mainMethod.getType().equals(Tab.noType)) {
            report_error("Main method must be declared as void ", null);
            return;
        }
        if (mainMethod.getLevel() != 0) {
            report_error("Main method must not have formal parameters ", null);
            return;
        }
    }

    /**
     * Poziva se prilikom analize tipa, cuva se trenutni currType (Struct)
     * kako bi nako
     *
     * @param type
     */

    // TYPE ---------------------------------------------------------------
    public void visit(Type type) {
        Obj typeNodeFromSymbolTable = Tab.find(type.getIdent());
        if (typeNodeFromSymbolTable == Tab.noObj) { // Ukoliko nije pronadjeno nista u taebeli simbola sa datim identifikatorom
            report_error("Nije pronadjen tip " + type.getIdent() + " u tabeli simbola", null);
            type.struct = currType = Tab.noType;
        }
        else {
            if (Obj.Type != typeNodeFromSymbolTable.getKind()) { // Ukoliko jeste pronadjeno nesto u tabeli simbola sa datim identifikatorom
                // ali to nesto nije TIP (nego npr. identifikator neke varijable ili funkcije)
                report_error("Greska: Ime " + type.getIdent() + " ne predstavlja tip ", type);
                type.struct = currType = Tab.noType;
            }
            else {
                type.struct = currType = typeNodeFromSymbolTable.getType(); // charType ili intType ili neki drugi tip
            }
        }
    }

    // VAR ---------------------------------------------------------------

    /**
     * Poziva se prilikom deklaracije varijable
     *
     * @param var
     */
    public void visit(Var var) {
        String identifier = var.getIdent();
        if (SemanticAnalyzer.isAlreadyDeclared(identifier)) {
            report_error("Symbol " + identifier + " already declared |", var);
            return;
        }

        int kind = this.getCurrScope().equals(Scope.CLASS) ? Obj.Fld : Obj.Var;
        Struct type = (var.getBrackets() instanceof BracketsIndeed ? new Struct(Struct.Array, currType) : currType);

        Obj obj = Tab.insert(kind, identifier, type);

        this.report_info((this.getCurrScope().equals(Scope.PROGRAM) ? "Global" : "Local") + " variable declared (" + identifier + ").", var);
    }

    // CONST ---------------------------------------------------------------
    public void visit(ConstAssignment constAssignment) {
        String identifier = constAssignment.getIdent();
        if (SemanticAnalyzer.isAlreadyDeclared(identifier)) {
            report_error("Symbol " + identifier + " already declared ", constAssignment);
            return;
        }

        if (currType.getKind() != Struct.Bool && currType.getKind() != Struct.Char && currType.getKind() != Struct.Int) {
            report_error("Invalid const declaration ", constAssignment);
            return;
        }

        int value;
        Literal literal = constAssignment.getLiteral();
        if (literal instanceof NumConst) {
            if (currType.getKind() != Struct.Int) {
                report_error("Invalid const declaration ", constAssignment);
                return;
            }
            value = ((NumConst) literal).getNumVal();
        }
        else
            if (literal instanceof CharConst) {
                if (currType.getKind() != Struct.Char) {
                    report_error("Invalid const declaration ", constAssignment);
                    return;
                }
                value = ((CharConst) literal).getCharVal();
            }
            else // (literal instanceof BoolConst)
            {
                if (currType.getKind() != Struct.Bool) {
                    report_error("Invalid const declaration ", constAssignment);
                    return;
                }
                value = ((BoolConst) literal).getBoolVal() ? 1 : 0;
            }
        Obj obj = Tab.insert(Obj.Con, identifier, currType);
        obj.setAdr(value);

        this.report_info("Const declared (" + identifier + ")", constAssignment);
    }

    // METHOD ---------------------------------------------------------------
    public void visit(ConstructorDeclStart constructorDeclStart) {
        String identifier = constructorDeclStart.getIdent();
        if (!identifier.equals(currentClass.getCurrClass().getName())) { // error je kada se metod zove isto kao klasa
            report_error("Constructor (" + identifier + ") must have the same identifier as its class (" + currentClass.getCurrClass().getName() + ")", constructorDeclStart);
            return;
        }

        Obj currMethodObj = Tab.insert(Obj.Meth, identifier, Tab.noType);
        this.currentMethod = new CurrentMethod(currMethodObj);
        constructorDeclStart.obj = currMethodObj;

        this.openScope(Scope.METHOD);
        Tab.insert(Obj.Var, THIS, currentClass.getCurrClass().getType());
        this.currentMethod.incFormalParameterCnt();
    }

    public void visit(ConstructorDecl constructorDecl) {
        if (this.currentMethod != null) {
            this.currentMethod.setFormalParameterCnt();
            Tab.chainLocalSymbols(this.currentMethod.getCurrMethod());
            this.closeScope();
            constructorDecl.obj = this.currentMethod.getCurrMethod();
            this.currentMethod = null;
        }

    }

    public void visit(TypeOrVoid_Void TypeOrVoid_Void) {
        this.currType = Tab.noType;
    }

    public void visit(MethodDeclStart methodDeclStart) {
        String identifier = methodDeclStart.getIdent();

        if (this.getCurrScope().equals(Scope.PROGRAM)) {
            if (SemanticAnalyzer.isAlreadyDeclared(identifier)) {
                report_error("Symbol " + identifier + " already declared ", methodDeclStart);
                return;
            }
        }
        else
            if (this.getCurrScope().equals(Scope.CLASS)) {
                if (identifier.equals(currentClass.getCurrClass().getName())) { // error je kada se metod zove isto kao klasa
                    report_error("Invalid method identifier (" + identifier + ")", methodDeclStart);
                    return;
                }
                Obj alreadyDeclaredObj = Tab.currentScope().findSymbol(identifier);
                if (alreadyDeclaredObj != null && (alreadyDeclaredObj.getKind() != Obj.Meth // vec postoji objekat istog imena koji nije tipa meth
                        || alreadyDeclaredObj.getType() != currType // vec postoji objekat istog imena koji jeste tipa meth i nije istog tipa
                        || !this.currentClass.hasSuperClass()  // postoji identifier koji jeste tipa meth i jeste istog tipa ali nije u pitanju nasledjeni zato sto nema natklase
                        || this.currentClass.getSuperClass().getMembers().stream().noneMatch(obj -> obj.getKind() == Obj.Meth && obj.getName().equals(identifier) && obj.getType().equals(currType))  // postoji identifier koji jeste tipa meth i jeste istog tipa ali nije u pitanju nasledjeni
                        || alreadyDeclaredObj.getLocalSymbols().stream().findFirst().get().getType().equals(currentClass.getCurrClass().getType()))) // postoji identifier koji jeste tipa meth i jeste istog tipa i on vec overrideuje metod natklase
                {
                    report_error("Symbol " + identifier + " already declared ", methodDeclStart);
                    return;
                }
            }

        Obj currMethodObj = new Obj(Obj.Meth, identifier, this.currType, 0, 0);
        this.currentMethod = new CurrentMethod(currMethodObj);
        methodDeclStart.obj = currMethodObj;
        Tab.openScope();
        if (this.getCurrScope().equals(Scope.CLASS)) {
            Tab.insert(Obj.Var, THIS, currentClass.getCurrClass().getType());
            this.currentMethod.incFormalParameterCnt();
        }
        this.scopeStack.push(Scope.METHOD);
    }

    public void visit(FormPar formPar) {
        this.currentMethod.incFormalParameterCnt();
        String identifier = formPar.getIdent();
        if (SemanticAnalyzer.isAlreadyDeclared(identifier)) {
            report_error("Symbol " + identifier + " already declared |", formPar);
            return;
        }

        int kind = Obj.Var;
        Struct type = (formPar.getBrackets() instanceof BracketsIndeed ? new Struct(Struct.Array, currType) : currType);

        Obj obj = Tab.insert(kind, identifier, type);

        this.report_info(" Formal parameter declared (" + identifier + ").", formPar);
    }

    public void visit(OptArg optArg) {
        int value;
        Literal literal = optArg.getLiteral();
        if (literal instanceof NumConst) {
            if (currType.getKind() != Struct.Int) {
                report_error("Invalid default argument type declaration ", optArg);
                return;
            }
            value = ((NumConst) literal).getNumVal();
        }
        else {
            if (literal instanceof CharConst) {
                if (currType.getKind() != Struct.Char) {
                    report_error("Invalid default argument type declaration ", optArg);
                    return;
                }
                value = ((CharConst) literal).getCharVal();
            }
            else // (literal instanceof BoolConst)
            {
                if (currType.getKind() != Struct.Bool) {
                    report_error("Invalid default argument type declaration ", optArg);
                    return;
                }
                value = ((BoolConst) literal).getBoolVal() ? 1 : 0;
            }
        }

        this.currentMethod.incFormalParameterCnt();
        String identifier = optArg.getIdent();
        if (SemanticAnalyzer.isAlreadyDeclared(identifier)) {
            report_error("Symbol " + identifier + " already declared |", optArg);
            return;
        }

        int kind = Obj.Var;
        Struct type = currType;
        Obj obj = Tab.insert(kind, identifier, type);
        obj.setFpPos(value);
        this.currentMethod.incOptArgsCnt();
        this.report_info(" Optional argument declared (" + identifier + ").", optArg);
    }

    public void visit(MethodDecl methodDecl) {
        this.scopeStack.pop();
        if (this.getCurrScope().equals(Scope.CLASS)) {
            Obj superMethod = Tab.currentScope().getOuter().findSymbol(methodDecl.getMethodDeclStart().getIdent());
            if (superMethod != null) {
                // check formal parameters
                if (superMethod.getLevel() != this.currentMethod.getFormalParameterCnt()) {
                    report_error("Method " + methodDecl.getMethodDeclStart().getIdent() + " does not have the same signature as its super method |", methodDecl);
                    return;
                }
                else {
                    List<Obj> superFormParams = superMethod.getLocalSymbols().stream().limit(superMethod.getLevel()).collect(Collectors.toList());
                    List<Obj> methodFormParams = Tab.currentScope().getLocals().symbols().stream().limit(superMethod.getLevel()).collect(Collectors.toList());
                    for (int i = 1; i < superMethod.getLevel(); i++) {
                        Obj superFormParam = superFormParams.get(i);
                        Obj methodFormParam = methodFormParams.get(i);
                        if (!superFormParam.getType().equals(methodFormParam.getType())) {
                            report_error("Method " + methodDecl.getMethodDeclStart().getIdent() + " does not have the same signature as its super method |", methodDecl);
                            return;
                        }
                    }
                }
            }
        }

        if (this.getCurrScope().equals(Scope.CLASS)) {
            Obj superMethod = Tab.currentScope().getOuter().findSymbol(methodDecl.getMethodDeclStart().getIdent());
            if (superMethod != null) {
                // change superMethod locals
                Tab.chainLocalSymbols(superMethod);
                superMethod.setFpPos(this.currentMethod.getCurrMethod().getFpPos());
                this.currentMethod.setCurrMethod(superMethod);
            }
            else {
                this.currentMethod.setFormalParameterCnt();
                Tab.chainLocalSymbols(this.currentMethod.getCurrMethod());
                Tab.currentScope().getOuter().addToLocals(this.currentMethod.getCurrMethod());
            }
        }
        else {
            this.currentMethod.setFormalParameterCnt();
            Tab.chainLocalSymbols(this.currentMethod.getCurrMethod());
            Tab.currentScope().getOuter().addToLocals(this.currentMethod.getCurrMethod());
        }
        Tab.closeScope();
        methodDecl.getMethodDeclStart().obj = methodDecl.obj = this.currentMethod.getCurrMethod();
        this.currentMethod = null;
    }

    // CLASS ---------------------------------------------------------------
    public void visit(ClassDeclStart classDeclStart) {
        if (!(classDeclStart.getParent() instanceof ClassDeclError)) {
            String identifier = classDeclStart.getIdent();
            if (SemanticAnalyzer.isAlreadyDeclared(identifier)) {
                report_error("Symbol " + identifier + " already declared |", classDeclStart);
                return;
            }

            Obj obj = Tab.insert(Obj.Type, identifier, new Struct(Struct.Class)); // type node
            this.currentClass = new CurrentClass(obj);
            classDeclStart.obj = obj;

            this.openScope(Scope.CLASS);

            Tab.insert(Obj.Fld, VIRTUAL_METHOD_TABLE, Tab.intType); // Vitual Method Table
        }
    }

    public void visit(ExtendsIndeed extendsIndeed) {
        if (currType.getKind() != Struct.Class) {
            report_error(extendsIndeed.getType().getIdent() + " ain't declared |", extendsIndeed);
            return;
        }
        if (currType.getElemType() != null && currType.getElemType().equals(RECORD_STRUCT)) {
            report_error(extendsIndeed.getType().getIdent() + " ain't no class, it's record |", extendsIndeed);
            return;
        }

        // set parent class
        this.currentClass.getCurrClass().getType().setElementType(currType);
        // copy all super fields
        currType.getMembers()
                .stream()
                .filter(obj -> obj.getKind() == Obj.Fld)
                .filter(obj -> !Objects.equals(obj.getName(), VIRTUAL_METHOD_TABLE))
                .forEachOrdered(obj -> Tab.insert(Obj.Fld, obj.getName(), obj.getType()));
    }

    public void visit(ClassVarDeclList classVarDeclList) {
        if (this.currentClass.hasSuperClass()) {
            String superClassName = Tab.currentScope()
                    .getOuter()
                    .getLocals()
                    .symbols()
                    .stream()
                    .filter(obj -> obj.getKind() == Obj.Type && obj.getType().equals(this.currentClass.getSuperClass()))
                    .findFirst()
                    .get()
                    .getName();
            this.currentClass.getSuperClass()
                    .getMembers()
                    .stream()
                    .filter(obj -> obj.getKind() == Obj.Meth)
                    .filter(obj -> !obj.getName().equals(superClassName)) // don't copy constructors
                    .forEachOrdered(superMethod -> {
                        Obj copiedMethod = Tab.insert(Obj.Meth, superMethod.getName(), superMethod.getType());
                        copiedMethod.setLevel(superMethod.getLevel());
                        copiedMethod.setAdr(superMethod.getAdr());
                        copiedMethod.setFpPos(superMethod.getFpPos());

                        // copy locals
                        Tab.openScope();
                        superMethod.getLocalSymbols().forEach(local -> {
                            Obj obj = Tab.insert(local.getKind(), local.getName(), local.getType());
                            obj.setFpPos(local.getFpPos());
                        });
                        Tab.chainLocalSymbols(copiedMethod);
                        Tab.closeScope();
                    });
        }
    }

    public void visit(ClassDeclValid classDeclValid) {
        // foreach method update this reference
        SymbolDataStructure currentScopeLocals = Tab.currentScope().getLocals();
        if (currentScopeLocals != null)
            currentScopeLocals.symbols()
                    .stream()
                    .filter(obj -> obj.getKind() == Obj.Meth)
                    .forEach(methodObj -> {
                        // Update THIS reference
                        Tab.openScope();
                        Tab.insert(Obj.Var, THIS, currentClass.getCurrClass().getType());
                        methodObj.getLocalSymbols()
                                .stream()
                                .skip(1)
                                .forEach(local -> {
                                    Obj obj = Tab.insert(local.getKind(), local.getName(), local.getType());
                                    obj.setFpPos(local.getFpPos());
                                });
                        Tab.chainLocalSymbols(methodObj);
                        Tab.closeScope();
                    });

        Tab.chainLocalSymbols(this.currentClass.getCurrClass().getType());
        this.closeScope();
        currentClass = null;
    }

    // RECORD ---------------------------------------------------------------
    public void visit(RecordDeclStart recordDeclStart) {
        String identifier = recordDeclStart.getIdent();
        if (SemanticAnalyzer.isAlreadyDeclared(identifier)) {
            report_error("Symbol " + identifier + " already declared |", recordDeclStart);
            return;
        }

        int kind = Obj.Type;
        Struct type = new Struct(Struct.Class);
        type.setElementType(RECORD_STRUCT);
        Obj obj = Tab.insert(kind, identifier, type); // type node

        this.currentClass = new CurrentClass(obj);
        recordDeclStart.obj = obj;

        this.openScope(Scope.CLASS);
        this.report_info("Record declared (" + identifier + ")", recordDeclStart);
    }

    public void visit(RecordDecl recordDecl) {
        Tab.chainLocalSymbols(this.currentClass.getCurrClass().getType());
        this.closeScope();
        currentClass = null;
    }

    // -------------------------------------------------------
    public void visit(DesignatorIdent designatorIdent) {
        String identifier = designatorIdent.getIdent();
        superCall = false;
        if (identifier.equals("super")) {
            if (!this.insideClass()) {
                designatorIdent.obj = Tab.noObj;
                report_error("'super' reference used outside of class.", designatorIdent);
                return;
            }
            if (!this.currentClass.hasSuperClass()) {
                designatorIdent.obj = Tab.noObj;
                report_error("'super' reference used inside of class with no super class.", designatorIdent);
                return;
            }

            Obj obj;
            if (this.insideConstructor()) {
                final String superClassName = Tab.currentScope()
                        .getOuter()
                        .getOuter()
                        .getLocals()
                        .symbols()
                        .stream()
                        .filter(o -> o.getKind() == Obj.Type && o.getType().equals(this.currentClass.getSuperClass()))
                        .findFirst()
                        .get()
                        .getName();
                obj = this.currentClass.getCurrClass().getType().getElemType().getMembers()
                        .stream()
                        .filter(o -> o.getKind() == Obj.Meth)
                        .filter(o -> o.getName().equals(superClassName))
                        .findFirst()
                        .orElse(null);
                superCall = true;
            }
            else
                obj = this.currentClass.getCurrClass().getType().getElemType().getMembers()
                        .stream()
                        .filter(o -> o.getKind() == Obj.Meth && o.getName().equals(this.currentMethod.getCurrMethod().getName()))
                        .findFirst()
                        .orElse(null);

            if (obj == null) {
                designatorIdent.obj = Tab.noObj;
                report_error("Invalid 'super' reference used - Method " + this.currentMethod.getCurrMethod().getName() + " does not override another method.", designatorIdent);
                return;
            }
            designatorIdent.obj = obj;
            return;
        }
        Obj obj = Tab.find(identifier);
        if (obj.equals(Tab.noObj)) {
            designatorIdent.obj = Tab.noObj;
            report_error("Identifier " + identifier + " used but never declared.", designatorIdent);
            return;
        }
        designatorIdent.obj = obj;

        if (obj.getKind() == Obj.Fld) {
            report_info("Class member detected (" + identifier + ")", designatorIdent, obj);
            if (isRecord(obj.getType())) {
                report_info("Record detected (" + identifier + ")", designatorIdent, obj);
            }
        }
        else
            if (obj.getKind() == Obj.Var) {
                if (Tab.currentScope().findSymbol(obj.getName()) != null) // local variable
                    if (isFormalParameter(obj))
                        report_info("Formal parameter detected (" + identifier + ")", designatorIdent, obj);
                    else
                        report_info("Local variable detected (" + identifier + ")", designatorIdent, obj);
                else
                    report_info("Global variable detected (" + identifier + ")", designatorIdent, obj);
                if (isRecord(obj.getType())) {
                    report_info("Record detected (" + identifier + ")", designatorIdent, obj);
                }
            }

    }

    public void visit(DesignatorMemberReference designatorMemberReference) {
        String identifier = designatorMemberReference.getIdent();
        Designator designator = designatorMemberReference.getDesignator();
        Obj designatorObj = designator.obj; // cvor u tabeli simbola
        if (designatorObj == null || designatorObj.getType().getKind() != Struct.Class) {
            report_error("Identifier (" + (designatorObj == null ? "null" : designatorObj.getName()) + ") is not a class/record.", designatorMemberReference);
            designatorMemberReference.obj = Tab.noObj;
            return;
        }
        Obj obj;
        if (designatorObj.getName().equals(THIS)) {
            if (!this.insideClass()) {
                report_error("'this' reference detected outside of class.", designatorMemberReference);
                designatorMemberReference.obj = Tab.noObj;
                return;
            }
            obj = Tab.currentScope().getOuter().findSymbol(identifier);
        }
        else
            obj = designatorObj.getType().getMembers()
                    .stream()
                    .filter(member -> /*member.getKind() == Obj.Fld && */member.getName().equals(identifier))
                    .findFirst()
                    .orElse(null);

        if (obj == null) {
            report_error("Identifier (" + identifier + ") is not a member of a class/record " + designatorObj.getName(), designatorMemberReference);
            designatorMemberReference.obj = Tab.noObj;
            return;
        }

        designatorMemberReference.obj = obj;

        report_info("Member reference detected: " + designatorObj.getName() + " DOT " + identifier, designatorMemberReference);
    }

    public void visit(DesignatorArrayReference designatorArrayReference) {
        Designator designator = designatorArrayReference.getDesignator();
        Obj designatorObj = designator.obj; // cvor u tabeli simbola
        if (designatorObj.getType().getKind() != Struct.Array) {
            designatorArrayReference.obj = Tab.noObj;
            report_error("Identifier (" + designatorObj.getName() + ") ain't no array.", designatorArrayReference);
            return;
        }
        if (!designatorArrayReference.getExpr().struct.equals(Tab.intType)) {
            designatorArrayReference.obj = Tab.noObj;
            report_error("Expression inside brackets must have int type.", designatorArrayReference);
            return;
        }
        designatorArrayReference.obj = new Obj(Obj.Elem, designatorObj.getName(), designatorObj.getType().getElemType());
        report_info("Array reference: " + designatorObj.getName() + "[] ", designatorArrayReference);
    }

    public void visit(DesignatorStatement designatorStatement) {
        if (designatorStatement.getDesignatorOperation() instanceof DesignatorAssignOperation) {
            Obj designatorObj = designatorStatement.getDesignator().obj;
            int designatorKind = designatorObj.getKind();
            if (designatorKind != Obj.Fld && designatorKind != Obj.Elem && designatorKind != Obj.Var) {
                report_error("Identifier (" + designatorObj.getName() + ") must be either a class/record member, an array element or a variable.", designatorStatement.getDesignator());
                return;
            }
            Struct dstType = designatorObj.getType();
            Struct srcType = ((DesignatorAssignOperation) designatorStatement.getDesignatorOperation()).getExpr().struct;

            if (!isAssignable(dstType, srcType)) {
                report_error("Incompatible assignment operation types.", designatorStatement);
                return;
            }
        }
        else
            if (designatorStatement.getDesignatorOperation() instanceof DesignatorIncOperation || designatorStatement.getDesignatorOperation() instanceof DesignatorDecOperation) {
                Obj designatorObj = designatorStatement.getDesignator().obj;
                int designatorKind = designatorObj.getKind();
                if (designatorKind != Obj.Fld && designatorKind != Obj.Elem && designatorKind != Obj.Var) {
                    report_error("Invalid INC/DEC operation - Identifier (" + designatorObj.getName() + ") must be either a class/record member, an array element or a variable.", designatorStatement.getDesignator());
                    return;
                }
                Struct designatorType = designatorObj.getType();
                if (!designatorType.equals(Tab.intType)) {
                    report_error("Invalid INC/DEC operation - Identifier (" + designatorObj.getName() + ") must be int type.", designatorStatement.getDesignator());
                    return;
                }
            }
            else
                if (designatorStatement.getDesignatorOperation() instanceof DesignatorFuncCallOperation) {
                    Obj designatorObj = designatorStatement.getDesignator().obj;
                    int designatorKind = designatorObj.getKind();
                    if (designatorKind != Obj.Meth) {
                        report_error("Invalid method call - Identifier (" + designatorObj.getName() + ") ain't no method.", designatorStatement.getDesignator());
                        return;
                    }
                    // get formal parameters
                    List<Struct> formalParametersTypeList = designatorObj.getLocalSymbols()
                            .stream()
                            .limit(designatorObj.getLevel()) // formal parameters only
                            .filter(obj -> !obj.getName().equals(THIS)) // skip this
                            .map(Obj::getType)
                            .collect(Collectors.toList());

                    int optArgsCnt = designatorObj.getFpPos();

                    // actual parameters
                    List<Struct> actualParametersList = new ArrayList<>();

                    if (((DesignatorFuncCallOperation) designatorStatement.getDesignatorOperation()).getActParsOrNothing() instanceof ActParsIndeed) {
                        ActParsIndeed actPars = (ActParsIndeed) ((DesignatorFuncCallOperation) designatorStatement.getDesignatorOperation()).getActParsOrNothing();
                        Struct firstActualParameter = actPars.getExpr().struct;

                        ExprList exprList = actPars.getExprList();
                        while (exprList instanceof ExprListIndeed) {
                            ExprListIndeed exprListIndeed = (ExprListIndeed) exprList;
                            actualParametersList.add(exprListIndeed.getExpr().struct);
                            exprList = exprListIndeed.getExprList();
                        }
                        actualParametersList.add(firstActualParameter);
                        Collections.reverse(actualParametersList);
                    }

                    if (actualParametersList.size() > formalParametersTypeList.size() || actualParametersList.size() < formalParametersTypeList.size() - optArgsCnt) {
                        report_error("Invalid method call - Method " + designatorObj.getName() + "() called with invalid number of arguments.", designatorStatement.getDesignator());
                        return;
                    }

                    for (int i = 0; i < actualParametersList.size(); i++) {
                        Struct dstType = formalParametersTypeList.get(i);
                        Struct srcType = actualParametersList.get(i);
                        if (!SemanticAnalyzer.isAssignable(dstType, srcType)) {
                            report_error("Invalid method call - Method " + designatorObj.getName() + "() called with invalid arguments.", designatorStatement.getDesignator());
                            return;
                        }
                    }

                    // report_info global function call
                    if (designatorStatement.getDesignator() instanceof DesignatorIdent && !this.scopeStack.contains(Scope.CLASS)) {
                        report_info("Global function call detected - " + designatorObj.getName() + "()", designatorStatement);
                    }
                    else
                        if (designatorStatement.getDesignator() instanceof DesignatorIdent && superCall) {
                            report_info("Super call detected inside a constructor " + designatorObj.getName() + "()", designatorStatement);
                        }
                }
    }

    public void visit(ReadStatement readStatement) {
        Obj designatorObj = readStatement.getDesignator().obj;
        if (designatorObj == null)
            return;
        int designatorKind = designatorObj.getKind();
        if (designatorKind != Obj.Fld && designatorKind != Obj.Elem && designatorKind != Obj.Var) {
            report_error("Invalid read statement - Identifier (" + designatorObj.getName() + ") must be either a class/record member, an array element or a variable.", readStatement);
            return;
        }
        Struct designatorType = designatorObj.getType();
        if (!designatorType.equals(Tab.intType) && !designatorType.equals(Tab.charType) && !designatorType.equals(BOOL_STRUCT)) {
            report_error("Invalid read statement - Identifier (" + designatorObj.getName() + ") must be either int, char or bool type.", readStatement);
            return;
        }
    }

    public void visit(ExprCondFact exprCondFact) {
        Struct expr1Struct = exprCondFact.getExpr().struct;

        RelopExprOrNothing relopExprOrNothing = exprCondFact.getRelopExprOrNothing();
        if (relopExprOrNothing instanceof NoRelopExpr) {
            if (!expr1Struct.equals(BOOL_STRUCT)) {
                report_error("Invalid condition statement - expression must be bool type.", exprCondFact);
                return;
            }
        }
        else {
            RelopExprIndeed relopExprIndeed = (RelopExprIndeed) relopExprOrNothing;
            Struct expr2Struct = relopExprIndeed.getExpr().struct;

            if (!areCompatible(expr1Struct, expr2Struct)) {
                report_error("Invalid condition statement - expressions must be compatible.", exprCondFact);
                return;
            }
            if (expr1Struct.getKind() == Struct.Array || expr1Struct.getKind() == Struct.Class) {
                Relop relop = relopExprIndeed.getRelop();
                if (!(relop instanceof Eq) && !(relop instanceof Neq)) {
                    report_error("Invalid condition statement - class objects and arrays can be compared only using '==' or '!=' operators.", exprCondFact);
                    return;
                }
            }
        }
    }


    public void visit(ReturnStatement returnStatement) {
        if (!this.scopeStack.contains(Scope.METHOD)) {
            report_error("Invalid return statement - return statement outside of function/method.", returnStatement);
            return;
        }

        if (returnStatement.getExprOrNothing() instanceof NoExpr && !this.currentMethod.isVoid()) {
            report_error("Invalid return statement - return statement must return a value.", returnStatement);
            return;
        }
        else
            if (returnStatement.getExprOrNothing() instanceof ExprIndeed) {
                Struct exprStruct = ((ExprIndeed) returnStatement.getExprOrNothing()).getExpr().struct;
                if (!isAssignable(this.currentMethod.getCurrMethod().getType(), exprStruct)) {
                    report_error("Invalid return statement - returned type does not match method/function type.", returnStatement);
                    return;
                }
            }
    }

    public void visit(PrintStatement printStatement) {
        Struct exprStruct = printStatement.getExpr().struct;
        if (!exprStruct.equals(Tab.intType) && !exprStruct.equals(Tab.charType) && !exprStruct.equals(BOOL_STRUCT)) {
            report_error("Invalid print statement - expression inside the statement must be either int, char or bool type.", printStatement);
            return;
        }
    }

    public void visit(BreakStatement breakStatement) {
        if (!this.getCurrScope().equals(Scope.DO_WHILE)) {
            report_error("Break statement must be inside do-while loop.", breakStatement);
        }
    }

    public void visit(ContinueStatement continueStatement) {
        if (!this.getCurrScope().equals(Scope.DO_WHILE)) {
            report_error("Continue statement must be inside do-while loop.", continueStatement);
        }
    }

    public void visit(DoWhileStatementStart doWhileStatementStart) { // needed for continue and break statements
        this.scopeStack.push(Scope.DO_WHILE);
    }

    public void visit(DoWhileStatement doWhileStatement) {
        this.scopeStack.pop();
    }

    public void visit(BaseExpNumber baseExp) {
        baseExp.struct = Tab.intType;
    }

    public void visit(BaseExpChar baseExp) {
        baseExp.struct = Tab.charType;
    }

    public void visit(BaseExpBool baseExp) {
        baseExp.struct = BOOL_STRUCT;
    }

    public void visit(BaseExpNewInstance baseExpNewInstance) {
        // new int[5]
        if (baseExpNewInstance.getBracketsWithExprOrNothing() instanceof BracketsWithExprIndeed) {
            baseExpNewInstance.struct = new Struct(Struct.Array, baseExpNewInstance.getType().struct);

            BracketsWithExprIndeed bracketsWithExprIndeed = (BracketsWithExprIndeed) baseExpNewInstance.getBracketsWithExprOrNothing();
            if (!bracketsWithExprIndeed.getExpr().struct.equals(Tab.intType)) {
                report_error("Expression inside brackets must have int type.", baseExpNewInstance);
                return;
            }
        }
        else { // new A
            baseExpNewInstance.struct = baseExpNewInstance.getType().struct;
            if (baseExpNewInstance.getType().struct.getKind() != Struct.Class) {
                report_error("Type " + baseExpNewInstance.getType().getIdent() + " is not a class.", baseExpNewInstance);
                return;
            }

            report_info("New instance of class (" + baseExpNewInstance.getType().getIdent() + ") detected.", baseExpNewInstance, Tab.find(baseExpNewInstance.getType().getIdent()));
        }
    }


    public void visit(AddopTermListIndeed addopTermListIndeed) {
        if (!addopTermListIndeed.getTerm().struct.equals(Tab.intType)) {
            report_error("Invalid expression - term in add operation must have int type.", addopTermListIndeed);
            return;
        }
    }

    public void visit(MulopFactorListIndeed mulopFactorListIndeed) {
        if (!mulopFactorListIndeed.getFactor().struct.equals(Tab.intType)) {
            report_error("Invalid expression - factor in mul operation must have int type.", mulopFactorListIndeed);
            return;
        }
    }

    public void visit(NegativeExpr negativeExpr) {
        if (!negativeExpr.getTerm().struct.equals(Tab.intType)) {
            report_error("Invalid expression - negated term must have int type.", negativeExpr);
            negativeExpr.struct = Tab.noType;
            return;
        }
        negativeExpr.struct = Tab.intType;
    }

    public void visit(PositiveExpr positiveExpr) {
        if (positiveExpr.getAddopTermList() instanceof AddopTermListIndeed) {
            if (!positiveExpr.getTerm().struct.equals(Tab.intType)) {
                positiveExpr.struct = Tab.noType;
                report_error("Invalid expression - terms in add operation must have int type.", positiveExpr.getAddopTermList());
                return;
            }
        }
        positiveExpr.struct = positiveExpr.getTerm().struct;
    }

    public void visit(Expr expr) {
        IfNullExprOrNothing ifNullExprOrNothing = expr.getIfNullExprOrNothing();
        if (ifNullExprOrNothing instanceof IfNullExprIndeed && !expr.getBasicExpr().struct.equals(Tab.intType)) {
            expr.struct = Tab.noType;
            report_error("Invalid expression - expressions inside IF NULL (??) expression must have int type.", expr.getBasicExpr());
            return;
        }
        expr.struct = expr.getBasicExpr().struct;
    }

    public void visit(IfNullExprIndeed ifNullExprIndeed) {
        if (!ifNullExprIndeed.getExpr().struct.equals(Tab.intType)) {
            report_error("Invalid expression - expressions inside IF NULL (??) expression must have int type.", ifNullExprIndeed.getExpr());
            return;
        }
    }

    public void visit(Term term) {
        if (term.getMulopFactorList() instanceof MulopFactorListIndeed) {
            if (!term.getFactor().struct.equals(Tab.intType)) {
                term.struct = Tab.noType;
                report_error("Invalid expression - factor in mul operation must have int type.", term.getMulopFactorList());
                return;
            }
        }
        term.struct = term.getFactor().struct;
    }

    public void visit(Factor factor) {
        factor.struct = factor.getBaseExp().struct;
    }

    public void visit(BaseExpExpr baseExpExpr) {
        baseExpExpr.struct = baseExpExpr.getExpr().struct;
    }

    public void visit(BaseExpDesignator baseExpDesignator) {
        baseExpDesignator.struct = baseExpDesignator.getDesignator().obj.getType();
    }

    // helper methods --------------------------------

    private static boolean isAssignable(Struct dstType, Struct srcType) {
        if (sameClass(srcType, dstType))
            return true;
        if (isDerivedClass(dstType, srcType))
            return false;
        return isDerivedClass(srcType, dstType) || srcType.assignableTo(dstType);
    }

    private static boolean sameClass(Struct struct1, Struct struct2) {
        if (struct1.getKind() == Struct.Class && struct2.getKind() == Struct.Class && (!isRecord(struct1) && !isRecord(struct2))) {
            return struct1.hashCode()==struct2.hashCode();
        }
        else
            if (struct1.getKind() == Struct.Array && struct2.getKind() == Struct.Array) {
                return sameClass(struct1.getElemType(), struct2.getElemType());
            }
        return false;
    }

    private boolean areCompatible(Struct struct1, Struct struct2) {
        return sameClass(struct1, struct2) || isDerivedClass(struct1, struct2) || isDerivedClass(struct2, struct1) || struct1.compatibleWith(struct2);
    }

    private static boolean isDerivedClass(Struct srcType, Struct dstType) {
        if (srcType.getKind() == Struct.Class && dstType.getKind() == Struct.Class && (!isRecord(srcType) && !isRecord(dstType))) {
            while (srcType.getElemType() != null) {
                srcType = srcType.getElemType();
                if (srcType.equals(dstType))
                    return true;
            }
        }
        else
            if (srcType.getKind() == Struct.Array && dstType.getKind() == Struct.Array) {
                return isDerivedClass(srcType.getElemType(), dstType.getElemType());
            }
        return false;
    }

    private static boolean isRecord(Struct srcType) {
        if (srcType.getElemType() == null)
            return false;
        return srcType.getElemType().equals(RECORD_STRUCT);
    }

    private boolean isFormalParameter(Obj variable) {
        int formalParameterCnt = this.currentMethod.getFormalParameterCnt();
        return variable.getAdr() < formalParameterCnt;
    }

    private boolean insideClass() {
        return this.scopeStack.contains(Scope.CLASS);
    }

    private boolean insideConstructor() {
        if (!this.insideClass())
            return false;
        return this.currentMethod.getCurrMethod().getName().equals(this.currentClass.getCurrClass().getName());
    }

    private Scope getCurrScope() {
        return this.scopeStack.peek();
    }

    private void openScope(Scope scope) {
        this.scopeStack.push(scope);
        Tab.openScope();
    }

    private void closeScope() {
        this.scopeStack.pop();
        Tab.closeScope();
    }

    private boolean superCall = false;

    private static boolean isAlreadyDeclared(String identifier) {
        return Tab.currentScope().findSymbol(identifier) != null;
    }

    public int getGlobalVarCnt() {
        return globalVarCnt;
    }
}

