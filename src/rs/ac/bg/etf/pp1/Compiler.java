package rs.ac.bg.etf.pp1;

import java_cup.runtime.Symbol;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;
import rs.ac.bg.etf.pp1.ast.SyntaxNode;
import rs.ac.bg.etf.pp1.util.Log4JUtils;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.mj.runtime.Run;
import rs.etf.pp1.mj.runtime.disasm;
import rs.etf.pp1.symboltable.Tab;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;

public class Compiler {

    static {
        DOMConfigurator.configure(Log4JUtils.instance().findLoggerConfigFile());
        Log4JUtils.instance().prepareLogFile(Logger.getRootLogger());
    }

    private static void tsdump() {
        Tab.dump();
    }

    public static void main(String[] args) throws Exception {
        Logger log = Logger.getLogger(Compiler.class);
        if (args.length < 1) {
            log.error("Not enough arguments supplied! Usage: Compiler <source-file> [<obj-file>] ");
            return;
        }

        File sourceCode = new File(args[0]);
        if (!sourceCode.exists()) {
            log.error("Source file [" + sourceCode.getAbsolutePath() + "] not found!");
            return;
        }

        log.info("Compiling source file: " + sourceCode.getAbsolutePath());

        try (BufferedReader br = new BufferedReader(new FileReader(sourceCode))) {
            Yylex lexer = new Yylex(br);
            MJParser p = new MJParser(lexer);
            Symbol s = p.parse();
            SyntaxNode prog = (SyntaxNode)(s.value);

            Tab.init(); // Universe scope
            SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer();
            prog.traverseBottomUp(semanticAnalyzer);

            Compiler.tsdump();

            if (!p.errorDetected && semanticAnalyzer.semanticCheckPassed()) {
                String objFilePath = args.length>1? args[1] : args[0].replace(".mj",".obj");
                File objFile = new File(objFilePath);
                log.info("Generating bytecode file: " + objFile.getAbsolutePath());
                if (objFile.exists())
                    objFile.delete();

                // Code generation...
                CodeGenerator codeGenerator = CodeGenerator.getInstance();
                prog.traverseBottomUp(codeGenerator);
                Code.mainPc = codeGenerator.getMainPc();
                Code.write(Files.newOutputStream(objFile.toPath()));
                log.info("MJ compiler finished with success.");

                disasm.main(new String[]{objFilePath});
                Run.main(new String[]{objFilePath/*,"-debug"*/});
            }
            else {
                log.error("There were compile errors, no code produced.");
            }
        }
    }

}
