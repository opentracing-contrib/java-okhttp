package io.opentracing.contrib.okhttp3;

import io.opentracing.Span;
import io.opentracing.SpanContext;

/**
 * Tag wrapper to store parent span context and user defined tags.
 *
 * @author Pavol Loffay
 */
public class TagWrapper {

    private SpanContext parentSpanContext;
    private Span span;

    private Object tag;

    /**
     * @param tag user tag
     */
    public TagWrapper(Object tag) {
        this.tag = tag;
    }

    /**
     * @param parentSpanContext parent span context. Created client span will be
     * {@link io.opentracing.References#CHILD_OF} of given context.
     */
    public TagWrapper(SpanContext parentSpanContext) {
        this.parentSpanContext = parentSpanContext;
    }

    /**
     * @param wrapper previous wrapper
     * @param span span
     */
    TagWrapper(TagWrapper wrapper, Span span) {
        this.parentSpanContext = wrapper.parentSpanContext;
        this.span = span;
        this.tag = wrapper.tag;
    }

    public void setTag(Object tag) {
        this.tag = tag;
    }

    public Object getTag() {
        return tag;
    }

    SpanContext getParentSpanContext() {
        return parentSpanContext;
    }

    Span getSpan() {
        return span;
    }
}
