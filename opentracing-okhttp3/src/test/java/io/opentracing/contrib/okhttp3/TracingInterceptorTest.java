package io.opentracing.contrib.okhttp3;

import java.util.concurrent.ExecutionException;

import org.junit.Ignore;

import okhttp3.OkHttpClient;

/**
 * @author Pavol Loffay
 */
public class TracingInterceptorTest extends AbstractOkHttpTest {

    public TracingInterceptorTest() {
        super(TracingInterceptor.addTracing(new OkHttpClient.Builder(), AbstractOkHttpTest.mockTracer));
    }

    @Ignore("Does not work for interceptors")
    @Override
    public void testAsyncMultipleRequests() throws ExecutionException, InterruptedException {
    }
}
