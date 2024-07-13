package net.ttddyy.dsproxy.listener;
import java.lang.reflect.Method;

public class MessageParameters {
    private long seq;
    private Throwable thrown;
    private long execTime;
    private String connectionId;
    private Class<?> targetClass;
    private Method method;
    private String args;

    // Constructors, getters, and setters
    public MessageParameters(long seq, Throwable thrown, long execTime,
                             String connectionId, Class<?> targetClass, Method method, String args) {
        this.seq = seq;
        this.thrown = thrown;
        this.execTime = execTime;
        this.connectionId = connectionId;
        this.targetClass = targetClass;
        this.method = method;
        this.args = args;
    }

    public long getSeq() { return seq; }
    public Throwable getThrown() { return thrown; }
    public long getExecTime() { return execTime; }
    public String getConnectionId() { return connectionId; }
    public Class<?> getTargetClass() { return targetClass; }
    public Method getMethod() { return method; }
    public String getArgs() { return args; }
}

