package bboss.org.jgroups.blocks;

import java.lang.reflect.Method;

/**
 * @author Bela Ban
 * @version $Id: MethodLookup.java,v 1.3 2005/07/22 08:59:20 belaban Exp $
 */
public interface MethodLookup {
    Method findMethod(short id);
}
