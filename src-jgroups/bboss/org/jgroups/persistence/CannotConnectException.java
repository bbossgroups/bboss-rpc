package  bboss.org.jgroups.persistence;

/**
 * @author Mandar Shinde
 * This exception inherits the Exception class and is used in
 * cases where the Persistence Storage cannot be connected to
 * for any purpose.
 */


public class CannotConnectException extends Exception
{

    private static final long serialVersionUID = 7472528586067210747L;
	
    /**
     * @param t
     * @param reason implementor-specified runtime reason
     */
    public CannotConnectException(Exception t, String reason)
    {
	this.t = t;
	this.reason = reason;
    }

    /**
     * @param t
      * @param reason implementor-specified runtime reason
     */
    public CannotConnectException(Throwable t, String reason)
    {	
	this.t = t;
	this.reason = reason;
    }		

    /**
     * @return String;
     */
    public String toString()
    {
	String tmp = "Exception " + t.toString() + " was thrown due to " + reason;
	return tmp;
    }

    /**
     * members are made available so that the top level user can dump
     * appropriate members on to his stack trace
     */
    public Throwable t = null;
    public String reason = null;
}
