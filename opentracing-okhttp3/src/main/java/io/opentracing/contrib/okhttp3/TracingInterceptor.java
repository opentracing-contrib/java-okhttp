package io.opentracing.contrib.okhttp3;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Okhttp interceptor to trace client requests. Interceptor adds span context into outgoing requests.
 * By default span operation name is set to HTTP method.
 *
 * <p>Initialization via {@link TracingInterceptor#addTracing(OkHttpClient.Builder, Tracer, List)}
 *
 * <p>or instantiate the interceptor and add it to {@link OkHttpClient.Builder#addInterceptor(Interceptor)} and
 * {@link OkHttpClient.Builder#addNetworkInterceptor(Interceptor)}.
 *
 * <p> Created span is by default in a new trace,
 * if you want to connect it with a parent span, then add parent {@link TagWrapper} with
 * parent {@link io.opentracing.SpanContext} to {@link Request.Builder#tag(Object)}.
 *
 * @author Pavol Loffay
 */
public class TracingInterceptor implements Interceptor {

    private static final Logger log = Logger.getLogger(TracingInterceptor.class.getName());

    private Tracer tracer;
    private List<SpanDecorator> decorators;

    /**
     * Create tracing interceptor. Interceptor has to be added to {@link OkHttpClient.Builder#addInterceptor(Interceptor)}
     * and {@link OkHttpClient.Builder#addNetworkInterceptor(Interceptor)}.
     *
     * @param tracer tracer
     * @param decorators decorators
     */
    public TracingInterceptor(Tracer tracer, List<SpanDecorator> decorators) {
        this.tracer = tracer;
        this.decorators = new ArrayList<>(decorators);
    }

    /**
     * Add tracing interceptors to client builder.
     *
     * @param okBuilder client builder
     * @param tracer tracer
     * @param decorators span decorators
     * @return client builder with added tracing interceptor
     */
    public static OkHttpClient.Builder addTracing(OkHttpClient.Builder okBuilder,
                                                  Tracer tracer, List<SpanDecorator> decorators) {

        TracingInterceptor tracingInterceptor = new TracingInterceptor(tracer, decorators);
        return okBuilder.addInterceptor(tracingInterceptor)
                    .addNetworkInterceptor(tracingInterceptor);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Response response;

        // application interceptor?
        if (chain.connection() == null) {
            Tracer.SpanBuilder spanBuilder = tracer.buildSpan(chain.request().method())
                    .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT);

            Object tag = chain.request().tag();
            TagWrapper tagWrapper;
            if (tag instanceof TagWrapper) {
                tagWrapper = (TagWrapper) tag;
                if (tagWrapper.getParentSpanContext() != null) {
                    spanBuilder.asChildOf(tagWrapper.getParentSpanContext());
                }
            } else {
                tagWrapper = new TagWrapper(tag);
            }

            Span span = spanBuilder.start();

            for (SpanDecorator spanDecorator: decorators) {
                spanDecorator.onRequest(chain.request(), span);
            }

            Request.Builder requestBuilder = chain.request().newBuilder();
            tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, new RequestBuilderInjectAdapter(requestBuilder));

            requestBuilder.tag(new TagWrapper(tagWrapper, span));

            try {
                response = chain.proceed(requestBuilder.build());

                for (SpanDecorator spanDecorator: decorators) {
                    spanDecorator.onResponse(response, span);
                }
            } catch (Throwable ex) {
                for (SpanDecorator spanDecorator: decorators) {
                    spanDecorator.onError(ex, span);
                }
                throw ex;
            } finally {
                span.finish();
            }
        } else {
            response = chain.proceed(chain.request());
            Object tag = response.request().tag();
            if (tag instanceof TagWrapper) {
                TagWrapper tagWrapper = (TagWrapper) tag;
                for (SpanDecorator spanDecorator: decorators) {
                    spanDecorator.onNetworkResponse(chain.connection(), response, tagWrapper.getSpan());
                }
            } else {
                log.severe("tag is null or not an instance of TagWrapper, skipping decorator onNetworkResponse()");
            }
        }

        return response;
    }

}
