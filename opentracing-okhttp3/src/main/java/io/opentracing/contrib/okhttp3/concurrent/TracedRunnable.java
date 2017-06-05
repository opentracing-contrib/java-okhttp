package io.opentracing.contrib.okhttp3.concurrent;

import io.opentracing.ActiveSpan;
import io.opentracing.NoopActiveSpanSource;

/**
 * @author Pavol Loffay
 */
class TracedRunnable implements Runnable {

    private final Runnable delegate;
    private final ActiveSpan.Continuation continuation;
    
    public TracedRunnable(Runnable delegate, ActiveSpan activeSpan) {
        this.delegate = delegate;
        this.continuation = activeSpan != null ? activeSpan.capture() : NoopActiveSpanSource.NoopContinuation
                .INSTANCE;
    }

    @Override
    public void run() {
        try (ActiveSpan activeSpan = continuation.activate()) {
            delegate.run();
        }
    }
}
