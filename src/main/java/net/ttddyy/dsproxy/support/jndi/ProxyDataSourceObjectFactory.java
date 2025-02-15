package net.ttddyy.dsproxy.support.jndi;

import net.ttddyy.dsproxy.listener.logging.CommonsLogLevel;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.listener.logging.Log4jLogLevel;
import net.ttddyy.dsproxy.listener.logging.SLF4JLogLevel;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import net.ttddyy.dsproxy.transform.ParameterTransformer;
import net.ttddyy.dsproxy.transform.QueryTransformer;

import javax.naming.*;
import javax.naming.spi.ObjectFactory;
import javax.sql.DataSource;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

/**
 * JNDI ObjectFactory to create {@link net.ttddyy.dsproxy.support.ProxyDataSource}.
 *
 * <pre>{@code
 * <Resource name="jdbc/global/myProxy"
 *           auth="Container"
 *           type="net.ttddyy.dsproxy.support.ProxyDataSource"
 *           factory="net.ttddyy.dsproxy.support.jndi.ProxyDataSourceObjectFactory"
 *           description="ds"
 *           listeners="count,sysout,org.example.SampleListener"
 *           proxyName="DS-PROXY"
 *           format="json"
 *           dataSource="[REFERENCE_TO_ACTUAL_DATASOURCE_RESOURCE]"  <!-- ex: java:jdbc/global/myDS -->
 * />
 * }</pre>
 *
 * Parameters:
 * <ul>
 * <li> <b>dataSource <i>(required)</i></b>: Reference to actual datasource resource.  ex: java:jdbc/global/myDS
 * <li> <b>proxyName</b>:             ProxyDataSource name
 * <li> <b>logLevel</b>:              Loglevel for commons-logging, slf4j or log4j. ex: DEBUG, INFO, etc.
 * <li> <b>listeners</b>:             Fully qualified class name of `QueryExecutionListener` implementation class,or predefined values below. Can be comma delimited.
 * <li> <b>queryTransformer</b>:      Fully qualified class name of `QueryTransformer` implementation class.
 * <li> <b>parameterTransformer</b>:  Fully qualified class name of `ParameterTransformer` implementation class.
 * </ul>
 *
 * <i>listeners</i> parameter:
 * <ul>
 * <li> <b>sysout</b>:   alias to `net.ttddyy.dsproxy.listener.logging.SystemOutQueryLoggingListener`
 * <li> <b>commons</b>:  alias to `net.ttddyy.dsproxy.listener.logging.CommonsQueryLoggingListener`
 * <li> <b>slf4j</b>:    alias to `net.ttddyy.dsproxy.listener.logging.SLF4JQueryLoggingListener`
 * <li> <b>log4j</b>:    alias to `net.ttddyy.dsproxy.listener.logging.Log4jSlowQueryListener`
 * <li> <b>count</b>:    alias to `net.ttddyy.dsproxy.listener.DataSourceQueryCountListener`
 * <li> <b>x.y.z.MyQueryExecutionListener</b>: Fully qualified class name of `QueryExecutionListener` implementation
 * </ul>
 *
 * <i>format</i> parameter:
 * <ul>
 * <li> <b>json</b>: set logging output format as JSON
 * </ul>
 *
 * @author Tadaya Tsuyukubo
 * @since 1.3
 */
public class ProxyDataSourceObjectFactory implements ObjectFactory {

    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment) throws Exception {
        if (obj == null || !(obj instanceof Reference)) {
            return null;
        }
        Reference reference = (Reference) obj;

        String dataSourceJndiName = getContentFromReference(reference, "dataSource");
        String proxyDataSourceName = getContentFromReference(reference, "proxyName");
        String listenerNames = getContentFromReference(reference, "listeners");
        String logLevel = getContentFromReference(reference, "logLevel");
        String loggerName = getContentFromReference(reference, "loggerName");
        String format = getContentFromReference(reference, "format");
        String queryTransformer = getContentFromReference(reference, "queryTransformer");
        String parameterTransformer = getContentFromReference(reference, "parameterTransformer");

        // Retrieve datasource from JNDI
        Object dataSourceResource = new InitialContext().lookup(dataSourceJndiName);
        if (dataSourceResource == null) {
            throw new Exception(String.format("%s is not available.", dataSourceJndiName));
        } else if (!(dataSourceResource instanceof DataSource)) {
            throw new Exception(String.format("%s is not DataSource: %s", dataSourceJndiName, dataSourceResource));
        }
        DataSource dataSource = (DataSource) dataSourceResource;

        // Builder
        ProxyDataSourceBuilder builder = ProxyDataSourceBuilder.create(dataSource);

        if (proxyDataSourceName != null) {
            builder.name(proxyDataSourceName);
        }

        if (listenerNames != null) {
            for (String listenerName : getListenerNames(listenerNames)) {
                configureListener(builder, listenerName, logLevel, loggerName);
            }

            if (format != null && "json".equals(format.toLowerCase())) {
                builder.asJson();
            }
        }

        if (queryTransformer != null) {
            try {
                QueryTransformer transformer = createNewInstance(QueryTransformer.class, queryTransformer);
                builder.queryTransformer(transformer);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create query transformer: " + queryTransformer, e);
            }
        }

        if (parameterTransformer != null) {
            try {
                ParameterTransformer transformer = createNewInstance(ParameterTransformer.class, parameterTransformer);
                builder.parameterTransformer(transformer);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create parameter transformer: " + parameterTransformer, e);
            }
        }

        return builder.build();
    }

    private void configureListener(ProxyDataSourceBuilder builder, String listenerName, String logLevel, String loggerName) {
        try {
            switch (listenerName.toLowerCase()) {
                case "commons":
                    configureCommonsListener(builder, logLevel, loggerName);
                    break;
                case "slf4j":
                    configureSlf4jListener(builder, logLevel, loggerName);
                    break;
                case "log4j":
                    configureLog4jListener(builder, logLevel, loggerName);
                    break;
                case "sysout":
                    builder.logQueryToSysOut();
                    break;
                case "count":
                    builder.countQuery();
                    break;
                default:
                    addCustomListener(builder, listenerName);
                    break;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure listener: " + listenerName, e);
        }
    }

    private void addCustomListener(ProxyDataSourceBuilder builder, String listenerName) {
        try {
            QueryExecutionListener listener = createNewInstance(QueryExecutionListener.class, listenerName);
            builder.listener(listener);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create listener: " + listenerName, e);
        }
    }

    private void configureCommonsListener(ProxyDataSourceBuilder builder, String logLevel, String loggerName) {
        try {
            if (logLevel != null && loggerName != null) {
                builder.logQueryByCommons(CommonsLogLevel.valueOf(logLevel.toUpperCase()), loggerName);
            } else if (logLevel != null) {
                builder.logQueryByCommons(CommonsLogLevel.valueOf(logLevel.toUpperCase()));
            } else if (loggerName != null) {
                builder.logQueryByCommons(loggerName);
            } else {
                builder.logQueryByCommons();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure commons listener", e);
        }
    }

    private void configureSlf4jListener(ProxyDataSourceBuilder builder, String logLevel, String loggerName) {
        try {
            if (logLevel != null && loggerName != null) {
                builder.logQueryBySlf4j(SLF4JLogLevel.valueOf(logLevel.toUpperCase()), loggerName);
            } else if (logLevel != null) {
                builder.logQueryBySlf4j(SLF4JLogLevel.valueOf(logLevel.toUpperCase()));
            } else if (loggerName != null) {
                builder.logQueryBySlf4j(loggerName);
            } else {
                builder.logQueryBySlf4j();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure SLF4J listener", e);
        }
    }

    private void configureLog4jListener(ProxyDataSourceBuilder builder, String logLevel, String loggerName) {
        try {
            if (logLevel != null && loggerName != null) {
                builder.logQueryByLog4j(Log4jLogLevel.valueOf(logLevel.toUpperCase()), loggerName);
            } else if (logLevel != null) {
                builder.logQueryByLog4j(Log4jLogLevel.valueOf(logLevel.toUpperCase()));
            } else if (loggerName != null) {
                builder.logQueryByLog4j(loggerName);
            } else {
                builder.logQueryByLog4j();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure Log4j listener", e);
        }
    }

    @SuppressWarnings("unchecked")
    protected <T> T createNewInstance(Class<T> clazz, String className) throws Exception {
        try {
            return (T) Class.forName(className).newInstance();
        } catch (Exception e) {
            String msg = String.format("Failed to create %s - %s: %s", clazz.getSimpleName(), e.getClass().getName(), e.getMessage());
            throw new Exception(msg);
        }
    }

    protected String getContentFromReference(Reference reference, String key) {
        RefAddr refAddr = reference.get(key);
        if (refAddr == null) {
            return null;
        }
        return refAddr.getContent().toString();
    }

    protected String[] getListenerNames(String listenerNames) {
        Set<String> listenerNameSet = new HashSet<String>();
        for (String listenerName : listenerNames.split(",")) {
            listenerNameSet.add(trim(listenerName));
        }
        return listenerNameSet.toArray(new String[listenerNameSet.size()]);

    }

    protected String trim(String s) {
        int length = s.length();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
