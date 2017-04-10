package io.opentracing.contrib.okhttp3;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import org.hamcrest.core.IsEqual;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.jayway.awaitility.Awaitility;

import io.opentracing.Span;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

/**
 * @author Pavol Loffay
 */
public class TracingInterceptorTest {

    private MockTracer mockTracer = new MockTracer();
    private MockWebServer mockWebServer = new MockWebServer();
    private OkHttpClient okHttpClient;

    public TracingInterceptorTest() {
        TracingInterceptor tracingInterceptor =
                new TracingInterceptor(mockTracer, Arrays.asList(SpanDecorator.STANDARD_TAGS));

        okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(tracingInterceptor)
                .addNetworkInterceptor(tracingInterceptor)
                .followRedirects(true)
                .build();
    }

    @Before
    public void before() throws IOException {
        mockTracer.reset();
        mockWebServer.start();
    }

    @After
    public void after() throws IOException {
        mockWebServer.close();
    }

    @Test
    public void testStandardTags() throws IOException {
        {
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(202));

            okHttpClient.newCall(new Request.Builder()
                    .url(mockWebServer.url("foo"))
                    .build())
                    .execute();
        }

        List<MockSpan> mockSpans = mockTracer.finishedSpans();
        Assert.assertEquals(1, mockSpans.size());
        assertOnErrors(mockSpans);

        MockSpan mockSpan = mockSpans.get(0);
        Assert.assertEquals(8, mockSpan.tags().size());
        Assert.assertEquals("java-okhttp", mockSpan.tags().get(Tags.COMPONENT.getKey()));
        Assert.assertEquals(Tags.SPAN_KIND_CLIENT, mockSpan.tags().get(Tags.SPAN_KIND.getKey()));
        Assert.assertEquals("GET", mockSpan.tags().get(Tags.HTTP_METHOD.getKey()));
        Assert.assertEquals("http://localhost:" + mockWebServer.getPort() + "/foo",
                mockSpan.tags().get(Tags.HTTP_URL.getKey()));
        Assert.assertEquals(202, mockSpan.tags().get(Tags.HTTP_STATUS.getKey()));
        Assert.assertEquals(mockWebServer.getPort(), mockSpan.tags().get(Tags.PEER_PORT.getKey()));
        Assert.assertEquals("localhost", mockSpan.tags().get(Tags.PEER_HOSTNAME.getKey()));
        Assert.assertEquals(ipv4ToInt("127.0.0.1"), mockSpan.tags().get(Tags.PEER_HOST_IPV4.getKey()));
        Assert.assertEquals(0, mockSpan.logEntries().size());
    }

    @Test
    public void testAsyncStandardTags() throws IOException {
        {
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(202));

            okHttpClient.newCall(new Request.Builder()
                    .url(mockWebServer.url("foo"))
                    .build())
                    .enqueue(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {}
                        @Override
                        public void onResponse(Call call, Response response) throws IOException {}
                    });
        }

        Awaitility.await().until(reportedSpansSize(), IsEqual.equalTo(1));
        List<MockSpan> mockSpans = mockTracer.finishedSpans();
        Assert.assertEquals(1, mockSpans.size());
        assertOnErrors(mockSpans);

        MockSpan mockSpan = mockSpans.get(0);
        Assert.assertEquals(8, mockSpan.tags().size());
        Assert.assertEquals("java-okhttp", mockSpan.tags().get(Tags.COMPONENT.getKey()));
        Assert.assertEquals(Tags.SPAN_KIND_CLIENT, mockSpan.tags().get(Tags.SPAN_KIND.getKey()));
        Assert.assertEquals("http://localhost:" + mockWebServer.getPort() + "/foo",
                mockSpan.tags().get(Tags.HTTP_URL.getKey()));
        Assert.assertEquals("GET", mockSpan.tags().get(Tags.HTTP_METHOD.getKey()));
        Assert.assertEquals(202, mockSpan.tags().get(Tags.HTTP_STATUS.getKey()));
        Assert.assertEquals(mockWebServer.getPort(), mockSpan.tags().get(Tags.PEER_PORT.getKey()));
        Assert.assertEquals("localhost", mockSpan.tags().get(Tags.PEER_HOSTNAME.getKey()));
        Assert.assertEquals(ipv4ToInt("127.0.0.1"), mockSpan.tags().get(Tags.PEER_HOST_IPV4.getKey()));
        Assert.assertEquals(0, mockSpan.logEntries().size());
    }

    @Test
    public void testUnknownHostException() throws IOException {
        {
            Request request = new Request.Builder()
                    .url("http://qwertydsanx.com/")
                    .get()
                    .build();

            try {
                okHttpClient.newCall(request).execute();
            } catch (Exception ex) {}
        }

        List<MockSpan> mockSpans = mockTracer.finishedSpans();
        Assert.assertEquals(1, mockSpans.size());
        assertOnErrors(mockSpans);

        MockSpan mockSpan = mockSpans.get(0);
        Assert.assertEquals(5, mockSpans.get(0).tags().size());
        Assert.assertEquals("java-okhttp", mockSpan.tags().get(Tags.COMPONENT.getKey()));
        Assert.assertEquals(Tags.SPAN_KIND_CLIENT, mockSpan.tags().get(Tags.SPAN_KIND.getKey()));
        Assert.assertEquals("GET", mockSpan.tags().get(Tags.HTTP_METHOD.getKey()));
        Assert.assertEquals("http://qwertydsanx.com/", mockSpan.tags().get(Tags.HTTP_URL.getKey()));
        Assert.assertEquals(Boolean.TRUE, mockSpan.tags().get(Tags.ERROR.getKey()));
        Assert.assertEquals(1, mockSpan.logEntries().size());
        Assert.assertEquals(4, mockSpan.logEntries().get(0).fields().size());
        Assert.assertEquals(Tags.ERROR.getKey(), mockSpan.logEntries().get(0).fields().get("event"));
        Assert.assertNotNull(mockSpan.logEntries().get(0).fields().get("message"));
        Assert.assertNotNull(mockSpan.logEntries().get(0).fields().get("stack"));
        Assert.assertNotNull(mockSpan.logEntries().get(0).fields().get("error.kind"));
    }

    @Test
    public void testParentSpan() throws IOException {
        {
            Span parent = mockTracer.buildSpan("parent")
                    .start();

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(203));

            Request request = new Request.Builder()
                    .url(mockWebServer.url("bar"))
                    .tag(new TagWrapper(parent.context()))
                    .get()
                    .build();

            okHttpClient.newCall(request).execute();
            parent.finish();
        }

        List<MockSpan> mockSpans = mockTracer.finishedSpans();
        assertOnErrors(mockSpans);
        Assert.assertEquals(2, mockSpans.size());
        Assert.assertEquals(mockSpans.get(0).context().traceId(), mockSpans.get(1).context().traceId());
        Assert.assertEquals(mockSpans.get(0).parentId(), mockSpans.get(1).context().spanId());
    }

    @Test
    public void testFollowRedirectsTrue() throws IOException {
        {
            mockWebServer.enqueue(new MockResponse().setResponseCode(301).setHeader("Location", "/redirect"));
            mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("after redirect"));

            Request request = new Request.Builder()
                    .url(mockWebServer.url("foo"))
                    .get()
                    .build();

            okHttpClient.newCall(request).execute();
        }

        List<MockSpan> mockSpans = mockTracer.finishedSpans();
        Assert.assertEquals(1, mockSpans.size());
        assertOnErrors(mockSpans);

        MockSpan mockSpan = mockSpans.get(0);
        Assert.assertEquals(200, mockSpan.tags().get(Tags.HTTP_STATUS.getKey()));
        Assert.assertEquals(mockWebServer.url("foo").toString(), mockSpan.tags().get(Tags.HTTP_URL.getKey()));
        Assert.assertEquals(1, mockSpan.logEntries().size());
        Assert.assertEquals(4, mockSpan.logEntries().get(0).fields().size());
        Assert.assertEquals("redirect", mockSpan.logEntries().get(0).fields().get("event"));
        Assert.assertEquals("localhost", mockSpan.logEntries().get(0).fields().get(Tags.PEER_HOSTNAME.getKey()));
        Assert.assertEquals((short)mockWebServer.getPort(),
                mockSpan.logEntries().get(0).fields().get(Tags.PEER_PORT.getKey()));
        Assert.assertEquals(ipv4ToInt("127.0.0.1"),
                mockSpan.logEntries().get(0).fields().get(Tags.PEER_HOST_IPV4.getKey()));
    }

    @Test
    public void testFollowRedirectsFalse() throws IOException {
        {
            mockWebServer.enqueue(new MockResponse().setResponseCode(301).setHeader("Location", "/redirect"));
            mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("after redirect"));

            Request request = new Request.Builder()
                    .url(mockWebServer.url("bar"))
                    .get()
                    .build();

            TracingInterceptor tracingInterceptor =
                    new TracingInterceptor(mockTracer, Arrays.asList(SpanDecorator.STANDARD_TAGS));
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .addInterceptor(tracingInterceptor)
                    .addNetworkInterceptor(tracingInterceptor)
                    .followRedirects(false)
                    .build();
            okHttpClient.newCall(request).execute();
        }

        List<MockSpan> mockSpans = mockTracer.finishedSpans();
        Assert.assertEquals(1, mockSpans.size());
        assertOnErrors(mockSpans);

        MockSpan mockSpan = mockSpans.get(0);
        Assert.assertEquals(301, mockSpan.tags().get(Tags.HTTP_STATUS.getKey()));
        Assert.assertEquals(mockWebServer.url("bar").toString(), mockSpan.tags().get(Tags.HTTP_URL.getKey()));
        Assert.assertEquals(1, mockSpan.logEntries().size());
        Assert.assertEquals(4, mockSpan.logEntries().get(0).fields().size());
        Assert.assertEquals("redirect", mockSpan.logEntries().get(0).fields().get("event"));
        Assert.assertEquals("localhost", mockSpan.logEntries().get(0).fields().get(Tags.PEER_HOSTNAME.getKey()));
        Assert.assertEquals((short)mockWebServer.getPort(),
                mockSpan.logEntries().get(0).fields().get(Tags.PEER_PORT.getKey()));
        Assert.assertEquals(ipv4ToInt("127.0.0.1"),
                mockSpan.logEntries().get(0).fields().get(Tags.PEER_HOST_IPV4.getKey()));
    }

    private Callable<Integer> reportedSpansSize() {
        return new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return mockTracer.finishedSpans().size();
            }
        };
    }

    public static int ipv4ToInt(String address) {
        int result = 0;

        // iterate over each octet
        for(String part : address.split(Pattern.quote("."))) {
            // shift the previously parsed bits over by 1 byte
            result = result << 8;
            // set the low order bits to the current octet
            result |= Integer.parseInt(part);
        }
        return result;
    }

    public static void assertOnErrors(List<MockSpan> spans) {
        for (MockSpan mockSpan: spans) {
            Assert.assertEquals(mockSpan.generatedErrors().toString(), 0, mockSpan.generatedErrors().size());
        }
    }
}
