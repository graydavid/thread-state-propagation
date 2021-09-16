/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.threadstatepropagation;

/** Defines a way to interact with the current thread's state. */
public interface ThreadStateManager<T> {
    /** Retrieves the state associated with the current thread. */
    T getCurrentThreadState();

    /** Sets the state associated with the current thread. */
    void setCurrentThreadState(T state);
}
