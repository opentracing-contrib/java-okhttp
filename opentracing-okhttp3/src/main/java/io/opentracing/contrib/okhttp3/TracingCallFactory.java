package io.opentracing.contrib.okhttp3;

import io.opentracing.Scope;
import io.opentracing.Span;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import okhttp3.Call;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * OkHttp client instrumentation.
 *
 * @author Pavol Loffay
 */
public class TracingCallFactory implements Call.Factory {
    static final String COMPONENT_NAME = "okhttp";

    private OkHttpClient okHttpClient;

    private Tracer tracer;
    private List<OkHttpClientSpanDecorator> decorators;

    public TracingCallFactory(OkHttpClient okHttpClient, Tracer tracer) {
        this(okHttpClient, tracer, Collections.singletonList(OkHttpClientSpanDecorator.STANDARD_TAGS));
    }

    public TracingCallFactory(OkHttpClient okHttpClient, Tracer tracer, List<OkHttpClientSpanDecorator> decorators) {
        this.okHttpClient = okHttpClient;
        this.tracer = tracer;
        this.decorators = new ArrayList<>(decorators);
    }

    @Override
    public Call newCall(final Request request) {
        final Span span = tracer.buildSpan(request.method())
            .withTag(Tags.COMPONENT.getKey(), COMPONENT_NAME)
            .start();
        try {
            /**
             * In case of exception network interceptor is not called
             */
            OkHttpClient.Builder okBuilder = okHttpClient.newBuilder();
            okBuilder.networkInterceptors().add(0, new NetworkInterceptor(tracer, span.context(), decorators));

            okBuilder.interceptors().add(0, new Interceptor() {
                @Override
                public Response intercept(Chain chain) throws IOException {
                    try (Scope activeInterceptorSpan = tracer.activateSpan(span)) {
                        return chain.proceed(chain.request());
                    } catch (Exception ex) {
                        for (OkHttpClientSpanDecorator spanDecorator : decorators) {
                            spanDecorator.onError(ex, span);
                        }
                        throw ex;
                    } finally {
                        span.finish();
                    }
                }
            });
            return okBuilder.build().newCall(request);
        } catch (Exception ex) {
            for (OkHttpClientSpanDecorator spanDecorator: decorators) {
                spanDecorator.onError(ex, span);
            }
            throw ex;
        }
    }

    static class NetworkInterceptor implements Interceptor {
        public SpanContext parentContext;
        public Tracer tracer;
        public List<OkHttpClientSpanDecorator> decorators;

        NetworkInterceptor(Tracer tracer, SpanContext spanContext, List<OkHttpClientSpanDecorator> decorators) {
            this.parentContext = spanContext;
            this.tracer = tracer;
            this.decorators = decorators;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Span networkSpan = tracer.buildSpan(chain.request().method())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .asChildOf(parentContext)
                .start();

            for (OkHttpClientSpanDecorator spanDecorator: decorators) {
                spanDecorator.onRequest(chain.request(), networkSpan);
            }

            Request.Builder requestBuilder = chain.request().newBuilder();
            tracer.inject(networkSpan.context(), Format.Builtin.HTTP_HEADERS, new RequestBuilderInjectAdapter(requestBuilder));

            try (Scope scope = tracer.activateSpan(networkSpan)) {
                Response response = chain.proceed(requestBuilder.build());
                for (OkHttpClientSpanDecorator spanDecorator: decorators) {
                    spanDecorator.onResponse(chain.connection(), response, networkSpan);
                }
                return response;
            } finally {
                networkSpan.finish();
            }
        }
    }
}
