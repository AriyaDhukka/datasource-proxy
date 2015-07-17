package net.ttddyy.dsproxy.test.hamcrest;

import net.ttddyy.dsproxy.test.StatementBatchExecution;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;

/**
 * @author Tadaya Tsuyukubo
 * @since 1.4
 */
public class StatementBatchExecutionMatcher {

    public static Matcher<StatementBatchExecution> queries(final int count, Matcher<String> stringMatcher) {
        String msg = "queries[" + count + "]";
        return new FeatureMatcher<StatementBatchExecution, String>(stringMatcher, msg, msg) {
            @Override
            protected String featureValueOf(StatementBatchExecution actual) {
                return actual.getQueries().get(count);  // TODO: list size check
            }
        };
    }

    public static Matcher<StatementBatchExecution> queries(Matcher<? super Iterable<String>> collectionMatcher) {
        return new FeatureMatcher<StatementBatchExecution, Iterable<String>>(collectionMatcher, "queries", "queries") {
            @Override
            protected Iterable<String> featureValueOf(StatementBatchExecution actual) {
                return actual.getQueries();
            }
        };
    }


}
