import static com.github.forax.pro.Pro.*;
import static com.github.forax.pro.builder.Builders.*;

runner.
  enablePreview(true).
  modulePath(location("target/main/artifact"), location("deps")).
  module("fr.umlv.lazystaticfinal/fr.umlv.lazystaticfinal.Hello").
  rawArguments("-XX:+UnlockDiagnosticVMOptions", "-XX:+LogTouchedMethods", "-XX:+PrintTouchedMethodsAtExit")
  
run(runner)

/exit errorCode()
