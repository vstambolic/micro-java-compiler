package rs.ac.bg.etf.pp1;

import java_cup.runtime.Symbol;

%%

%{

	// ukljucivanje informacije o poziciji tokena
	private Symbol new_symbol(int type) {
		return new Symbol(type, yyline+1, yycolumn);
	}
	
	// ukljucivanje informacije o poziciji tokena
	private Symbol new_symbol(int type, Object value) {
		return new Symbol(type, yyline+1, yycolumn, value);
	}

%}

%cup
%line
%column

%eofval{
	return new_symbol(sym.EOF);
%eofval}

%%

// Whitespaces -----------------------------------------------

" " 	{ }
"\b" 	{ }
"\t" 	{ }
"\r\n" 	{ }
"\f" 	{ }

// Comments --------------------------------------------------
"//" .* [\n\r] {  }

// Keywords --------------------------------------------------

"program"   { return new_symbol(sym.PROG, yytext()); }
"break" 	{ return new_symbol(sym.BREAK, yytext()); }
"class" 	{ return new_symbol(sym.CLASS, yytext()); }
"enum" 		{ return new_symbol(sym.ENUM, yytext()); }
"else" 		{ return new_symbol(sym.ELSE, yytext()); }
"const" 	{ return new_symbol(sym.CONST, yytext()); }
"if" 		{ return new_symbol(sym.IF, yytext()); }
"do" 		{ return new_symbol(sym.DO, yytext()); }
"while" 	{ return new_symbol(sym.WHILE, yytext()); }
"new" 		{ return new_symbol(sym.NEW, yytext()); }
"print"		{ return new_symbol(sym.PRINT, yytext()); }
"read"		{ return new_symbol(sym.READ, yytext()); }
"return"	{ return new_symbol(sym.RETURN, yytext()); }
"void"		{ return new_symbol(sym.VOID, yytext()); }
"extends"	{ return new_symbol(sym.EXTENDS, yytext()); }
"continue"	{ return new_symbol(sym.CONTINUE, yytext()); }
//"this"		{ return new_symbol(sym.THIS, yytext()); }
//"super"		{ return new_symbol(sym.SUPER, yytext()); }
"record"	{ return new_symbol(sym.RECORD, yytext()); }

// Operators -------------------------------------------------

"instanceof"  { return new_symbol(sym.INSTANCEOF, yytext()); }
"++"          { return new_symbol(sym.INC, yytext()); }
"--"          { return new_symbol(sym.DEC, yytext()); }
"+"           { return new_symbol(sym.ADD, yytext()); }
"-"           { return new_symbol(sym.SUB, yytext()); }
"*"           { return new_symbol(sym.MUL, yytext()); }
"/"           { return new_symbol(sym.DIV, yytext()); }
"%"           { return new_symbol(sym.MOD, yytext()); }
"^"           { return new_symbol(sym.EXP, yytext()); }
"=="          { return new_symbol(sym.EQ, yytext()); }
"!="          { return new_symbol(sym.NEQ, yytext()); }
">="          { return new_symbol(sym.GTE, yytext()); }
">"           { return new_symbol(sym.GT, yytext()); }
"<="          { return new_symbol(sym.LTE, yytext()); }
"<"           { return new_symbol(sym.LT, yytext()); }
"&&"          { return new_symbol(sym.AND, yytext()); }
"||"          { return new_symbol(sym.OR, yytext()); }
"="           { return new_symbol(sym.ASSIGN, yytext()); }
";"           { return new_symbol(sym.SEMICOLON, yytext()); }
":"           { return new_symbol(sym.COLON, yytext()); }
","           { return new_symbol(sym.COMMA, yytext()); }
"..."         { return new_symbol(sym.VARARGS, yytext()); }
"."           { return new_symbol(sym.DOT, yytext()); }
"("           { return new_symbol(sym.LPAREN, yytext()); }
")"           { return new_symbol(sym.RPAREN, yytext()); }
"["           { return new_symbol(sym.LBRACKET, yytext()); }
"]"           { return new_symbol(sym.RBRACKET, yytext()); }
"{"           { return new_symbol(sym.LBRACE, yytext()); }
"}"           { return new_symbol(sym.RBRACE, yytext()); }

// Literals --------------------------------------------------

[0-9]+             { return new_symbol(sym.NUMBER, new Integer (yytext())); }
("true" | "false") { return (yytext().equals("true"))? new_symbol(sym.BOOL, true) : new_symbol(sym.BOOL, false);}
\' [^\'\n\r\\] \'  { return new_symbol(sym.CHAR, yytext().charAt(1)); }

// Identifiers -----------------------------------------------

([a-z]|[A-Z])[a-zA-Z0-9_]* 	{return new_symbol (sym.IDENT, yytext()); }

// Error -----------------------------------------------------
. { System.err.println("Leksicka greska ("+yytext()+") u liniji "+(yyline+1)); }






