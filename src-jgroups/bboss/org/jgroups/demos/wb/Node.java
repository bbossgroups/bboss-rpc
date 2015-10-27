// $Id: Node.java,v 1.3 2008/01/22 10:44:37 belaban Exp $

package bboss.org.jgroups.demos.wb;
import bboss.org.jgroups.Address;



public class Node implements java.io.Serializable {
    public double    x, y, dx, dy;
    public boolean   fixed;
    public String    lbl=null;
    public Address   addr=null;
    public int       xloc=0, yloc=0;
    public int       width=0;
    public int       height=0;

    
    public String toString() {
	StringBuilder ret=new StringBuilder();
	ret.append("name=" + lbl + ", addr=" + addr + " at " + x + ',' + y);
	return ret.toString();
    }
}

