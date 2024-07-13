package net.ttddyy.dsproxy.transform;

import net.ttddyy.dsproxy.ConnectionInfo;
import net.ttddyy.dsproxy.proxy.ProxyConfig;
import net.ttddyy.dsproxy.proxy.jdk.ConnectionInvocationHandler;
import net.ttddyy.dsproxy.proxy.jdk.StatementInvocationHandler;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Method;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tadaya Tsuyukubo
 */
public class TransformInfoForQueryTest {

    private TransformInfo transformInfo;

    @Before
    public void setUp() {
        transformInfo = null;
    }

    private QueryTransformer getMockQueryTransformer(int timesToAnswer) {
        Answer<String> answer = new Answer<String>() {
            public String answer(InvocationOnMock invocation) throws Throwable {
                transformInfo = (TransformInfo) invocation.getArguments()[0];
                return "modified-query";
            }
        };

        QueryTransformer queryTransformer = mock(QueryTransformer.class);
        for (int i = 0; i < timesToAnswer; i++) {
            when(queryTransformer.transformQuery(isA(TransformInfo.class))).then(answer);
        }

        return queryTransformer;
    }

    @Test
    public void testQueryTransformerInStatement() throws Throwable {
        Statement stmt = mock(Statement.class);
        QueryTransformer queryTransformer = getMockQueryTransformer(1);
        ProxyConfig proxyConfig = ProxyConfig.Builder.create().queryTransformer(queryTransformer).build();

        StatementInvocationHandler handler = new StatementInvocationHandler(stmt, getConnectionInfo(), null, proxyConfig);

        Method method = Statement.class.getMethod("execute", String.class);
        Object[] args = new Object[]{"my-query"};
        handler.invoke(null, method, args);

        verify(queryTransformer).transformQuery(argThat(transformInfo -> {
            validateTransformInfo(transformInfo, Statement.class, "my-query", false, 0);
            return true;
        }));
    }

    @Test
    public void testQueryTransformerBatchInStatement() throws Throwable {
        Statement stmt = mock(Statement.class);
        QueryTransformer queryTransformer = getMockQueryTransformer(2);
        ProxyConfig proxyConfig = ProxyConfig.Builder.create().queryTransformer(queryTransformer).build();

        StatementInvocationHandler handler = new StatementInvocationHandler(stmt, getConnectionInfo(), null, proxyConfig);

        Method method = Statement.class.getMethod("addBatch", String.class);

        // First batch
        handler.invoke(null, method, new Object[]{"my-query-1"});
        verify(queryTransformer).transformQuery(argThat(transformInfo -> {
            validateTransformInfo(transformInfo, Statement.class, "my-query-1", true, 0);
            return true;
        }));

        // Second batch
        handler.invoke(null, method, new Object[]{"my-query-2"});
        verify(queryTransformer, times(2)).transformQuery(argThat(transformInfo -> {
            validateTransformInfo(transformInfo, Statement.class, "my-query-2", true, 1);
            return true;
        }));
    }

    @Test
    public void testQueryTransformerInConnectionHandlerForPrepareStatement() throws Throwable {
        Connection conn = mock(Connection.class);
        QueryTransformer queryTransformer = getMockQueryTransformer(1);
        ProxyConfig proxyConfig = ProxyConfig.Builder.create().queryTransformer(queryTransformer).build();

        ConnectionInvocationHandler handler = new ConnectionInvocationHandler(conn, getConnectionInfo(), proxyConfig);

        Method method = Connection.class.getMethod("prepareStatement", String.class);
        handler.invoke(null, method, new Object[]{"my-query"});

        verify(queryTransformer).transformQuery(argThat(transformInfo -> {
            validateTransformInfo(transformInfo, PreparedStatement.class, "my-query", false, 0);
            return true;
        }));
    }

    @Test
    public void testQueryTransformerInConnectionHandlerForPrepareCall() throws Throwable {
        Connection conn = mock(Connection.class);
        QueryTransformer queryTransformer = getMockQueryTransformer(1);
        ProxyConfig proxyConfig = ProxyConfig.Builder.create().queryTransformer(queryTransformer).build();

        ConnectionInvocationHandler handler = new ConnectionInvocationHandler(conn, getConnectionInfo(), proxyConfig);

        Method method = Connection.class.getMethod("prepareCall", String.class);
        handler.invoke(null, method, new Object[]{"my-query"});

        verify(queryTransformer).transformQuery(argThat(transformInfo -> {
            validateTransformInfo(transformInfo, CallableStatement.class, "my-query", false, 0);
            return true;
        }));
    }

    private void validateTransformInfo(TransformInfo transformInfo, Class<?> expectedClass, String expectedQuery, boolean isBatch, int expectedCount) {
        assertThat(transformInfo).isNotNull();
        assertThat(transformInfo.getClazz()).isEqualTo(expectedClass);
        assertThat(transformInfo.getQuery()).isEqualTo(expectedQuery);
        assertThat(transformInfo.getDataSourceName()).isEqualTo("my-ds");
        assertThat(transformInfo.isBatch()).isEqualTo(isBatch);
        assertThat(transformInfo.getCount()).isEqualTo(expectedCount);
    }


    private ConnectionInfo getConnectionInfo() {
        ConnectionInfo connectionInfo = new ConnectionInfo();
        connectionInfo.setDataSourceName("my-ds");
        return connectionInfo;
    }
}
