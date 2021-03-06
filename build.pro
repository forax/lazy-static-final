import static com.github.forax.pro.Pro.*;
import static com.github.forax.pro.builder.Builders.*;

resolver.
    //checkForUpdate(true).
    dependencies(
        // JUnit 5
        "org.junit.jupiter.api=org.junit.jupiter:junit-jupiter-api:5.7.0",
        "org.junit.platform.commons=org.junit.platform:junit-platform-commons:7.0",
        "org.apiguardian.api=org.apiguardian:apiguardian-api:1.1.0",
        "org.opentest4j=org.opentest4j:opentest4j:1.2.0",
        
        // ASM 7
        "org.objectweb.asm:9.0",
        "org.objectweb.asm.util:9.0",
        "org.objectweb.asm.tree:9.0",
        "org.objectweb.asm.tree.analysis:9.0"
    )

compiler.
    sourceRelease(Runtime.version().feature()).   // build latest version
    enablePreview(true)

runner.
  enablePreview(true)
  
var rewriter = command("rewriter", () -> {  // rewrite bytecode to simulate lazy static fields
  runner.
    modulePath(location("target/main/exploded/"), location("deps")).
    module("fr.umlv.lazystaticfinal/fr.umlv.lazystaticfinal.Rewriter").
    mainArguments("target/main/exploded/fr.umlv.lazystaticfinal/");
  run(runner);
})  
  
packager.
    modules(
        "fr.umlv.lazystaticfinal@1.0/fr.umlv.lazystaticfinal.Main"
    )   
    
run(resolver, modulefixer, compiler, rewriter, packager, runner)

/exit errorCode()
