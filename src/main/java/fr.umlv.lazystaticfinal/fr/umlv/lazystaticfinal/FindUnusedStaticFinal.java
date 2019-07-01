package fr.umlv.lazystaticfinal;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toSet;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ASM7;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.PUTSTATIC;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

public class FindUnusedStaticFinal {
  private static Entry<String, String> splitByDot(String method) {
    var tokens = method.split("\\.");
    //System.err.println(method + " " + Arrays.toString(tokens));
    return Map.entry(tokens[0], tokens[1]);
  }
  
  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      System.err.println("error no boot methods file provided");
      return;
    }
    var path = Path.of(args[0]);
    var classToMethodsMap = Files.lines(path)
        .filter(line -> !line.startsWith("#"))
        .map(FindUnusedStaticFinal::splitByDot)
        .collect(groupingBy(Entry::getKey, mapping(Entry::getValue, toSet())));
    
    var putStaticSet = new HashSet<String>();
    var getStaticSet = new HashSet<String>();
    var staticFinalSet = new HashSet<String>();
    var privateStaticFinalSet = new HashSet<String>();
    
    classToMethodsMap.forEach((className, methods) -> {
      //System.err.println("try to load " + className);
      if (className.startsWith("java/lang/invoke/LambdaForm$") || className.contains("$Lambda$")) {
        //System.err.println("skip " + className);
        return;
      }
      
      byte[] byteArray;
      try(var inputStream = ClassLoader.getSystemResourceAsStream(className + ".class")) {
        byteArray = inputStream.readAllBytes();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      
      //FIXME current version of ASM doesn't support Java 14
      byteArray[7] = 57;
      
      var reader = new ClassReader(byteArray);
      reader.accept(new ClassVisitor(ASM7) {
        private String currentClassName;
        
        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
          currentClassName = name;
        }
        
        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
          if (value != null) {
            return null;  // those are primitive constants, skip them
          }
          if ((access & (ACC_STATIC|ACC_FINAL)) == (ACC_STATIC|ACC_FINAL)) {
            var field = currentClassName + '.' + name + ':' + descriptor;
            staticFinalSet.add(field);
            if ((access & ACC_PRIVATE) != 0) {
              privateStaticFinalSet.add(field);
            }
          }
          return null;
        }
        
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
          var method = name + ':' + descriptor;
          if (!methods.contains(method)) {
            return null;
          }
          
          return new MethodVisitor(ASM7) {
            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
              var set = (opcode == PUTSTATIC)? putStaticSet: (opcode == GETSTATIC)? getStaticSet: null;
              if (set != null) {
                set.add(owner + '.' + name + ':' + descriptor);
              }
            }
          };
        }
      }, 0);
    });
    
    System.out.println("staticFinalSet.size(): " + staticFinalSet.size());
    System.out.println("privateStaticFinalSet.size(): " + privateStaticFinalSet.size());
    System.out.println("putStaticSet.size(): " + putStaticSet.size());
    System.out.println("getStaticSet.size(): " + getStaticSet.size());
    
    var fieldMap = putStaticSet.stream()
        .filter(field -> !getStaticSet.contains(field) && staticFinalSet.contains(field))
        .map(FindUnusedStaticFinal::splitByDot)
        .collect(groupingBy(Entry::getKey, TreeMap::new, mapping(Entry::getValue, toCollection(TreeSet::new))));
    
    System.out.println("initialized static final fields that are never read " + fieldMap.values().stream().flatMap(TreeSet::stream).count());
    System.out.println("private initialized static final fields that are never read " + fieldMap.entrySet().stream()
        .flatMap(e -> e.getValue().stream().map(fieldName -> e.getKey() + "." + fieldName))
        .filter(privateStaticFinalSet::contains)
        .count());
    fieldMap.forEach((className, localFields) -> {
      System.out.println(className + " " + localFields);
    });
  }
}
