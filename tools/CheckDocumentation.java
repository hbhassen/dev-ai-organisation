import com.sun.source.util.*;
import com.sun.source.tree.*;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Checks source documentation through the JDK syntax tree, without adding a parser dependency. Exigences : DEVAI-137, DEVAI-138. */
public final class CheckDocumentation {
    /** Parses every authored Java class and method and checks references against the actual specification. Exigences : DEVAI-137, DEVAI-138. */
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args.length==0?".":args[0]).toAbsolutePath().normalize();
        String specification=Files.readString(root.resolve("specification.md")); Set<String> ids=new HashSet<>();
        Matcher matcher=Pattern.compile("DEVAI-\\d{3}").matcher(specification); while(matcher.find()) ids.add(matcher.group());
        List<Path> files=new ArrayList<>();
        for(String directory:List.of("ods-contracts","ods-java-core","ods-gateway","tools")) try(var paths=Files.walk(root.resolve(directory))) {
            files.addAll(paths.filter(p->p.toString().endsWith(".java")&&!p.toString().contains(java.io.File.separator+"target"+java.io.File.separator)).toList());
        }
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler(); if(compiler==null) throw new IllegalStateException("JDK 21 required");
        List<String> errors=new ArrayList<>();
        try(var manager=compiler.getStandardFileManager(null,Locale.ROOT,java.nio.charset.StandardCharsets.UTF_8)) {
            JavacTask task=(JavacTask)compiler.getTask(null,manager,null,List.of("-proc:none"),null,manager.getJavaFileObjectsFromPaths(files));
            DocTrees docs=DocTrees.instance(task);
            for(CompilationUnitTree unit:task.parse()) new TreePathScanner<Void,Void>() {
                /** Validates named class documentation while allowing anonymous implementation expressions. Exigences : DEVAI-137. */
                @Override public Void visitClass(ClassTree tree,Void unused) { if(!tree.getSimpleName().toString().isEmpty()) check(tree.getSimpleName().toString()); return super.visitClass(tree,unused); }
                /** Includes constructors and private methods so no executable API is silently excluded. Exigences : DEVAI-137. */
                @Override public Void visitMethod(MethodTree tree,Void unused) { check(tree.getName().toString()); return super.visitMethod(tree,unused); }
                /** Requires an explanatory comment and at least one existing requirement identifier. Exigences : DEVAI-138. */
                private void check(String name) {
                    String comment=docs.getDocComment(getCurrentPath()); String location=root.relativize(Path.of(unit.getSourceFile().toUri()))+":"+name;
                    if(comment==null||comment.trim().length()<40) { errors.add(location+" : explanatory Javadoc missing"); return; }
                    Matcher refs=Pattern.compile("DEVAI-\\d{3}").matcher(comment); boolean found=false;
                    while(refs.find()) { found=true; if(!ids.contains(refs.group())) errors.add(location+" : unknown requirement "+refs.group()); }
                    if(!found) errors.add(location+" : requirement reference missing");
                }
            }.scan(unit,null);
        }
        if(!errors.isEmpty()) { errors.forEach(System.err::println); System.exit(1); }
        System.out.println("Java documentation verified: "+files.size()+" source files.");
    }
}
