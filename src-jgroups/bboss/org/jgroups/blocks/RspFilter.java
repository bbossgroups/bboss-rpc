package bboss.org.jgroups.blocks;

import bboss.org.jgroups.Address;

/**
 * Interface defining when a group request is done. This allows for termination of a group request based on
 * logic implemented by the caller. Example: caller uses mode GET_FIRST plus a RspFilter implementation. Here, the
 * request will not return (assuming timeout is 0) when the first response has been received, but when the filter
 * passed
 * @author Bela Ban
 * @version $Id: RspFilter.java,v 1.2 2008/11/25 10:00:35 belaban Exp $
 */
public interface RspFilter {


    /**
     * Determines whether a response from a given sender should be added to the response list of the request
     * @param response The response (usually a serializable value)
     * @param sender The sender of response
     * @return True if we should add the response to the response list ({@link bboss.org.jgroups.util.RspList}) of a request,
     * otherwise false. In the latter case, we don't add the response to the response list.
     */
    boolean isAcceptable(Object response, Address sender);

    /**
     * Right after calling {@link #isAcceptable(Object, bboss.org.jgroups.Address)}, this method is called to see whether
     * we are done with the request and can unblock the caller
     * @return False if the request is done, otherwise true
     */
    boolean needMoreResponses();
}
