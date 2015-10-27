
package bboss.org.jgroups.conf;


import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import bboss.org.jgroups.ChannelException;
import bboss.org.jgroups.Global;
import bboss.org.jgroups.logging.Log;
import bboss.org.jgroups.logging.LogFactory;
import bboss.org.jgroups.util.Tuple;
import bboss.org.jgroups.util.Util;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This class will be replaced with the class that read info
 * from the magic number configurator that reads info from the xml file.
 * The name and the relative path of the magic number map file can be specified
 * as value of the property <code>bboss.org.jgroups.conf.magicNumberFile</code>.
 * It must be relative to one of the classpath elements, to allow the
 * classloader to locate the file. If a value is not specified,
 * <code>MagicNumberReader.MAGIC_NUMBER_FILE</code> is used, which defaults
 * to "bboss-magic-map.xml".
 *
 * @author Filip Hanik
 * @author Bela Ban
 */
public class ClassConfigurator {
    public static final String MAGIC_NUMBER_FILE = "bboss-magic-map.xml";
    public static final String PROTOCOL_ID_FILE  = "bboss-protocol-ids.xml";
    private static final short MIN_CUSTOM_MAGIC_NUMBER=1024;
    private static final short MIN_CUSTOM_PROTOCOL_ID=512;

    // this is where we store magic numbers; contains data from bboss-magic-map.xml
    private static final Map<Class,Short> classMap=new ConcurrentHashMap<Class,Short>(); // key=Class, value=magic number
    private static final Map<Short,Class> magicMap=new ConcurrentHashMap<Short,Class>(); // key=magic number, value=Class

    /** Contains data read from bboss-protocol-ids.xml */
    private static final Map<Class,Short> protocol_ids=new ConcurrentHashMap<Class,Short>();
    private static final Map<Short,Class> protocol_names=new ConcurrentHashMap<Short,Class>();

    protected static final Log log=LogFactory.getLog(ClassConfigurator.class);


    static {
        try {
            init();
        }
        catch(Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public ClassConfigurator() {
    }

    protected static void init() throws ChannelException {
        try {
            // make sure we have a class for DocumentBuilderFactory
            Util.loadClass("javax.xml.parsers.DocumentBuilderFactory", ClassConfigurator.class);

            String magic_number_file=null, protocol_id_file=null;
            try { // PropertyPermission not granted if running in an untrusted environment with JNLP
                magic_number_file=Util.getProperty(new String[]{Global.MAGIC_NUMBER_FILE, "bboss.org.jgroups.conf.magicNumberFile"},
                                               null, null, false, MAGIC_NUMBER_FILE);
                protocol_id_file=Util.getProperty(new String[]{Global.PROTOCOL_ID_FILE, "bboss.org.jgroups.conf.protocolIDFile"},
                                                  null, null, false, PROTOCOL_ID_FILE);
                if(log.isDebugEnabled()) log.debug("Using " + magic_number_file + " as magic number file and " +
                        protocol_id_file + " for protocol IDs");
            }
            catch (SecurityException ex){
            }

            // Read bboss-magic-map.xml
            List<Tuple<Short,String>> mapping=readMappings(magic_number_file);
            for(Tuple<Short,String> tuple: mapping) {
                short m=tuple.getVal1();
                try {
                    Class clazz=Util.loadClass(tuple.getVal2(), ClassConfigurator.class);
                    if(magicMap.containsKey(m))
                        throw new ChannelException("key " + m + " (" + clazz.getName() + ')' +
                                " is already in magic map; please make sure that all keys are unique");
                    
                    magicMap.put(m, clazz);
                    classMap.put(clazz, m);
                }
                catch(ClassNotFoundException cnf) {
                    throw new ChannelException("failed loading class", cnf);
                }
            }

            // Read bboss-protocol-ids.xml
            mapping=readMappings(protocol_id_file);
            for(Tuple<Short,String> tuple: mapping) {
                short m=tuple.getVal1();
                try {
                    Class clazz=Util.loadClass(tuple.getVal2(), ClassConfigurator.class);
                    if(protocol_ids.containsKey(clazz))
                        throw new ChannelException("ID " + m + " (" + clazz.getName() + ')' +
                                " is already in protocol-ID map; please make sure that all protocol IDs are unique");
                    protocol_ids.put(clazz, m);
                    protocol_names.put(m, clazz);
                }
                catch(ClassNotFoundException cnf) {
                    throw new ChannelException("failed loading class", cnf);
                }
            }
        }
        catch(ChannelException ex) {
            throw ex;
        }
        catch(Throwable x) {
            throw new ChannelException("failed reading the magic number mapping file", x);
        }
    }



    /**
     * Method to register a user-defined header with jg-magic-map at runtime
     * @param magic The magic number. Needs to be > 1024
     * @param clazz The class. Usually a subclass of Header
     * @throws IllegalArgumentException If the magic number is already taken, or the magic number is <= 1024
     */
     public static void add(short magic, Class clazz) throws IllegalArgumentException {
        if(magic <= MIN_CUSTOM_MAGIC_NUMBER)
            throw new IllegalArgumentException("magic number (" + magic + ") needs to be greater than " +
                    MIN_CUSTOM_MAGIC_NUMBER);
        if(magicMap.containsKey(magic) || classMap.containsKey(clazz))
            throw new IllegalArgumentException("magic number " + magic + " for class " + clazz.getName() +
                    " is already present");
        magicMap.put(magic, clazz);
        classMap.put(clazz, magic);
    }


    public static void addProtocol(short id, Class protocol) {
        if(id <= MIN_CUSTOM_PROTOCOL_ID)
            throw new IllegalArgumentException("protocol ID (" + id + ") needs to be greater than " + MIN_CUSTOM_PROTOCOL_ID);
        if(protocol_ids.containsKey(protocol))
            throw new IllegalArgumentException("Protocol " + protocol + " is already present");
        protocol_ids.put(protocol, id);
    }


    /**
     * Returns a class for a magic number.
     * Returns null if no class is found
     *
     * @param magic the magic number that maps to the class
     * @return a Class object that represents a class that implements java.io.Externalizable
     */
    public static Class get(short magic) {
        return magicMap.get(magic);
    }

    /**
     * Loads and returns the class from the class name
     *
     * @param clazzname a fully classified class name to be loaded
     * @return a Class object that represents a class that implements java.io.Externalizable
     */
    public static Class get(String clazzname) {
        try {
            // return ClassConfigurator.class.getClassLoader().loadClass(clazzname);
            return Util.loadClass(clazzname, ClassConfigurator.class);
        }
        catch(Exception x) {
            if(log.isErrorEnabled()) log.error("failed loading class " + clazzname, x);
        }
        return null;
    }

    /**
     * Returns the magic number for the class.
     *
     * @param clazz a class object that we want the magic number for
     * @return the magic number for a class, -1 if no mapping is available
     */
    public static short getMagicNumber(Class clazz) {
        Short i=classMap.get(clazz);
        if(i == null)
            return -1;
        else
            return i;
    }


    public static short getProtocolId(Class protocol) {
        Short retval=protocol_ids.get(protocol);
        if(retval != null)
            return retval;
        return 0;
    }


    public static Class getProtocol(short id) {
        return protocol_names.get(id);
    }


    public String toString() {
        return printMagicMap();
    }

    public static String printMagicMap() {
        StringBuilder sb=new StringBuilder();
        SortedSet<Short> keys=new TreeSet<Short>(magicMap.keySet());

        for(Short key: keys) {
            sb.append(key).append(":\t").append(magicMap.get(key)).append('\n');
        }
        return sb.toString();
    }

    public static String printClassMap() {
        StringBuilder sb=new StringBuilder();
        Map.Entry entry;

        for(Iterator it=classMap.entrySet().iterator(); it.hasNext();) {
            entry=(Map.Entry)it.next();
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }
        return sb.toString();
    }


    /**
     * try to read the magic number configuration file as a Resource form the classpath using getResourceAsStream
     * if this fails this method tries to read the configuration file from mMagicNumberFile using a FileInputStream (not in classpath but somewhere else in the disk)
     *
     * @return an array of ClassMap objects that where parsed from the file (if found) or an empty array if file not found or had en exception
     */
    protected static List<Tuple<Short,String>> readMappings(String name) throws Exception {
        InputStream stream;
        try {
            stream=Util.getResourceAsStream(name, ClassConfigurator.class);
            // try to load the map from file even if it is not a Resource in the class path
            if(stream == null) {
                if(log.isTraceEnabled())
                    log.trace("Could not read " + name + " from the CLASSPATH, will try to read it from file");
                stream=new FileInputStream(name);
            }
        }
        catch(Exception x) {
            throw new ChannelException(name + " not found. Please make sure it is on the classpath", x);
        }
        return parse(stream);
    }

    protected static List<Tuple<Short,String>> parse(InputStream stream) throws Exception {
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
        factory.setValidating(false); // for now
        DocumentBuilder builder=factory.newDocumentBuilder();
        Document document=builder.parse(stream);
        NodeList class_list=document.getElementsByTagName("class");
        List<Tuple<Short,String>> list=new LinkedList<Tuple<Short,String>>();
        for(int i=0; i < class_list.getLength(); i++) {
            if(class_list.item(i).getNodeType() == Node.ELEMENT_NODE) {
                list.add(parseClassData(class_list.item(i)));
            }
        }
        return list;
    }

    protected static Tuple<Short,String> parseClassData(Node protocol) throws java.io.IOException {
        try {
            protocol.normalize();
            NamedNodeMap attrs=protocol.getAttributes();
            String clazzname;
            String magicnumber;

            magicnumber=attrs.getNamedItem("id").getNodeValue();
            clazzname=attrs.getNamedItem("name").getNodeValue();
            return new Tuple<Short,String>(Short.valueOf(magicnumber), clazzname);
        }
        catch(Exception x) {
            IOException tmp=new IOException();
            tmp.initCause(x);
            throw tmp;
        }
    }


}
