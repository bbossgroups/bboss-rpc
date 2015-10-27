package bboss.org.jgroups.util;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import bboss.org.jgroups.annotations.Experimental;
import bboss.org.jgroups.annotations.Property;
import bboss.org.jgroups.annotations.Unsupported;
import bboss.org.jgroups.logging.Log;
import bboss.org.jgroups.logging.LogFactory;
import bboss.org.jgroups.stack.Protocol;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Iterates over all concrete Protocol classes and creates tables with Protocol's properties.
 * These tables are in turn then merged into docbook. 
 * 
 * Iterates over unsupported and experimental classes and creates tables listing those classes.
 * These tables are in turn then merged into docbook.  
 * 
 * @author Vladimir Blagojevic
 * @version $Id: PropertiesToXML.java,v 1.11 2010/04/30 14:27:38 vlada Exp $
 * 
 */
public class PropertiesToXML {

    protected static final Log log = LogFactory.getLog(PropertiesToXML.class);

    public static void main(String[] args) {
        String input = "doc/manual/en/modules/protocols.xml";
        String input2 = "doc/manual/en/modules/installation.xml";     

        if (args.length != 1) {
            help();
            return;
        }
        input = args[0];
        String temp_file = input + ".tmp";       
        String temp_file2 = input2 + ".tmp";

        try {
            // first copy protocols.xml file into protocols-temp.xml
            File f = new File(temp_file);
            copy(new FileReader(new File(input)), new FileWriter(f));
            String s = fileToString(f);

            Set<Class<?>> classes = findClassesAssignableFrom("bboss.org.jgroups.protocols", Protocol.class);
            classes.addAll(findClassesAssignableFrom("bboss.org.jgroups.protocols.pbcast", Protocol.class));
            Properties props = new Properties();
            for (Class<?> clazz : classes) {
                convertToDocbookTable(props, clazz);
            }

            String result = Util.replaceProperties(s, props);
            FileWriter fw = new FileWriter(f, false);
            fw.write(result);
            fw.flush();
            fw.close();
            
            
            
            // copy installation.xml file into installation-temp.xml
            f = new File(temp_file2);
            copy(new FileReader(new File(input2)), new FileWriter(f));
            s = fileToString(f);
            
            props = new Properties();
            List<Class<?>> unsupportedClasses = findClassesAnnotatedWith("org.jgroups",Unsupported.class);
            convertToDocbookTable(props, unsupportedClasses, "Unsupported");
            List<Class<?>> experimentalClasses = findClassesAnnotatedWith("org.jgroups",Experimental.class);            
            convertToDocbookTable(props, experimentalClasses, "Experimental");
            
            result = Util.replaceProperties(s, props);
            fw = new FileWriter(f, false);
            fw.write(result);
            fw.flush();
            fw.close();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void help() {
        System.out.println("PropertiesToXML <input XML file>");
    }

    private static Set<Class<?>> findClassesAssignableFrom(String packageName, Class<?> assignableFrom)
                    throws IOException, ClassNotFoundException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Set<Class<?>> classes = new HashSet<Class<?>>();
        String path = packageName.replace('.', '/');
        URL resource = loader.getResource(path);
        if (resource != null) {
            String filePath = resource.getFile();
            if (filePath != null && new File(filePath).isDirectory()) {
                for (String file : new File(filePath).list()) {
                    if (file.endsWith(".class")) {
                        String name = packageName + '.' + file.substring(0, file.indexOf(".class"));
                        Class<?> clazz = Class.forName(name);
                        if (assignableFrom.isAssignableFrom(clazz))
                            classes.add(clazz);
                    }
                }
            }
        }
        return classes;
    }
    
    private static List<Class<?>> findClassesAnnotatedWith(String packageName, Class<? extends Annotation> a) throws IOException, ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<Class<?>>();
        recurse(classes, packageName, a);
        return classes;
    }

    private static void recurse(List<Class<?>> classes, String packageName, Class<? extends Annotation> a) throws ClassNotFoundException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        String path = packageName.replace('.', '/');
        URL resource = loader.getResource(path);
        if (resource != null) {
            String filePath = resource.getFile();
            if (filePath != null && new File(filePath).isDirectory()) {
                for (String file : new File(filePath).list()) {
                    if (file.endsWith(".class")) {
                        String name = packageName + '.' + file.substring(0, file.indexOf(".class"));
                        Class<?> clazz = Class.forName(name);
                        if (clazz.isAnnotationPresent(a))
                            classes.add(clazz);
                    }
                    else if (new File(filePath,file).isDirectory()) {
                        recurse(classes, packageName + "." + file, a);
                    }
                }
            }
        }
    }
    
    private static void convertToDocbookTable(Properties props, List<Class<?>> clazzes, String title)
                    throws ParserConfigurationException, TransformerException {

        Document xmldoc = null;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        DOMImplementation impl = builder.getDOMImplementation();
        xmldoc = impl.createDocument(null, "table", null);
        Element tbody = createXMLTable(xmldoc, title);

        for (Class<?> clazz : clazzes) {
            Element row = xmldoc.createElement("row");
            Element entry = xmldoc.createElement("entry");
            entry.setTextContent(clazz.getPackage().getName());
            row.appendChild(entry);

            entry = xmldoc.createElement("entry");
            entry.setTextContent(clazz.getSimpleName());
            row.appendChild(entry);
            tbody.appendChild(row);
        }

        // do we have more than one property (superclass Protocol has only one property (stats))
        if (clazzes != null && clazzes.size() > 1) {
            DOMSource domSource = new DOMSource(xmldoc);
            StringWriter sw = new StringWriter();
            StreamResult streamResult = new StreamResult(sw);
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer serializer = tf.newTransformer();
            serializer.setOutputProperty(OutputKeys.ENCODING, "ISO-8859-1");
            serializer.setOutputProperty(OutputKeys.INDENT, "yes");
            serializer.transform(domSource, streamResult);
            StringBuffer buffer = sw.getBuffer();
            buffer.delete(0, buffer.indexOf("table") - 1);
            props.put(title, buffer.toString());
        }
    }

    private static void convertToDocbookTable(Properties props, Class<?> clazz)
                    throws ParserConfigurationException, TransformerException {
        boolean isConcreteClass = (clazz.getModifiers() & Modifier.ABSTRACT) == 0;
        boolean isExperimental = clazz.isAnnotationPresent(Experimental.class);
        boolean isUnsupported = clazz.isAnnotationPresent(Unsupported.class);
        // if(isConcreteClass && !isExperimental && !isUnsupported) {
        if (isConcreteClass && !isUnsupported) {
            Class<?> protocol = clazz;
            Document xmldoc = null;
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            DOMImplementation impl = builder.getDOMImplementation();
            xmldoc = impl.createDocument(null, "table", null);
            Element tbody = createXMLTree(xmldoc, isExperimental);
            Map<String, String> nameToDescription = new TreeMap<String, String>();

            // iterate fields
            for (Class clazzInLoop = clazz; clazzInLoop != null; clazzInLoop = clazzInLoop
                            .getSuperclass()) {
                Field[] fields = clazzInLoop.getDeclaredFields();
                for (Field field : fields) {
                    if (field.isAnnotationPresent(Property.class)) {
                        String property = field.getName();
                        Property annotation = field.getAnnotation(Property.class);
                        String desc = annotation.description();
                        nameToDescription.put(property, desc);
                    }
                }
            }

            // iterate methods
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                if (method.isAnnotationPresent(Property.class)
                                && method.getName().startsWith("set")) {

                    Property annotation = method.getAnnotation(Property.class);
                    String desc = annotation.description();

                    if (desc.length() > 0) {

                        String name = annotation.name();
                        if (name.length() < 1) {
                            name = Util.methodNameToAttributeName(method.getName());
                        }
                        nameToDescription.put(name, desc);
                    }
                }
            }

            // and write them out
            for (Map.Entry<String, String> e : nameToDescription.entrySet()) {
                Element row = xmldoc.createElement("row");
                Element entry = xmldoc.createElement("entry");
                entry.setTextContent(e.getKey());
                row.appendChild(entry);

                entry = xmldoc.createElement("entry");
                entry.setTextContent(e.getValue());
                row.appendChild(entry);
                tbody.appendChild(row);
            }

            // do we have more than one property (superclass Protocol has only one property (stats))
            if (nameToDescription.size() > 1) {
                DOMSource domSource = new DOMSource(xmldoc);
                StringWriter sw = new StringWriter();
                StreamResult streamResult = new StreamResult(sw);
                TransformerFactory tf = TransformerFactory.newInstance();
                Transformer serializer = tf.newTransformer();
                serializer.setOutputProperty(OutputKeys.ENCODING, "ISO-8859-1");
                serializer.setOutputProperty(OutputKeys.INDENT, "yes");
                serializer.transform(domSource, streamResult);
                StringBuffer buffer = sw.getBuffer();
                buffer.delete(0, buffer.indexOf("table") - 1);
                props.put(protocol.getSimpleName(), buffer.toString());
            }
        }
    }

    private static String fileToString(File f) throws Exception {
        StringWriter output = new StringWriter();
        FileReader input = new FileReader(f);
        char[] buffer = new char[8 * 1024];
        int count = 0;
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return output.toString();
    }

    public static int copy(Reader input, Writer output) throws IOException {
        char[] buffer = new char[8 * 1024];
        int count = 0;
        int n = 0;
        try {
            while (-1 != (n = input.read(buffer))) {
                output.write(buffer, 0, n);
                count += n;
            }
        } finally {
            output.flush();
            output.close();
        }
        return count;
    }

    private static Element createXMLTree(Document xmldoc, boolean experimental)
                    throws ParserConfigurationException {

        Element root = xmldoc.getDocumentElement();
        Element title = xmldoc.createElement("title");
        if (experimental)
            title.setTextContent("Properties (experimental)");
        else
            title.setTextContent("Properties");
        root.appendChild(title);

        Element tgroup = xmldoc.createElement("tgroup");
        tgroup.setAttribute("cols", "2");
        root.appendChild(tgroup);

        Element colspec = xmldoc.createElement("colspec");
        colspec.setAttribute("align", "left");
        tgroup.appendChild(colspec);

        Element thead = xmldoc.createElement("thead");
        tgroup.appendChild(thead);

        Element row = xmldoc.createElement("row");
        thead.appendChild(row);

        Element entry = xmldoc.createElement("entry");
        entry.setAttribute("align", "center");
        entry.setTextContent("Name");
        row.appendChild(entry);

        entry = xmldoc.createElement("entry");
        entry.setAttribute("align", "center");
        entry.setTextContent("Description");
        row.appendChild(entry);

        Element tbody = xmldoc.createElement("tbody");
        tgroup.appendChild(tbody);

        return tbody;
    }

    private static Element createXMLTable(Document xmldoc, String titleContent)
                    throws ParserConfigurationException {

        Element root = xmldoc.getDocumentElement();
        Element title = xmldoc.createElement("title");
        title.setTextContent(titleContent);
        root.appendChild(title);

        Element tgroup = xmldoc.createElement("tgroup");
        tgroup.setAttribute("cols", "2");
        root.appendChild(tgroup);

        Element colspec = xmldoc.createElement("colspec");
        colspec.setAttribute("align", "left");
        tgroup.appendChild(colspec);

        Element thead = xmldoc.createElement("thead");
        tgroup.appendChild(thead);

        Element row = xmldoc.createElement("row");
        thead.appendChild(row);

        Element entry = xmldoc.createElement("entry");
        entry.setAttribute("align", "center");
        entry.setTextContent("Package");
        row.appendChild(entry);

        entry = xmldoc.createElement("entry");
        entry.setAttribute("align", "center");
        entry.setTextContent("Class");
        row.appendChild(entry);

        Element tbody = xmldoc.createElement("tbody");
        tgroup.appendChild(tbody);

        return tbody;
    }
}
