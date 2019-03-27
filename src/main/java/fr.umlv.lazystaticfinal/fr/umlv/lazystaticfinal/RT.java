package fr.umlv.lazystaticfinal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;

public class RT {
  public static Object bsm(Lookup lookup, String name, Class<?> type, MethodHandle init) throws Throwable {
    // check that the method handle was created by the lookup for security purpose
    //lookup.revealDirect(init);
    
    //System.out.println("call bsm init " + name + " from " + lookup.lookupClass());
    return init.invoke();
  }
}
