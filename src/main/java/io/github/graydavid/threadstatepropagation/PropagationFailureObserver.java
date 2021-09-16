/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.threadstatepropagation;

/** Defines a way to observe/suppress failures encountered during the thread state propagation process. */
@FunctionalInterface
public interface PropagationFailureObserver {
    /**
     * Observes a failure in thread state propagation. Because propagation participants throw only unchecked exceptions,
     * the failure will only ever be an unchecked exception (i.e. an Error or a RuntimeException)... unless you use
     * "sneaky throws" in your participant.
     */
    void observe(Throwable throwable);

    /**
     * Creates a PropagationFailureObserver that decorates another observer, swallowing any exceptions from it. Clients
     * should be wary of using this, as it's destroying useful information... but perhaps as a last resort, that makes
     * sense. It's your call.
     */
    static PropagationFailureObserver faultSwallowing(PropagationFailureObserver decorated) {
        return PropagationFailureObservers.faultSwallowing(decorated);
    }
}


class PropagationFailureObservers {
    private PropagationFailureObservers() {}

    public static PropagationFailureObserver faultSwallowing(PropagationFailureObserver decorated) {
        return throwable -> {
            try {
                decorated.observe(throwable);
            } catch (Throwable t) {
                // Do nothing
            }
        };
    }
}
