package fr.umlv.lazystaticfinal;

import java.nio.file.Path;

public class Main {
  private static final Path HOME_lazy;
  static {
    HOME_lazy = findHOME();
  }
  
  private static Path findHOME() {
    System.out.println("find HOME");
    StackWalker.getInstance().forEach(frame -> System.out.println("  from " + frame));  // print stack trace
    return Path.of(System.getenv("HOME"));
  }
  
  static class Internal {
    static void test() {
      System.out.println("Internal.test HOME_lazy: " + HOME_lazy);
    }
  }
  
  public static void main(String[] args) {
    System.out.println("main started");
    System.out.println("main HOME_lazy: " + HOME_lazy);
    Internal.test();
  }
}
