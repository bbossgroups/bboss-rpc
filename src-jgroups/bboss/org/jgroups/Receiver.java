package bboss.org.jgroups;

/**
 * Defines the callbacks that are invoked when messages, views etc are received on a channel
 * @author Bela Ban
 * @version $Id: Receiver.java,v 1.1 2005/11/08 10:40:16 belaban Exp $
 */
public interface Receiver extends MessageListener, MembershipListener {
}
