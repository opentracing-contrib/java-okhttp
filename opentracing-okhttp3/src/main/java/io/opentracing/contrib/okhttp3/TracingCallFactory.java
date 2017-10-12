package io.opentracing.contrib.okhttp3;

import io.opentracing.Scope;
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
        Scope scope = null;
        try {
            scope = tracer.buildSpan(request.method())
                    .withTag(Tags.COMPONENT.getKey(), COMPONENT_NAME)
                    .startActive(false);

            /**
             * In case of exception network interceptor is not called
             */
            OkHttpClient.Builder okBuilder = okHttpClient.newBuilder();
            okBuilder.networkInterceptors().add(0, new NetworkInterceptor(tracer, scope.span().context(), decorators));

            final Scope finalScope = scope;
            okBuilder.interceptors().add(0, new Interceptor() {
                @Override
                public Response intercept(Chain chain) throws IOException {
                    Scope activeInterceptorSpan = tracer.scopeManager().activate(finalScope.span(), true);
                    try {
                        return chain.proceed(chain.request());
                    } catch (Exception ex) {
                        for (OkHttpClientSpanDecorator spanDecorator : decorators) {
                            spanDecorator.onError(ex, activeInterceptorSpan.span());
                        }
                        throw ex;
                    } finally {
                        activeInterceptorSpan.close();
                    }
                }
            });
            return okBuilder.build().newCall(request);
        } catch (Throwable ex) {
            for (OkHttpClientSpanDecorator spanDecorator: decorators) {
                spanDecorator.onError(ex, scope.span());
            }
            throw ex;
        } finally {
            scope.close();
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
            try (Scope networkScope = tracer.buildSpan(chain.request().method())
                        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                        .asChildOf(parentContext)
                        .startActive(true)) {

                for (OkHttpClientSpanDecorator spanDecorator: decorators) {
                    spanDecorator.onRequest(chain.request(), networkScope.span());
                }

                Request.Builder requestBuilder = chain.request().newBuilder();
                tracer.inject(networkScope.span().context(), Format.Builtin.HTTP_HEADERS, new RequestBuilderInjectAdapter(requestBuilder));
                Response response = chain.proceed(requestBuilder.build());

                for (OkHttpClientSpanDecorator spanDecorator: decorators) {
                    spanDecorator.onResponse(chain.connection(), response, networkScope.span());
                }

                return response;
            }
        }
    }
}
