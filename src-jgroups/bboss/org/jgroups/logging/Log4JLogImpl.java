package bboss.org.jgroups.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logger that delivers messages to a Log4J logger
 * 
 * @author Manik Surtani
 * @author Bela Ban
 * @since 2.8
 */
public class Log4JLogImpl implements Log {

    private static final String FQCN = Log4JLogImpl.class.getName();

    private final Logger logger;

    public Log4JLogImpl(String category) {
        logger = LoggerFactory.getLogger(category);
    }

    public Log4JLogImpl(Class<?> category) {
        logger = LoggerFactory.getLogger(category);
    }

    public boolean isFatalEnabled() {
        return logger.isErrorEnabled();
    }

    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }

    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    public void debug(String msg) {
        logger.debug(  msg);
    }

    public void debug(String msg, Throwable throwable) {
        logger.debug(  msg, throwable);
    }

    public void error(String msg) {
        logger.error(msg);
    }

    public void error(String msg, Throwable throwable) {
        logger.error(  msg, throwable);
    }

    public void fatal(String msg) {
        logger.error(msg);
    }

    public void fatal(String msg, Throwable throwable) {
        logger.error( msg, throwable);
    }

    public void info(String msg) {
        logger.info( msg);
    }

    public void info(String msg, Throwable throwable) {
        logger.info( msg, throwable);
    }

    public void trace(Object msg) {
        logger.trace( String.valueOf(msg));
    }

    public void trace(Object msg, Throwable throwable) {
        logger.trace( String.valueOf( msg), throwable);
    }

    public void trace(String msg) {
        logger.trace(  msg);
    }

    public void trace(String msg, Throwable throwable) {
        logger.trace(  msg, throwable);
    }

    public void warn(String msg) {
        logger.warn( msg);
    }

    public void warn(String msg, Throwable throwable) {
        logger.warn( msg, throwable);
    }

    public String getLevel() {
//       
//        return level != null ? level.toString() : "off";
    	return null;
    }

    public void setLevel(String level) {
//        Level new_level = strToLevel(level);
//        if (new_level != null)
//            logger.setLevel(new_level);
    }

//    private static Level strToLevel(String level) {
//        if (level == null)
//            return null;
//        level = level.toLowerCase().trim();
//        if (level.equals("fatal"))
//            return Level.FATAL;
//        if (level.equals("error"))
//            return Level.ERROR;
//        if (level.equals("warn"))
//            return Level.WARN;
//        if (level.equals("warning"))
//            return Level.WARN;
//        if (level.equals("info"))
//            return Level.INFO;
//        if (level.equals("debug"))
//            return Level.DEBUG;
//        if (level.equals("trace"))
//            return Level.TRACE;
//        return null;
//    }
}
