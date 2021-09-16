/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.threadstatepropagation;

/**
 * Defines a way to restore the current thread's state. This class helps implement the third step in thread propagation
 * (see {@link ThreadStatePropagationStarter}), where it is assumed that the caller is still on the desired destination
 * thread before calling this class.
 */
@FunctionalInterface
public interface ThreadStateRestorable {
    void restoreToCurrentThread();

    /** Returns a ThreadStateRestorable that does nothing. */
    static ThreadStateRestorable doNothing() {
        return ThreadStateRestorables.doNothing();
    }
}


class ThreadStateRestorables {
    private ThreadStateRestorables() {}

    public static ThreadStateRestorable doNothing() {
        return DO_NOTHING;
    }

    private static final ThreadStateRestorable DO_NOTHING = () -> {
    };
}
