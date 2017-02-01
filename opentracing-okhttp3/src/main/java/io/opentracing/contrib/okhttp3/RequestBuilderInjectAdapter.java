package io.opentracing.contrib.okhttp3;

import java.util.Iterator;
import java.util.Map;

import io.opentracing.propagation.TextMap;
import okhttp3.Request;

/**
 * Helper class to inject span context into request headers.
 *
 * @author Pavol Loffay
 */
public class RequestBuilderInjectAdapter implements TextMap {

    private Request.Builder requestBuilder;

    public RequestBuilderInjectAdapter(Request.Builder request) {
        this.requestBuilder = request;
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        throw new UnsupportedOperationException("Should be used only with tracer#inject()");
    }

    @Override
    public void put(String key, String value) {
        requestBuilder.addHeader(key, value);
    }
}
