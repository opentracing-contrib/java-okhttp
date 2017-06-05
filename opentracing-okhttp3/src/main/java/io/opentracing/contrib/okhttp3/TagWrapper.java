package io.opentracing.contrib.okhttp3;

import io.opentracing.BaseSpan;

/**
 * Tag wrapper to store parent span context and user defined tags.
 *
 * @author Pavol Loffay
 */
public class TagWrapper {
    private BaseSpan<?> span;

    private Object tag;

    /**
     * @param tag user tag
     */
    public TagWrapper(Object tag) {
        this.tag = tag;
    }

    /**
     * @param wrapper previous wrapper
     * @param span span
     */
    TagWrapper(TagWrapper wrapper, BaseSpan<?> span) {
        this.span = span;
        this.tag = wrapper.tag;
    }

    public void setTag(Object tag) {
        this.tag = tag;
    }

    public Object getTag() {
        return tag;
    }

    BaseSpan<?> getSpan() {
        return span;
    }
}
