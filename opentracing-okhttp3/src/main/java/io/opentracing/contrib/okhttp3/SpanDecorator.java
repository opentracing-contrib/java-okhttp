package io.opentracing.contrib.okhttp3;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Inet4Address;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import okhttp3.Connection;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Span decorator to add tags, logs and operation name.
 *
 * @author Pavol Loffay
 */
public interface SpanDecorator {

    /**
     * Decorate span before a request is made.
     *
     * @param request request
     * @param span span
     */
    void onRequest(Request request, Span span);

    /**
     * Decorate span after request is made.
     *
     * @param response response
     * @param span span
     */
    void onResponse(Response response, Span span);

    /**
     * Decorate span on an error e.g. {@link java.net.UnknownHostException} or any exception in interceptor.
     *
     * @param throwable exception
     * @param span span
     */
    void onError(Throwable throwable, Span span);

    /**
     * This is invoked after {@link okhttp3.Interceptor.Chain#proceed(Request)} in network interceptor.
     * In this method it is possible to capture server address, log redirects etc.
     *
     * @param connection connection
     * @param response response
     * @param span span
     */
    void onNetworkResponse(Connection connection, Response response, Span span);

    /**
     * Decorator which adds standard HTTP and peer tags to the span.
     *
     * <p> On error it adds {@link Tags#ERROR} with log representing exception and
     * on redirects adds log entries with peer tags.
     *
     */
    SpanDecorator STANDARD_TAGS = new SpanDecorator() {
        @Override
        public void onRequest(Request request, Span span) {
            Tags.COMPONENT.set(span, "java-okhttp");
            Tags.HTTP_METHOD.set(span, request.method());
            Tags.HTTP_URL.set(span, request.url().toString());
        }

        @Override
        public void onResponse(Response response, Span span) {
            Tags.HTTP_STATUS.set(span, response.code());
        }

        @Override
        public void onError(Throwable throwable, Span span) {
            Tags.ERROR.set(span, Boolean.TRUE);
            span.log(errorLogs(throwable));
        }

        @Override
        public void onNetworkResponse(Connection connection, Response response, Span span) {
            if (response.isRedirect()) {
                Map<String, Object> redirectLogs = new HashMap<>(4);
                redirectLogs.put("event", "redirect");
                redirectLogs.put(Tags.PEER_HOSTNAME.getKey(), connection.socket().getInetAddress().getHostName());
                redirectLogs.put(Tags.PEER_PORT.getKey(), (short)connection.socket().getPort());

                if (connection.socket().getInetAddress() instanceof Inet4Address) {
                    byte[] address = connection.socket().getInetAddress().getAddress();
                    redirectLogs.put(Tags.PEER_HOST_IPV4.getKey(), ByteBuffer.wrap(address).getInt());
                } else {
                    redirectLogs.put(Tags.PEER_HOST_IPV6.getKey(), connection.socket().getInetAddress().getHostAddress());
                }

                span.log(redirectLogs);
            } else {
                Tags.PEER_HOSTNAME.set(span, connection.socket().getInetAddress().getHostName());
                Tags.PEER_PORT.set(span, connection.socket().getPort());

                if (connection.socket().getInetAddress() instanceof Inet4Address) {
                    byte[] address = connection.socket().getInetAddress().getAddress();
                    Tags.PEER_HOST_IPV4.set(span, ByteBuffer.wrap(address).getInt());
                } else {
                    Tags.PEER_HOST_IPV6.set(span, connection.socket().getInetAddress().toString());
                }
            }
        }

        protected Map<String, String> errorLogs(Throwable throwable) {
            Map<String, String> errorLogs = new HashMap<>(4);
            errorLogs.put("event", Tags.ERROR.getKey());
            errorLogs.put("error.kind", throwable.getClass().getName());

            String exMessage = throwable.getCause() != null ? throwable.getCause().getMessage() :
                    throwable.getMessage();
            errorLogs.put("message", exMessage);

            StringWriter sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));
            errorLogs.put("stack", sw.toString());

            return errorLogs;
        }
    };
}
