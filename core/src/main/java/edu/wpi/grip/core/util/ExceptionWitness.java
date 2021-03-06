package edu.wpi.grip.core.util;

import com.google.common.eventbus.EventBus;
import edu.wpi.grip.core.events.ExceptionClearedEvent;
import edu.wpi.grip.core.events.ExceptionEvent;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Witnesses and reports exception. <b>This class is not suitable to handle {@link Error Errors}.</b><br />
 * {@link #flagException} should be used to flag the witness that an error has has occurred.
 * This will post an {@link ExceptionEvent} to the {@link EventBus}.
 * <br />
 * <br />
 * Example Usage:
 * <blockquote>
 * <pre>
 * {@code
 * while(true){
 *     try {
 *          // Do something that may throw an exception
 *          witness.clearException();
 *     } catch (Exception e) {
 *        witness.flagException(e, "There was a problem in this while loop");
 *     }
 * }
 * }
 * </pre>
 * <blockquote/>
 */
public final class ExceptionWitness {
    private final EventBus eventBus;
    private final Object origin;
    private final AtomicBoolean isExceptionState = new AtomicBoolean(false);

    public ExceptionWitness(final EventBus eventBus, final Object origin) {
        this.eventBus = checkNotNull(eventBus, "The event bus can not be null");
        this.origin = checkNotNull(origin, "The origin can not be null");
    }

    /**
     * Indicates to the witness that an exception has occurred. This will also post an {@link ExceptionEvent} to the {@link EventBus}
     *
     * @param exception The exception that this is reporting
     * @param message   Any additional details that should be associated with this message.
     */
    public final void flagException(final Exception exception, final String message) {
        isExceptionState.set(true);
        this.eventBus.post(new ExceptionEvent(origin, exception, message));
    }

    /**
     * @see #flagException(Exception, String)
     */
    public final void flagException(Exception exception) {
        flagException(exception, null);
    }

    /**
     * Indicate that there isn't currently an exception.
     * <p>
     * Clears the exception state and posts an {@link ExceptionClearedEvent}.
     * This method can be called every time that there isn't an exception as an {@link ExceptionClearedEvent} will
     * only be posted when there was previously an exception flagged.
     */
    public final void clearException() {
        // Only post an ExceptionClearedEvent if there was an exception before
        if (isExceptionState.compareAndSet(true, false)) {
            this.eventBus.post(new ExceptionClearedEvent(origin));
        }
    }

    /**
     * @return true if an exception has been flagged and not cleared
     */
    public final boolean isException() {
        return isExceptionState.get();
    }
}
