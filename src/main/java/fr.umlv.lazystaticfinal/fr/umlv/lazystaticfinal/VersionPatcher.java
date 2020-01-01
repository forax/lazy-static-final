package fr.umlv.lazystaticfinal;

import org.objectweb.asm.Opcodes;

//ASM currently doesn't support V15 yet
class VersionPatcher {
  public static int patch(byte[] bytecode) {
    int version = bytecode[7];
    bytecode[7] = Opcodes.V14;
    return version;
  }
  
  public static void unpatch(byte[] bytecode, int version) {
    bytecode[7] = (byte)version;
  }
}
