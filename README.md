# lazy-static-final
study the introduction of lazy static final field in Java

# get the build tool
Using Java 12, run
```
  export PRO_SPECIAL_BUILD='12-early-access'
  java pro_wrapper.java
```

# build
```
  pro/bin/pro
```

# find all methods used to run helloworld
```
  pro/bin/pro build find-touched-methods.pro
```

# find usused static final
```
  pro/bin/java --enable-preview \
               --module-path target/main/artifact:deps \
               --module fr.umlv.lazystaticfinal/fr.umlv.lazystaticfinal.FindUnusedStaticFinal
```

