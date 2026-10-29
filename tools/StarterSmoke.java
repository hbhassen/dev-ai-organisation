import org.organisation.ods.starter.StarterService;
import org.organisation.ods.runtime.ProcessRunner;
import java.nio.file.*;
import java.util.*;

/** Generates real standard projects for an explicit network-enabled acceptance run. Exigences : DEVAI-046, DEVAI-060. */
public final class StarterSmoke {
    /** Creates one new project per requested stack and leaves validation to the recorded build commands. Exigences : DEVAI-060, DEVAI-149. */
    public static void main(String[] args) throws Exception {
        if(args.length<2) throw new IllegalArgumentException("Expected destination and stacks");
        Path root=Path.of(args[0]).toAbsolutePath(); Files.createDirectories(root);
        try(ProcessRunner runner=new ProcessRunner()) {
            for(String stack:Arrays.copyOfRange(args,1,args.length)) {
                new StarterService(runner).create(root,"acceptance-"+stack.toLowerCase(Locale.ROOT),stack);
                System.out.println("Generated "+stack+" from its actual provider; build validation still required.");
            }
        }
    }
}
