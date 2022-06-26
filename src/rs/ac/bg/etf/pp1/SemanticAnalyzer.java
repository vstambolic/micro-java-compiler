package rs.ac.bg.etf.pp1;
import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
public class SemanticAnalyzer extends VisitorAdaptor {

	private enum Scope {PROGRAM, CLASS, RECORD, METHOD};
	private Scope currScope = null;

	// Helpers -----------------------------------
	private Struct currType = Tab.noType;
	boolean errorDetected = false;
	int printCallCount = 0;
	Obj currentMethod = null;
	boolean returnFound = false;
	int nVars;


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


	/**
	 * Opening program scope
	 * @param programDecl
	 */
	public void visit(ProgramDecl programDecl) {
		programDecl.obj = Tab.insert(Obj.Prog, programDecl.getIdent(), Tab.noType);
		Tab.openScope();
		this.currScope = Scope.PROGRAM;
	}

	/**
	 * Poziva se nakon cele analize
	 * @param program
	 */
	public void visit(Program program) {
		nVars = Tab.currentScope.getnVars();
		Tab.chainLocalSymbols(program.getProgramDecl().obj); // Iz currentScopa prebacuje sve u dati cvor kao locals
		Tab.closeScope();
	}

	/**
	 * Poziva se prilikom analize tipa, cuva se trenutni currType (Struct)
	 * kako bi nako
	 * @param type
	 */
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

		Obj obj = Tab.insert(
				Obj.Var,
				identifier,
				(var.getBrackets() instanceof BracketsIndeed ? new Struct(Struct.Array, currType) : currType)
		);

		this.report_info( (this.isGlobal()?"Global":"Local") + " variable declared (" + identifier + ").", var);

		// todo if is method signature
	}

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


	// helper methods --------------------------------

	private boolean isGlobal() {
		return this.currScope.equals(Scope.PROGRAM);
	}

	private int getCurrentLevel() {
		return this.currScope.equals(Scope.METHOD)? 1:0;
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

