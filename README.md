# lazy-static-final
study the introduction of lazy static final field in Java

# get the build tool
Using Java 11+, run
```
  export PRO_SPECIAL_BUILD='12-early-access'
  java pro_wrapper.java
```

# build
```
  pro/bin/pro
```

# ask the VM to log all methods used to run HelloWorld
```
  ./pro/bin/java --enable-preview \
                 --module-path target/main/artifact:deps \
                 --module fr.umlv.lazystaticfinal/fr.umlv.lazystaticfinal.Hello \
                 -XX:+UnlockDiagnosticVMOptions -XX:+LogTouchedMethods -XX:+PrintTouchedMethodsAtExit > boot-methods.txt
```

# find unused static final (when running HelloWorld)
```
  ./pro/bin/java --enable-preview \
                 --module-path target/main/artifact:deps \
                 --module fr.umlv.lazystaticfinal/fr.umlv.lazystaticfinal.FindUnusedStaticFinal boot-methods.txt
```

