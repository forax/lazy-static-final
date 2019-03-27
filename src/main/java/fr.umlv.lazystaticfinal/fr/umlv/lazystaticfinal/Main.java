package fr.umlv.lazystaticfinal;

import java.nio.file.Path;

public class Main {
  private static final Path HOME_lazy;
  static {
    HOME_lazy = init_HOME_lazy();
  }
  
  private static Path init_HOME_lazy() {
    System.out.println("init HOME_lazy");
    return Path.of(System.getenv("HOME"));
  }
  
  static class Internal {
    static void test() {
      System.out.println(HOME_lazy);
    }
  }
  
  public static void main(String[] args) {
    System.out.println("main started");
    Internal.test();
    System.out.println(HOME_lazy);
  }
}
