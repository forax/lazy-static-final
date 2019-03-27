import static com.github.forax.pro.Pro.*;
import static com.github.forax.pro.builder.Builders.*;

compiler.
  moduleTestPath(List.of()).  // don't compile  test
  files(location("src/main/java/fr.umlv.lazystaticfinal/fr/umlv/lazystaticfinal/Hello.java"))

runner.
  modulePath(location("target/main/artifact"), location("deps")).
  module("fr.umlv.lazystaticfinal/fr.umlv.lazystaticfinal.Hello").
  rawArguments("-XX:+UnlockDiagnosticVMOptions", "-XX:+LogTouchedMethods", "-XX:+PrintTouchedMethodsAtExit")
  
run(compiler, packager, runner)

/exit errorCode()
