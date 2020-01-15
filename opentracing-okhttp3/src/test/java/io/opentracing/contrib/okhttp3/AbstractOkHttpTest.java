package io.opentracing.contrib.okhttp3;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.ThreadLocalScopeManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;
import org.awaitility.Awaitility;
import org.hamcrest.core.IsEqual;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

/**
 * @author Pavol Loffay
 */
public abstract class AbstractOkHttpTest {

    protected static MockTracer mockTracer = new MockTracer(new ThreadLocalScopeManager(), MockTracer.Propagator
            .TEXT_MAP);
    protected MockWebServer mockWebServer = new MockWebServer();
    protected Call.Factory client;

    public AbstractOkHttpTest(Call.Factory callFactory) {
        client = callFactory;
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

            client.newCall(new Request.Builder()
                    .url(mockWebServer.url("foo"))
                    .build())
                    .execute();
        }

        List<MockSpan> mockSpans = mockTracer.finishedSpans();
        Assert.assertEquals(2, mockSpans.size());
        assertLocalSpan(mockSpans);
        assertOnErrors(mockSpans);

        MockSpan networkSpan = mockSpans.get(0);
        Assert.assertEquals(8, networkSpan.tags().size());
        Assert.assertEquals(Tags.SPAN_KIND_CLIENT, networkSpan.tags().get(Tags.SPAN_KIND.getKey()));
        Assert.assertEquals(TracingCallFactory.COMPONENT_NAME, networkSpan.tags().get(Tags.COMPONENT.getKey()));
        Assert.assertEquals("GET", networkSpan.tags().get(Tags.HTTP_METHOD.getKey()));
        Assert.assertEquals("http://localhost:" + mockWebServer.getPort() + "/foo",
                networkSpan.tags().get(Tags.HTTP_URL.getKey()));
        Assert.assertEquals(202, networkSpan.tags().get(Tags.HTTP_STATUS.getKey()));
        Assert.assertEquals(mockWebServer.getPort(), networkSpan.tags().get(Tags.PEER_PORT.getKey()));
        Assert.assertEquals("localhost", networkSpan.tags().get(Tags.PEER_HOSTNAME.getKey()));
        Assert.assertEquals(ipv4ToInt("127.0.0.1"), networkSpan.tags().get(Tags.PEER_HOST_IPV4.getKey()));
        Assert.assertEquals(0, networkSpan.logEntries().size());
    }

    @Test
    public void testStandardTagsForPost() throws IOException {
        {
            mockWebServer.enqueue(new MockResponse()
                .setResponseCode(202));

            client.newCall(new Request.Builder()
                .url(mockWebServer.url("foo"))
                .method("POST", new RequestBody() {
                    @Override
                    public MediaType contentType() {
                        return null;
                    }

                    @Override
                    public void writeTo(BufferedSink bufferedSink) throws IOException {

                    }
                })
                .build())
                .execute();
        }

        List<MockSpan> mockSpans = mockTracer.finishedSpans();
        Assert.assertEquals(2, mockSpans.size());
        assertLocalSpan(mockSpans);
        assertOnErrors(mockSpans);

        MockSpan networkSpan = mockSpans.get(0);
        Assert.assertEquals(8, networkSpan.tags().size());
        Assert.assertEquals(Tags.SPAN_KIND_CLIENT, networkSpan.tags().get(Tags.SPAN_KIND.getKey()));
        Assert.assertEquals(TracingCallFactory.COMPONENT_NAME, networkSpan.tags().get(Tags.COMPONENT.getKey()));
        Assert.assertEquals("POST", networkSpan.tags().get(Tags.HTTP_METHOD.getKey()));
        Assert.assertEquals("http://localhost:" + mockWebServer.getPort() + "/foo",
            networkSpan.tags().get(Tags.HTTP_URL.getKey()));
        Assert.assertEquals(202, networkSpan.tags().get(Tags.HTTP_STATUS.getKey()));
        Assert.assertEquals(mockWebServer.getPort(), networkSpan.tags().get(Tags.PEER_PORT.getKey()));
        Assert.assertEquals("localhost", networkSpan.tags().get(Tags.PEER_HOSTNAME.getKey()));
        Assert.assertEquals(ipv4ToInt("127.0.0.1"), networkSpan.tags().get(Tags.PEER_HOST_IPV4.getKey()));
        Assert.assertEquals(0, networkSpan.logEntries().size());
    }

    @Test
    public void testAsyncStandardTags() throws IOException {
        {
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(202));

            client.newCall(new Request.Builder()
                    .url(mockWebServer.url("foo"))
                    .build())
                    .enqueue(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {}
                        @Override
                        public void onResponse(Call call, Response response) throws IOException {}
                    });
        }

        Awaitility.await().until(reportedSpansSize(), IsEqual.equalTo(2));
        List<MockSpan> mockSpans = mockTracer.finishedSpans();
        Assert.assertEquals(2, mockSpans.size());
        assertLocalSpan(mockSpans);
        assertOnErrors(mockSpans);

        MockSpan networkSpan = mockSpans.get(0);
        Assert.assertEquals(8, networkSpan.tags().size());
        Assert.assertEquals(Tags.SPAN_KIND_CLIENT, networkSpan.tags().get(Tags.SPAN_KIND.getKey()));
        Assert.assertEquals(TracingCallFactory.COMPONENT_NAME, networkSpan.tags().get(Tags.COMPONENT.getKey()));
        Assert.assertEquals("http://localhost:" + mockWebServer.getPort() + "/foo",
                networkSpan.tags().get(Tags.HTTP_URL.getKey()));
        Assert.assertEquals("GET", networkSpan.tags().get(Tags.HTTP_METHOD.getKey()));
        Assert.assertEquals(202, networkSpan.tags().get(Tags.HTTP_STATUS.getKey()));
        Assert.assertEquals(mockWebServer.getPort(), networkSpan.tags().get(Tags.PEER_PORT.getKey()));
        Assert.assertEquals("localhost", networkSpan.tags().get(Tags.PEER_HOSTNAME.getKey()));
        Assert.assertEquals(ipv4ToInt("127.0.0.1"), networkSpan.tags().get(Tags.PEER_HOST_IPV4.getKey()));
        Assert.assertEquals(0, networkSpan.logEntries().size());
    }

    @Test
    public void testAsyncMultipleRequests() throws ExecutionException, InterruptedException {
        int numberOfCalls = 100;

        Map<Long, MockSpan> parentSpans = new LinkedHashMap<>(numberOfCalls);

        ExecutorService executorService = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new ArrayList<>(numberOfCalls);
        for (int i = 0; i < numberOfCalls; i++) {
            final String requestUrl = mockWebServer.url("foo/" + i).toString();

            final MockSpan parentSpan = mockTracer.buildSpan(requestUrl)
                    .ignoreActiveSpan()
                    .start();

            parentSpan.setTag("request-url", requestUrl);
            parentSpans.put(parentSpan.context().spanId(), parentSpan);

            futures.add(executorService.submit(new Runnable() {
                @Override
                public void run() {
                    mockWebServer.enqueue(new MockResponse()
                            .setResponseCode(200));

                    mockTracer.scopeManager().activate(parentSpan);
                    client.newCall(new Request.Builder()
                            .url(requestUrl)
                            .build())
                            .enqueue(new Callback() {
                                @Override
                                public void onFailure(Call call, IOException e) {}
                                @Override
                                public void onResponse(Call call, Response response) throws IOException {}
                            });
                }
            }));
        }

        // wait to finish all calls
        for (Future<?> future: futures) {
            future.get();
        }

        executorService.awaitTermination(1, TimeUnit.SECONDS);
        executorService.shutdown();

        List<MockSpan> mockSpans = mockTracer.finishedSpans();
        assertOnErrors(mockSpans);
        Assert.assertEquals(numberOfCalls*2, mockSpans.size());


        Map<Long, MockSpan> spansByIdMap = new HashMap<>(mockSpans.size());
        for (MockSpan mockSpan: mockSpans) {
            spansByIdMap.put(mockSpan.context().spanId(), mockSpan);
        }

        for (MockSpan networkSpan: mockSpans) {
            if (networkSpan.tags().containsKey(Tags.COMPONENT.getKey())) {
                continue;
            }

            MockSpan localSpan = spansByIdMap.get(networkSpan.parentId());
            MockSpan parentSpan = parentSpans.get(localSpan.parentId());

            Assert.assertEquals(parentSpan.tags().get("request-url"), networkSpan.tags().get(Tags.HTTP_URL.getKey()));

            Assert.assertEquals(parentSpan.context().traceId(), localSpan.context().traceId());
            Assert.assertEquals(parentSpan.context().spanId(), localSpan.parentId());
            Assert.assertEquals(0, localSpan.generatedErrors().size());
            Assert.assertEquals(0, networkSpan.generatedErrors().size());
        }
    }

    @Test
    public void testSyncMultipleRequests() throws ExecutionException, InterruptedException {
        int numberOfCalls = 100;

        Map<Long, MockSpan> parentSpans = new LinkedHashMap<>(numberOfCalls);

        ExecutorService executorService = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new ArrayList<>(numberOfCalls);
        for (int i = 0; i < numberOfCalls; i++) {
            final String requestUrl = mockWebServer.url("foo/" + i).toString();

            final MockSpan parentSpan = mockTracer.buildSpan(requestUrl)
                    .ignoreActiveSpan().start();
            parentSpan.setTag("request-url", requestUrl);
            parentSpans.put(parentSpan.context().spanId(), parentSpan);

            futures.add(executorService.submit(new Runnable() {
                @Override
                public void run() {
                    mockWebServer.enqueue(new MockResponse()
                            .setResponseCode(200));

                    mockTracer.scopeManager().activate(parentSpan);
                    try {
                        client.newCall(new Request.Builder()
                                .url(requestUrl)
                                .build()).execute();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }));
        }

        // wait to finish all calls
        for (Future<?> future: futures) {
            future.get();
        }

        executorService.awaitTermination(1, TimeUnit.SECONDS);
        executorService.shutdown();

        List<MockSpan> mockSpans = mockTracer.finishedSpans();
        assertOnErrors(mockSpans);
        Assert.assertEquals(numberOfCalls*2, mockSpans.size());

        Map<Long, MockSpan> spansByIdMap = new HashMap<>(mockSpans.size());
        for (MockSpan mockSpan: mockSpans) {
            spansByIdMap.put(mockSpan.context().spanId(), mockSpan);
        }

        for (MockSpan networkSpan: mockSpans) {
            if (networkSpan.tags().containsKey(Tags.COMPONENT.getKey())) {
                continue;
            }

            MockSpan localSpan = spansByIdMap.get(networkSpan.parentId());
            MockSpan parentSpan = parentSpans.get(localSpan.parentId());

            Assert.assertEquals(parentSpan.tags().get("request-url"), networkSpan.tags().get(Tags.HTTP_URL.getKey()));

            Assert.assertEquals(parentSpan.context().traceId(), localSpan.context().traceId());
            Assert.assertEquals(parentSpan.context().spanId(), localSpan.parentId());
            Assert.assertEquals(0, localSpan.generatedErrors().size());
            Assert.assertEquals(0, networkSpan.generatedErrors().size());
        }
    }

    @Test
    public void testUnknownHostException() throws IOException {
        {
            Request request = new Request.Builder()
                    .url("http://nonexisting.example.com/")
                    .get()
                    .build();

            try {
                client.newCall(request).execute();
            } catch (Exception ex) {
            }
        }

        List<MockSpan> mockSpans = mockTracer.finishedSpans();
        Assert.assertEquals(1, mockSpans.size());
        assertLocalSpan(mockSpans);
        assertOnErrors(mockSpans);

        MockSpan networkSpan = mockSpans.get(0);
        Assert.assertEquals(2, mockSpans.get(0).tags().size());
        Assert.assertEquals(Boolean.TRUE, networkSpan.tags().get(Tags.ERROR.getKey()));
        Assert.assertEquals(1, networkSpan.logEntries().size());
        Assert.assertEquals(2, networkSpan.logEntries().get(0).fields().size());
        Assert.assertEquals(Tags.ERROR.getKey(), networkSpan.logEntries().get(0).fields().get("event"));
        Assert.assertNotNull(networkSpan.logEntries().get(0).fields().get("error.object"));
    }

    @Test
    public void testParentSpanSource() throws IOException {
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

            try (Scope scope = mockTracer.activateSpan(parent)) {
                client.newCall(request).execute();
            }
            parent.finish();
        }

        List<MockSpan> mockSpans = mockTracer.finishedSpans();
        assertOnErrors(mockSpans);
        Assert.assertEquals(3, mockSpans.size());
        Assert.assertEquals(mockSpans.get(2).context().traceId(), mockSpans.get(1).context().traceId());
        Assert.assertEquals(mockSpans.get(2).context().spanId(), mockSpans.get(1).parentId());
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

            client.newCall(request).execute();
        }

        List<MockSpan> mockSpans = mockTracer.finishedSpans();
        Assert.assertEquals(3, mockSpans.size());
        assertLocalSpan(mockSpans);
        assertOnErrors(mockSpans);

        MockSpan networkSpan = mockSpans.get(0);
        Assert.assertEquals(8, networkSpan.tags().size());
        Assert.assertEquals(Tags.SPAN_KIND_CLIENT, networkSpan.tags().get(Tags.SPAN_KIND.getKey()));
        Assert.assertEquals(TracingCallFactory.COMPONENT_NAME, networkSpan.tags().get(Tags.COMPONENT.getKey()));
        Assert.assertEquals(301, networkSpan.tags().get(Tags.HTTP_STATUS.getKey()));
        Assert.assertEquals("GET", networkSpan.tags().get(Tags.HTTP_METHOD.getKey()));
        Assert.assertEquals(mockWebServer.url("foo").toString(), networkSpan.tags().get(Tags.HTTP_URL.getKey()));
        Assert.assertEquals(TracingCallFactory.COMPONENT_NAME, networkSpan.tags().get(Tags.COMPONENT.getKey()));
        Assert.assertEquals(mockWebServer.getPort(), networkSpan.tags().get(Tags.PEER_PORT.getKey()));
        Assert.assertEquals(ipv4ToInt("127.0.0.1"), networkSpan.tags().get(Tags.PEER_HOST_IPV4.getKey()));
        Assert.assertEquals("localhost", networkSpan.tags().get(Tags.PEER_HOSTNAME.getKey()));

        networkSpan = mockSpans.get(1);
        Assert.assertEquals(8, networkSpan.tags().size());
        Assert.assertEquals(Tags.SPAN_KIND_CLIENT, networkSpan.tags().get(Tags.SPAN_KIND.getKey()));
        Assert.assertEquals(TracingCallFactory.COMPONENT_NAME, networkSpan.tags().get(Tags.COMPONENT.getKey()));
        Assert.assertEquals(200, networkSpan.tags().get(Tags.HTTP_STATUS.getKey()));
        Assert.assertEquals("GET", networkSpan.tags().get(Tags.HTTP_METHOD.getKey()));
        Assert.assertEquals(mockWebServer.url("redirect").toString(), networkSpan.tags().get(Tags.HTTP_URL.getKey()));
        Assert.assertEquals(mockWebServer.getPort(), networkSpan.tags().get(Tags.PEER_PORT.getKey()));
        Assert.assertEquals(ipv4ToInt("127.0.0.1"), networkSpan.tags().get(Tags.PEER_HOST_IPV4.getKey()));
        Assert.assertEquals("localhost", networkSpan.tags().get(Tags.PEER_HOSTNAME.getKey()));
    }

    protected void assertLocalSpan(List<MockSpan> mockSpans) {
        MockSpan localSpan = mockSpans.get(mockSpans.size() - 1);
        Assert.assertNotNull(localSpan.tags().get(Tags.COMPONENT.getKey()));

        if (mockSpans.size() > 1) {
            MockSpan firstNetworkSpan = mockSpans.get(mockSpans.size() - 1);
            Assert.assertEquals(localSpan.context().traceId(), firstNetworkSpan.context().traceId());
            Assert.assertEquals(localSpan.context().spanId(), firstNetworkSpan.context().spanId());

            Assert.assertTrue(localSpan.finishMicros() - localSpan.startMicros()
                    >= firstNetworkSpan.finishMicros() - firstNetworkSpan.startMicros());
        }
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
