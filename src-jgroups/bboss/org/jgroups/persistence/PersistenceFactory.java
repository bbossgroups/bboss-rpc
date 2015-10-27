package bboss.org.jgroups.persistence;

/**
 * @author Mandar Shinde
 * This class is the factory to get access to any DB based or file based
 * implementation. None of the implementations should expose directly
 * to user for migration purposes
 */


import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Properties;

import bboss.org.jgroups.annotations.Unsupported;
import bboss.org.jgroups.logging.Log;
import bboss.org.jgroups.logging.LogFactory;
import bboss.org.jgroups.util.Util;


@Unsupported
public class PersistenceFactory
{

    protected final static Log log=LogFactory.getLog(PersistenceFactory.class);

    /**
     * Default private constructor// does nothing
     */
    private PersistenceFactory()
    {
    }


    /**
     * Singular public method to get access to any of the Persistence
     * Manager implementations. It is important to known at this point
     * that properties determine the implementation of the Persistence
     * Manager, there is no direct interface which gives access to 
     * either DB implemented ot FILE implemented storage API.
     * @return PersistenceFactory;
     */
    public static PersistenceFactory getInstance() {
        log.debug(" getting factory instance ");
        if (_factory == null)
            _factory = new PersistenceFactory();
        return _factory;
    }

    /**
     * Register a custom persistence manager as opposed to the
     * {@link FilePersistenceManager} or {@link DBPersistenceManager}.
     */ 
    public synchronized void registerManager(PersistenceManager manager)
    {
        _manager = manager;
    }

    /**
     * Reads the default properties and creates a persistencemanager
     * The default properties are picked up from the $USER_HOME or 
     * from the classpath. Default properties are represented by
     * "persist.properties"
     * @return PersistenceManager
     * @exception Exception;
     */ 
    public synchronized PersistenceManager createManager() throws Exception {
        // will return null if not initialized
        // uses default properties
        if (_manager == null)
        {
            if (checkDB())
                _manager = createManagerDB(propPath);
            else
                _manager = createManagerFile(propPath);
        }
        return _manager;
    }


    /**
     * Duplicated signature to create PersistenceManager to allow user to
     * provide property path. 
     * @param filePath complete pathname to get the properties
     * @return PersistenceManager;
     * @exception Exception;
     */
    public synchronized PersistenceManager createManager (String filePath) throws Exception 
    {
        if (_manager == null)
        {
            if (checkDB(filePath))
                _manager = createManagerDB(filePath);
            else
                _manager = createManagerFile(filePath);
        }
        return _manager;
    }



    /**
     * Internal creator of DB persistence manager, the outside user accesses only
     * the PersistenceManager interface API
     */
    private PersistenceManager createManagerDB(String filePath) throws Exception
    {

            if(log.isInfoEnabled()) log.info("Calling db persist from factory: " + filePath);
        if (_manager == null)
            _manager = new DBPersistenceManager(filePath);
        return _manager;
    }// end of DB persistence

    /**
     * creates instance of file based persistency manager
     * @return PersistenceManager
     */
    private PersistenceManager createManagerFile(String filePath)
    {

            if(log.isInfoEnabled()) log.info("Creating file manager: " + filePath);
        Properties props;

        try
        {
            if (_manager == null)
            {
                props=readProps(filePath);
                String classname=props.getProperty(filePersistMgr);
                if(classname != null)
                {
                    Class cl=Util.loadClass(classname, this.getClass());
                    Constructor ctor=cl.getConstructor(new Class[]{String.class});
                    _manager=(PersistenceManager)ctor.newInstance(new Object[]{filePath});
                }
                else
                {
                    _manager = new FilePersistenceManager(filePath);
                }
            }
            return _manager;
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            return null;
        }
    }// end of file persistence
    

    /**
     * checks the default properties for DB/File flag
     * @return boolean;
     * @exception Exception;
     */
    private boolean checkDB() throws Exception
    {
        Properties props=readProps(propPath);
        String persist = props.getProperty(persistProp);
        if ("DB".equals(persist))
            return true;
        return false;
    }




    /**
     * checks the provided properties for DB/File flag
     * @return boolean;
     */
    private boolean checkDB(String filePath) throws Exception
    {
        Properties props=readProps(filePath);
        String persist = props.getProperty(persistProp);
        if ("DB".equals(persist))
            return true;
        return false;
    }


    Properties readProps(String fileName) throws IOException
    {
        Properties props;
        FileInputStream _stream = new FileInputStream(fileName);
        props=new Properties();
        props.load(_stream);
        return props;
    }

    private static volatile PersistenceManager _manager = null;
    private static volatile PersistenceFactory _factory = null;
   

    /* Please set this according to configuration */
    final static String propPath = "persist.properties";
    final static String persistProp = "persist";

    /** The class that implements a file-based PersistenceManager */
    final static String filePersistMgr="filePersistMgr";
}
