package rs.ac.bg.etf.pp1;
import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;
import rs.ac.bg.etf.pp1.semantic_analyzer_utils.CurrentClass;
import rs.ac.bg.etf.pp1.semantic_analyzer_utils.CurrentMethod;
import rs.ac.bg.etf.pp1.semantic_analyzer_utils.Scope;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.structure.SymbolDataStructure;

import java.util.*;
import java.util.stream.Collectors;

public class SemanticAnalyzer extends VisitorAdaptor {

	// Constants ---------------------------------
	private static final String THIS = "this";
	public static final int RECORD = 8;
	private static final Struct recordStruct = new Struct(RECORD);

	// Helpers -----------------------------------
	private Stack<Scope> scopeStack = new Stack<>();
	private CurrentClass currentClass = null;
	private CurrentMethod currentMethod = null;

	private Struct currType = Tab.noType;
	boolean errorDetected = false;

	int nVars;

	// Currents ----------------------------

	Logger log = Logger.getLogger(getClass());


	public SemanticAnalyzer() {
		Tab.currentScope().addToLocals(new Obj(Obj.Type,"bool", new Struct(Struct.Bool)));
	}

	public void report_error(String message, SyntaxNode info) {
		errorDetected = true;
		StringBuilder msg = new StringBuilder(message);
		int line = (info == null) ? 0: info.getLine();
		if (line != 0)
			msg.append (" line ").append(line);
		log.error(msg.toString());
	}
	public void report_info(String message, SyntaxNode info) {
		StringBuilder msg = new StringBuilder(message);
		int line = (info == null) ? 0: info.getLine();
		if (line != 0)
			msg.append (" line ").append(line);
		log.info(msg.toString());
	}



	// PROGRAM ---------------------------------------------------------------
	/**
	 * Opening program scope
	 * @param programDecl
	 */
	public void visit(ProgramDecl programDecl) {
		programDecl.obj = Tab.insert(Obj.Prog, programDecl.getIdent(), Tab.noType);
		Tab.openScope();
		this.scopeStack.push(Scope.PROGRAM);
	}

	/**
	 * Poziva se nakon cele analize
	 * @param program
	 */
	public void visit(Program program) {
		nVars = Tab.currentScope.getnVars();
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
	 * @param var
	 */
	public void visit(Var var) {
		String identifier = var.getIdent();
		if (SemanticAnalyzer.isAlreadyDeclared(identifier)) {
			report_error("Symbol " +identifier+" already declared |", var);
			return;
		}

		int kind = this.getCurrScope().equals(Scope.CLASS)? Obj.Fld : Obj.Var;
		Struct type = (var.getBrackets() instanceof BracketsIndeed ? new Struct(Struct.Array, currType) : currType);

		Obj obj = Tab.insert(kind,identifier,type);

		this.report_info( (this.getCurrScope().equals(Scope.PROGRAM)?"Global":"Local") + " variable declared (" + identifier + ").", var);

		// todo if is method signature
	}

	// CONST ---------------------------------------------------------------
	public void visit(ConstAssignment constAssignment) {
		String identifier = constAssignment.getIdent();
		if (SemanticAnalyzer.isAlreadyDeclared(identifier)) {
			report_error("Symbol " +identifier+" already declared ", constAssignment);
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
			if (literal instanceof  CharConst) {
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
					value = ((BoolConst) literal).getBoolVal()? 1 : 0;
				}
		Obj obj = Tab.insert(Obj.Con, identifier, currType);
		obj.setAdr(value);

		this.report_info( "Const declared (" + identifier + ")", constAssignment);
	}

	// todo record neka bude class ali da bi naglasio da iz njega ne moze nista da se izvodi upisi nesto u onaj elemtype


	// METHOD ---------------------------------------------------------------
	public void visit(TypeOrVoid_Void TypeOrVoid_Void) {
		this.currType = Tab.noType;
	}
	public void visit(MethodDeclStart methodDeclStart) {
		String identifier = methodDeclStart.getIdent();

		if (this.getCurrScope().equals(Scope.PROGRAM))
			if (SemanticAnalyzer.isAlreadyDeclared(identifier)) {
				report_error("Symbol " + identifier + " already declared ", methodDeclStart);
				return;
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

		Obj currMethodObj = new Obj(Obj.Meth, identifier, this.currType,0,0);
		this.currentMethod = new CurrentMethod(currMethodObj);
		methodDeclStart.obj = currMethodObj; // TODO koji ce mi ovo djavo
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
			report_error("Symbol " +identifier+" already declared |", formPar);
			return;
		}

		int kind = Obj.Var;
		Struct type = (formPar.getBrackets() instanceof BracketsIndeed ? new Struct(Struct.Array, currType) : currType);

		Obj obj = Tab.insert(kind, identifier, type);

		this.report_info(" Formal parameter declared (" + identifier + ").", formPar);
	}
	public void visit(MethodDecl methodDecl) {
		this.scopeStack.pop();
		if (this.getCurrScope().equals(Scope.CLASS)) {
			Obj superMethod = Tab.currentScope().getOuter().findSymbol(methodDecl.getMethodDeclStart().getIdent());
			if (superMethod != null) {
				// check formal parameters
				if (superMethod.getLevel() != this.currentMethod.getFormalParameterCnt()) {
					report_error("Method " + methodDecl.getMethodDeclStart().getIdent()+" does not have the same signature as its super method |", methodDecl);
					return;
				}
				else {
					List<Obj> superFormParams = superMethod.getLocalSymbols().stream().limit(superMethod.getLevel()).collect(Collectors.toList());
					List<Obj> methodFormParams = Tab.currentScope().getLocals().symbols().stream().limit(superMethod.getLevel()).collect(Collectors.toList());
					for (int i=1; i < superMethod.getLevel(); i++) {
						Obj superFormParam = superFormParams.get(i);
						Obj methodFormParam = methodFormParams.get(i);
						if (!superFormParam.getType().equals(methodFormParam.getType())) {
							report_error("Method " +methodDecl.getMethodDeclStart().getIdent()+" does not have the same signature as its super method |", methodDecl);
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
		methodDecl.obj = this.currentMethod.getCurrMethod();
		this.currentMethod = null;
	}

	// CLASS ---------------------------------------------------------------
	public void visit(ClassDeclStart classDeclStart) {
		if (!(classDeclStart.getParent() instanceof ClassDeclError)) {
			String identifier = classDeclStart.getIdent();
			if (SemanticAnalyzer.isAlreadyDeclared(identifier)) {
				report_error("Symbol " +identifier+" already declared |", classDeclStart);
				return;
			}

			Obj obj = Tab.insert(Obj.Type, identifier, new Struct(Struct.Class)); // type node
			this.currentClass = new CurrentClass(obj);
			classDeclStart.obj = obj;

			this.openScope(Scope.CLASS);
			// Tab.insert(Obj.Fld, "TVF", className.obj.getType()); TODO WAS IST DAS
		}
	}
	public void visit(ExtendsIndeed extendsIndeed) {
		if (currType.getKind() != Struct.Class || currType.getElemType().equals(recordStruct)) {
			report_error(extendsIndeed.getType().getIdent()+" ain't no class |", extendsIndeed);
			return;
		}

		// set parent class
		this.currentClass.getCurrClass().getType().setElementType(currType);
		// copy all super fields
		currType.getMembers()
				.stream()
				.filter(obj -> obj.getKind() == Obj.Fld)
				.forEachOrdered(obj -> Tab.insert(Obj.Fld, obj.getName(), obj.getType()));
	}
	public void visit(ClassVarDeclList classVarDeclList) {
			if (this.currentClass.hasSuperClass()) {
				this.currentClass.getSuperClass()
						.getMembers()
						.stream()
						.filter(obj -> obj.getKind() == Obj.Meth)
						.forEachOrdered(superMethod -> {
							Obj copiedMethod = Tab.insert(Obj.Meth, superMethod.getName(), superMethod.getType());
							copiedMethod.setLevel(superMethod.getLevel());
							copiedMethod.setAdr(superMethod.getAdr());

							// copy locals
							Tab.openScope();
							superMethod.getLocalSymbols().forEach(local -> Tab.insert(local.getKind(), local.getName(), local.getType()));
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
					methodObj.getLocalSymbols().stream().skip(1).forEach(local -> Tab.insert(local.getKind(), local.getName(), local.getType()));
					Tab.chainLocalSymbols(methodObj);
					Tab.closeScope();
				});

		Tab.chainLocalSymbols(this.currentClass.getCurrClass().getType());
		this.closeScope();
		currentClass = null;
	}

	// RECORD ---------------------------------------------------------------
	public void visit(RecordDeclStart recordDeclStart) {

	}

	public void visit(RecordDecl recordDecl) {

	}

	// helper methods --------------------------------

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

	private int getCurrentLevel() {
		return this.getCurrScope().equals(Scope.METHOD)? 1:0;
	}

	private static boolean isAlreadyDeclared(String identifier) {
		return Tab.currentScope().findSymbol(identifier) != null;
	}

//
//	public void visit(MethodDecl methodDecl) {
//		if (!returnFound && currentMethod.getType() != Tab.noType) {
//			report_error("Semanticka greska na liniji " + methodDecl.getLine() + ": funcija " + currentMethod.getName() + " nema return iskaz!", null);
//		}
//
//		Tab.chainLocalSymbols(currentMethod);
//		Tab.closeScope();
//
//		returnFound = false;
//		currentMethod = null;
//	}
//
//	public void visit(MethodTypeName methodTypeName) {
//		currentMethod = Tab.insert(Obj.Meth, methodTypeName.getMethName(), methodTypeName.getType().struct);
//		methodTypeName.obj = currentMethod;
//		Tab.openScope();
//		report_info("Obradjuje se funkcija " + methodTypeName.getMethName(), methodTypeName);
//	}
//
//	public void visit(Assignment assignment) {
//		if (!assignment.getExpr().struct.assignableTo(assignment.getDesignator().obj.getType()))
//			report_error("Greska na liniji " + assignment.getLine() + " : " + " nekompatibilni tipovi u dodeli vrednosti ", null);
//	}
//
//	public void visit(PrintStmt printStmt){
//		printCallCount++;
//	}

//	public void visit(ReturnExpr returnExpr){
//		returnFound = true;
//		Struct currMethType = currentMethod.getType();
//		if (!currMethType.compatibleWith(returnExpr.getExpr().struct)) {
//			report_error("Greska na liniji " + returnExpr.getLine() + " : " + "tip izraza u return naredbi ne slaze se sa tipom povratne vrednosti funkcije " + currentMethod.getName(), null);
//		}
//	}
//
//	public void visit(ProcCall procCall){
//		Obj func = procCall.getDesignator().obj;
//		if (Obj.Meth == func.getKind()) {
//			report_info("Pronadjen poziv funkcije " + func.getName() + " na liniji " + procCall.getLine(), null);
//			//RESULT = func.getType();
//		}
//		else {
//			report_error("Greska na liniji " + procCall.getLine()+" : ime " + func.getName() + " nije funkcija!", null);
//			//RESULT = Tab.noType;
//		}
//	}
//
//	public void visit(AddExpr addExpr) {
//		Struct te = addExpr.getExpr().struct;
//		Struct t = addExpr.getTerm().struct;
//		if (te.equals(t) && te == Tab.intType)
//			addExpr.struct = te;
//		else {
//			report_error("Greska na liniji "+ addExpr.getLine()+" : nekompatibilni tipovi u izrazu za sabiranje.", null);
//			addExpr.struct = Tab.noType;
//		}
//	}
//
//	public void visit(TermExpr termExpr) {
//		termExpr.struct = termExpr.getTerm().struct;
//	}
//
//	public void visit(Term term) {
//		term.struct = term.getFactor().struct;
//	}
//
//	public void visit(Const cnst){
//		cnst.struct = Tab.intType;
//	}
//
//	public void visit(Var var) {
//		var.struct = var.getDesignator().obj.getType();
//	}
//
//	public void visit(FuncCall funcCall){
//		Obj func = funcCall.getDesignator().obj;
//		if (Obj.Meth == func.getKind()) {
//			report_info("Pronadjen poziv funkcije " + func.getName() + " na liniji " + funcCall.getLine(), null);
//			funcCall.struct = func.getType();
//		}
//		else {
//			report_error("Greska na liniji " + funcCall.getLine()+" : ime " + func.getName() + " nije funkcija!", null);
//			funcCall.struct = Tab.noType;
//		}
//
//	}
//
//	public void visit(Designator designator){
//		Obj obj = Tab.find(designator.getName());
//		if (obj == Tab.noObj) {
//			report_error("Greska na liniji " + designator.getLine()+ " : ime "+designator.getName()+" nije deklarisano! ", null);
//		}
//		designator.obj = obj;
//	}
//
//	public boolean passed() {
//		return !errorDetected;
//	}

}

