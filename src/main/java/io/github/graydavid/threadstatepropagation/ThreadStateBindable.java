/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.threadstatepropagation;

/**
 * Defines a way to bind thread state to the current thread. This class helps implement the second step in thread
 * propagation (see {@link ThreadStatePropagationStarter}), where it is assumed that the caller has already traveled to
 * the desired destination thread before calling this class.
 */
@FunctionalInterface
public interface ThreadStateBindable {
    ThreadStateRestorable bindToCurrentThread();

    /** Returns a ThreadStateBindable that does nothing: it only returns a do-nothing ThreadStateRestorable. */
    static ThreadStateBindable doNothing() {
        return ThreadStateBindables.doNothing();
    }
}


class ThreadStateBindables {
    private ThreadStateBindables() {}

    public static ThreadStateBindable doNothing() {
        return DO_NOTHING;
    }

    private static final ThreadStateBindable DO_NOTHING = () -> ThreadStateRestorable.doNothing();
}
