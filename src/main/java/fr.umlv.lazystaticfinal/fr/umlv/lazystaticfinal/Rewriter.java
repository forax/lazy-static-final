package fr.umlv.lazystaticfinal;

import static java.nio.file.Files.readAllBytes;
import static java.nio.file.Files.walk;
import static java.nio.file.Files.write;
import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ASM7;
import static org.objectweb.asm.Opcodes.DCONST_0;
import static org.objectweb.asm.Opcodes.FCONST_0;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.LCONST_0;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.POP2;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Type.BOOLEAN;
import static org.objectweb.asm.Type.BYTE;
import static org.objectweb.asm.Type.CHAR;
import static org.objectweb.asm.Type.DOUBLE;
import static org.objectweb.asm.Type.FLOAT;
import static org.objectweb.asm.Type.INT;
import static org.objectweb.asm.Type.LONG;
import static org.objectweb.asm.Type.SHORT;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.CheckClassAdapter;

@SuppressWarnings("preview")  // disable preview feature warning
public class Rewriter {
  private static final String RT_CLASS = RT.class.getName().replace('.', '/');
  private static final Handle BSM = new Handle(H_INVOKESTATIC, RT_CLASS, "bsm",
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;", false);
  
  private static byte[] rewrite(byte[] code) {
    var reader = new ClassReader(code);
    var writer = new ClassWriter(reader, COMPUTE_MAXS|COMPUTE_FRAMES);
    reader.accept(new ClassVisitor(ASM7, writer) {
      private String currentClassName;
      
      @Override
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        currentClassName = name;
        super.visit(version, access, name, signature, superName, interfaces);
      }
      
      @Override
      public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        if (name.endsWith("_lazy")) {
          if ((access & ACC_STATIC) == 0) {
            throw new IllegalStateException("field " + currentClassName + "." + name + " should be declared static");
          }
          
          var accessor = writer.visitMethod(access, name, "()" + descriptor, null, null);
          accessor.visitCode();
          var initHandle = new Handle(H_INVOKESTATIC, currentClassName, "init_" + name, "()" + descriptor, false);
          accessor.visitLdcInsn(new ConstantDynamic(name, descriptor, BSM, initHandle));
          accessor.visitInsn(Type.getType(descriptor).getOpcode(Opcodes.IRETURN));
          //accessor.visitInsn(RETURN);
          accessor.visitMaxs(-1, -1);
          accessor.visitEnd();
          return null;
        }
        return super.visitField(access, name, descriptor, signature, value);
      }
      
      @Override
      public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        var mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (name.equals("<clinit>")) {
          mv = new MethodVisitor(ASM7, mv) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
              if (opcode == INVOKESTATIC && name.endsWith("_lazy") && name.startsWith("init_")) {
                if (!descriptor.startsWith("()")) {
                  throw new IllegalStateException("init method for " + name + " should not take parameter in static block of " + currentClassName);
                }
                
                var type = Type.getType(descriptor.substring(2));
                mv.visitInsn(switch(type.getSort()) {
                  case BOOLEAN, BYTE, SHORT, CHAR, INT -> ICONST_0;
                  case LONG -> LCONST_0;
                  case FLOAT -> FCONST_0;
                  case DOUBLE -> DCONST_0;
                  default -> ACONST_NULL;
                });
                return;
              }
              super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
              if (opcode == PUTSTATIC && owner.equals(currentClassName) && name.endsWith("_lazy")) {
                mv.visitInsn(descriptor.equals("D") || descriptor.equals("J")? POP2: POP);
                return;
              }
              super.visitFieldInsn(opcode, owner, name, descriptor);
            }   
          };
        }
        return new MethodVisitor(ASM7, mv) {
          @Override
          public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            if (opcode == GETSTATIC && name.endsWith("_lazy")) {
              if (currentClassName.equals(owner)) {
                var initHandle = new Handle(H_INVOKESTATIC, currentClassName, "init_" + name, "()" + descriptor, false);
                mv.visitLdcInsn(new ConstantDynamic(name, descriptor, BSM, initHandle));
              } else {
                mv.visitMethodInsn(INVOKESTATIC, owner, name, "()" + descriptor, false);
              }
            } else {
              super.visitFieldInsn(opcode, owner, name, descriptor);
            }
          }
        };
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
      write(path, rewrite(code));
    }
  }
}
