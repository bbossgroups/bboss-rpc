package bboss.org.jgroups.blocks;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
import java.util.Map.Entry;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import bboss.org.jgroups.Address;
import bboss.org.jgroups.Global;
import bboss.org.jgroups.logging.Log;
import bboss.org.jgroups.logging.LogFactory;
import bboss.org.jgroups.util.ThreadFactory;
import bboss.org.jgroups.util.Util;

public abstract class AbstractConnectionMap<V extends Connection> implements ConnectionMap<V> {
        
    protected final Vector<ConnectionMapListener<V>> conn_listeners=new Vector<ConnectionMapListener<V>>();
    protected final Map<Address,V> conns=new HashMap<Address,V>();
    protected final Lock lock = new ReentrantLock();
    protected final ThreadFactory factory; 
    protected final long reaperInterval;
    protected final Reaper reaper;
    protected final Log log=LogFactory.getLog(getClass());
    
    public AbstractConnectionMap(ThreadFactory factory) {
        this(factory,0);        
    }
    
    public AbstractConnectionMap(ThreadFactory factory,long reaperInterval) {
        super();
        this.factory=factory;        
        this.reaperInterval = reaperInterval;       
        if(reaperInterval > 0){
            reaper = new Reaper();
        }else{
            reaper = null;
        }
    }
    
    public Lock getLock(){
        return lock;
    }
    
    public boolean hasOpenConnection(Address address) {
        lock.lock();
        try {
            V v=conns.get(address);
            return v != null && v.isOpen();
        }
        finally {
            lock.unlock();
        }
    }
    
    public boolean hasConnection(Address address) {
        lock.lock();
        try {
            return conns.containsKey(address);
        }
        finally {
            lock.unlock();
        }
    }

    public void addConnection(Address address, V conn) {
        lock.lock();
        try {
            V previous = conns.put(address, conn);
            Util.close(previous);           
        }
        finally {
            lock.unlock();
        }
        notifyConnectionOpened(address, conn);
    }

    public void addConnectionMapListener(ConnectionMapListener<V> cml) {
        if(cml != null && !conn_listeners.contains(cml))
            conn_listeners.addElement(cml);
    } 
    
    public int getNumConnections() {
        lock.lock();
        try {
            return conns.size();
        }
        finally {
            lock.unlock();
        }
    }

    public String printConnections() {
        StringBuilder sb=new StringBuilder();

        lock.lock();
        try {
            for(Map.Entry<Address,V> entry: conns.entrySet()) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        finally {
            lock.unlock();
        }

        return sb.toString();
    }

    public ThreadFactory getThreadFactory() {
        return factory;
    }

    public void removeConnection(Address address) {
        Connection conn = null;
        lock.lock();
        try {
            conn=conns.remove(address);
        }
        finally {
            lock.unlock();
        }       
        Util.close(conn);
    }

    public void removeConnectionMapListener(ConnectionMapListener<V> cml) {
        if(cml != null)
            conn_listeners.removeElement(cml);
    }

    /**
     * Removes all connections from ConnectionTable which are not in
     * current_mbrs
     * 
     * @param current_mbrs
     */
    public void retainAll(Collection<Address> current_mbrs) {
        if(current_mbrs == null)
            return;

        Map<Address,V> copy=null;
        lock.lock();
        try {
            copy=new HashMap<Address,V>(conns);
            conns.keySet().retainAll(current_mbrs);
        }
        finally {
            lock.unlock();
        }   
        copy.keySet().removeAll(current_mbrs);
       
        for(Iterator<Entry<Address,V>> i = copy.entrySet().iterator();i.hasNext();) {
            Entry<Address,V> e = i.next();
            Util.close(e.getValue());      
        }
        copy.clear();
    }
    
    public void start() throws Exception {
        if(reaper != null) {                      
            reaper.start();
        }
    }

    public void stop() {
        if(reaper != null) {              
            reaper.stop();
        }
        lock.lock();
        try {
            for(Iterator<Entry<Address,V>> i = conns.entrySet().iterator();i.hasNext();) {
                Entry<Address,V> e = i.next();
                Util.close(e.getValue());          
            }
            clear();
        }
        finally {
            lock.unlock();
        }           
        conn_listeners.clear();
    }

    protected void clear() {
        lock.lock();
        try {
            conns.clear();
        }
        finally {
            lock.unlock();
        }
    }

    protected void notifyConnectionClosed(Address address) {
        for(ConnectionMapListener<V> l:conn_listeners) {
            l.connectionClosed(address);
        }
    }

    protected void notifyConnectionOpened(Address address, V conn) {
       
        for(ConnectionMapListener<V> l:conn_listeners) {
            l.connectionOpened(address, conn);
        }
    }
        
    
    class Reaper implements Runnable {

        private Thread thread;

        public synchronized void start() {
            if(thread == null || !thread.isAlive()) {
                thread=factory.newThread(new Reaper(), "Reaper");
                thread.start();
            }
        }

        public synchronized void stop() {
            if(thread != null && thread.isAlive()) {
                thread.interrupt();
                try {
                    thread.join(Global.THREAD_SHUTDOWN_WAIT_TIME);
                }
                catch(InterruptedException ignored) {
                }
            }
            thread=null;
        }

        public void run() {
            while(!Thread.currentThread().isInterrupted()) {
                lock.lock();
                try {
                    for(Iterator<Entry<Address,V>> it=conns.entrySet().iterator();it.hasNext();) {
                        Entry<Address,V> entry=it.next();
                        V c=entry.getValue();
                        if(c.isExpired(System.currentTimeMillis())) {
                            log.info("Connection " + c.toString() + " reaped");
                            Util.close(c);                                            
                            it.remove();                           
                        }
                    }
                }
                finally {
                    lock.unlock();
                }
                Util.sleep(reaperInterval);
            }           
        }
    }

    public interface ConnectionMapListener<V> {

        public void connectionClosed(Address address);

        public void connectionOpened(Address address, V conn);

    }
}
