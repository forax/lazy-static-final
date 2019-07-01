package fr.umlv.lazystaticfinal;

import static java.nio.file.Files.readAllBytes;
import static java.nio.file.Files.walk;
import static java.nio.file.Files.write;
import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ASM7;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.PUTSTATIC;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.ConstantBootstraps;
import java.nio.file.Path;
import java.util.HashMap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.util.CheckClassAdapter;

public class Rewriter {
  private static final String CONSTANT_BOOTSTRAPS_CLASS = ConstantBootstraps.class.getName().replace('.', '/');
  private static final Handle BSM = new Handle(H_INVOKESTATIC, CONSTANT_BOOTSTRAPS_CLASS, "invoke",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;[Ljava/lang/Object;)Ljava/lang/Object;", false);
  
  static class LazyField {
    private final FieldNode field;
    
    private final MethodInsnNode initCall;
    
    public LazyField(FieldNode field, MethodInsnNode initCall) {
      this.field = field;
      this.initCall = initCall;
    }
    
    public LazyField withInitCall(MethodInsnNode initCall) {
      return new LazyField(field, initCall);
    }
    
    public void generateAccessor(ClassVisitor cv) {
      var accessor = cv.visitMethod(field.access, field.name, "()" + field.desc, /*FIXME*/null, null);
      accessor.visitCode();
      generateLdc(accessor);
      accessor.visitInsn(Type.getType(field.desc).getOpcode(Opcodes.IRETURN));
      accessor.visitMaxs(-1, -1);
      accessor.visitEnd();
    }
    
    public void generateLdc(MethodVisitor mv) {
      var initHandle = new Handle(H_INVOKESTATIC, initCall.owner, initCall.name, initCall.desc, initCall.itf);
      mv.visitLdcInsn(new ConstantDynamic(field.name, field.desc, BSM, initHandle));
    }
  }
  
  static abstract class ClassInitMethodVisitor extends MethodVisitor {
    MethodInsnNode delayedInitCall;
    
    public ClassInitMethodVisitor(int api, MethodVisitor mv) {
      super(api, mv);
    }
    
    void generateDelayedMethod() {
      var node = delayedInitCall;
      if (node != null) {
        mv.visitMethodInsn(node.getOpcode(), node.owner, node.name, node.desc, node.itf);
        delayedInitCall = null;
      }
    }
    
    @Override
    public void visitIincInsn(int var, int increment) {
      generateDelayedMethod();
      super.visitIincInsn(var, increment);
    }
    @Override
    public void visitInsn(int opcode) {
      generateDelayedMethod();
      super.visitInsn(opcode);
    }
    @Override
    public void visitIntInsn(int opcode, int operand) {
      generateDelayedMethod();
      super.visitIntInsn(opcode, operand);
    }
    @Override
    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
      generateDelayedMethod();
      super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
    }
    @Override
    public void visitJumpInsn(int opcode, Label label) {
      generateDelayedMethod();
      super.visitJumpInsn(opcode, label);
    }
    @Override
    public void visitLdcInsn(Object value) {
      generateDelayedMethod();
      super.visitLdcInsn(value);
    }
    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
      generateDelayedMethod();
      super.visitLookupSwitchInsn(dflt, keys, labels);
    }
    @Override
    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
      generateDelayedMethod();
      super.visitMultiANewArrayInsn(descriptor, numDimensions);
    }
    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
      generateDelayedMethod();
      super.visitTableSwitchInsn(min, max, dflt, labels);
    }
    @Override
    public void visitTypeInsn(int opcode, String type) {
      generateDelayedMethod();
      super.visitTypeInsn(opcode, type);
    }
    @Override
    public void visitVarInsn(int opcode, int var) {
      generateDelayedMethod();
      super.visitVarInsn(opcode, var);
    }
    
    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
      generateDelayedMethod();
      if (opcode == INVOKESTATIC && descriptor.startsWith("()")) {
        delayedInitCall = new MethodInsnNode(opcode, owner, name, descriptor, isInterface);
        return;
      }
      super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }
  }
  
  private static byte[] rewrite(byte[] code) {
    var reader = new ClassReader(code);
    var currentClassName = reader.getClassName();
    
    // first find all lazy static fields and their initializers
    var fieldMap = new HashMap<String, LazyField>();
    reader.accept(new ClassVisitor(ASM7) {
      @Override
      public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        if (name.endsWith("_lazy")) {
          if ((access & ACC_STATIC) == 0) {
            throw new IllegalStateException("field " + currentClassName + "." + name + " should be declared static");
          }
          if (value != null) {
            throw new IllegalStateException("field " + currentClassName + "." + name + " should not contains a primitive or a String constant");
          }
          var fieldNode = new FieldNode(access, name, descriptor, signature, value);
          fieldMap.put(name + '.' + descriptor, new LazyField(fieldNode, null));
        }
        return null;
      }
      
      @Override
      public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (name.equals("<clinit>")) {
          return new ClassInitMethodVisitor(ASM7, null) {
            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
              if (opcode == PUTSTATIC && owner.equals(currentClassName) && name.endsWith("_lazy")) {
                if (delayedInitCall == null) {
                  throw new IllegalStateException("bad code shape, " + currentClassName + "." + name + " is not initialized with a no arg static method");
                }
                fieldMap.computeIfPresent(name + '.' + descriptor, (__, field) -> field.withInitCall(delayedInitCall));
              }
              delayedInitCall = null;
              return;
            }
          };
        }
        return null;
      }
    }, ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
    
    
    // then rewrite the bytecode
    var writer = new ClassWriter(reader, COMPUTE_MAXS|COMPUTE_FRAMES);
    reader.accept(new ClassVisitor(ASM7, writer) {
      @Override
      public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        // remove lazy static field
        if (fieldMap.containsKey(name + '.' + descriptor)) {
          return null;
        }
        return super.visitField(access, name, descriptor, signature, value);
      }
      
      @Override
      public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        var mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (name.equals("<clinit>")) {
          mv = new ClassInitMethodVisitor(ASM7, mv) {
            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
              if (opcode == PUTSTATIC && owner.equals(currentClassName) && name.endsWith("_lazy")) {
                // don't generate anything and reset delayedInitCall
                delayedInitCall = null;
                return;
              }
              generateDelayedMethod();
              super.visitFieldInsn(opcode, owner, name, descriptor);
            }   
          };
        }
        return new MethodVisitor(ASM7, mv) {
          @Override
          public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            //System.err.println("visitFieldInsn" + owner + '.' + name + descriptor + " in " + currentClassName);
            if (opcode == GETSTATIC && name.endsWith("_lazy")) {
              if (currentClassName.equals(owner)) {
                var field = fieldMap.get(name + '.' + descriptor);
                field.generateLdc(mv);
              } else {
                mv.visitMethodInsn(INVOKESTATIC, owner, name, "()" + descriptor, /*FIXME*/false);
              }
            } else {
              super.visitFieldInsn(opcode, owner, name, descriptor);
            }
          }
        };
      }
      
      @Override
      public void visitEnd() {
        for(var field: fieldMap.values()) {
          field.generateAccessor(cv);
        }
        super.visitEnd();
      }
    }, 0);
    
    var newCode = writer.toByteArray();
    CheckClassAdapter.verify(new ClassReader(newCode), false, new PrintWriter(System.err));
    
    return newCode;
  }
  
  public static void main(String[] args) throws IOException {
    var directory = Path.of(args[0]);
    System.out.println("rewrite directory " + directory);
    for(var path: (Iterable<Path>)walk(directory)
                       .filter(path -> path.toString().endsWith(".class"))
                       ::iterator) {
      System.out.println("rewrite file " + path);
      var code = readAllBytes(path);
      
      //FIXME Hack to support Java 14 until there is a release of ASM that support the JDK 14
      code[7] = 57;
      
      write(path, rewrite(code));
    }
  }
}
