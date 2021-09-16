/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.threadstatepropagation;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The first step in propagating thread state from an origin thread to a destination thread:<br>
 * 1. ThreadStatePropagationStarter -- read the origin Thread's state and use that to create a way to make and bind new
 * thread state to the destination thread.<br>
 * 2. ThreadStateBindable -- bind the new thread state to the destination thread and create a way to restore its
 * original state. (Note: this assumes that the client has jumped from the origin thread to the destination thread
 * between steps 1 and 2. How that happens is left up to the client/is the responsibility of the client.)<br>
 * 3. ThreadStateRestorable -- restore the destination thread's original state.
 * 
 * "Thread state" means some sort of state/data specific to a Thread. This usually means a ThreadLocal or some similar
 * concept. Propagating state from one thread to another is a useful thing to do in multithreaded code. E.g. say you're
 * running a request/response server. Suppose you generate a request id on entry to the server and want to insert that
 * id into all of your log statements. One way to do that is to pass the request id around through every function call
 * and explicitly include it in every log statement. Another way is to store the id in some sort of ThreadLocal or
 * similar concept and then retrieve the id at every logging site (either manually or automatically, say through log4j
 * 2's ThreadContext). The ThreadLocal option can be more convenient, but what happens when you want to run
 * multi-threaded code? When you jump to a new thread, you want that request id to jump with you. That's where thread
 * state propagation comes in handy.
 */
@FunctionalInterface
public interface ThreadStatePropagationStarter {
    /** Reads the current Thread's state and creates a way to make and bind new Thread state to a destination Thread. */
    ThreadStateBindable createBindableFromCurrentThread();

    /**
     * Returns an ThreadStatePropagationStarter does nothing: it returns a do-nothing ThreadStateBindable that simply
     * returns a do-nothing ThreadStateRestorable.
     */
    static ThreadStatePropagationStarter doNothing() {
        return ThreadStatePropagationStarters.doNothing();
    }

    /**
     * Creates one of the simplest mechanisms for propagating thread state: reading and setting states using the given
     * stateManager. The starter will get the state-to-propagate from the thread it's running on (via
     * ThreadStatePropagationStarter), expects the caller to travel to the destination thread (done separately by the
     * client), get the state-to-restore from the thread it's running on (via part 1 of ThreadStateBindable), sets the
     * state to the state-to-propagate value it read before on the thread it's running on (via part 2 of
     * ThreadStateBindable), and then sets the state to the state-to-restore value it read before on the thread it's
     * running on (via ThreadStateRestorable).
     */
    static ThreadStatePropagationStarter simplyGettingAndSettingState(ThreadStateManager<?> stateManager) {
        return ThreadStatePropagationStarters.simplyGettingAndSettingState(stateManager);
    }

    /**
     * Creates a composite adorners that runs each adorner in the list in order. The response from the returned adorner
     * is a BeforeRunnableAction that runs each adorner's action in order. The response from the BeforeRunnableAction is
     * an AfterRunnableAction that runs each adorner's BeforeRunnableAction in reverse order. At any point iterating
     * through each adorner, before action, or after action; if any part of that iteration throws an exception, the
     * iteration is halted and the exception is propagated.
     */
    static ThreadStatePropagationStarter composite(List<ThreadStatePropagationStarter> starters) {
        return ThreadStatePropagationStarters.composite(starters);
    }

    /**
     * Creates a (mostly) fault-tolerant adorner that decorates another adorner. <br>
     * * If any Throwable is thrown during the adornment-creation process, it's suppressed and passed to the
     * failureObserver, while a do-nothing BeforeRunnableAction is returned.<br>
     * * If the adornment-creation process succeeds, then the underlying BeforeRunnableAction it returns is run. If the
     * action throws an exception, it's suppressed and passed to the failureObserver, while a do-nothing
     * AfterRunnableAction is returned.<br>
     * * If the underlying BeforeRunnableAction succeeds, then the underlying AfterRunnableAction it returns is run. If
     * the action throws an exception, it's suppressed and passed to the failureObserver.<br>
     * * If the failureObserver itself throws an exception, then that exception is propagated as is. That's where the
     * "mostly" comes from in the name. This is intentional, as it gives you a way to re-throw exceptions that you
     * didn't want caught (e.g. Errors). That said, if you want something truly fault tolerant, then see
     * {@link PropagationFailureObserver#faultSwallowing(PropagationFailureObserver)}.
     */
    static ThreadStatePropagationStarter mostlyFaultTolerant(ThreadStatePropagationStarter decorated,
            PropagationFailureObserver failureObserver) {
        return ThreadStatePropagationStarters.mostlyFaultTolerant(decorated, failureObserver);
    }

    /**
     * A utility method to create a {@link #composite(List)} adorner that decorates a list of
     * {@link #mostlyFaultTolerant(RunnableAdorner, PropagationFailureObserver)}s.
     */
    static ThreadStatePropagationStarter compositeOfMostlyFaultTolerant(List<ThreadStatePropagationStarter> starters,
            PropagationFailureObserver failureObserver) {
        Objects.requireNonNull(failureObserver);
        List<ThreadStatePropagationStarter> tolerants = starters.stream()
                .map(starter -> mostlyFaultTolerant(starter, failureObserver))
                .collect(Collectors.toList());
        return composite(tolerants);
    }
}


class ThreadStatePropagationStarters {
    private ThreadStatePropagationStarters() {}

    public static ThreadStatePropagationStarter doNothing() {
        return DO_NOTHING;
    }

    private static final ThreadStatePropagationStarter DO_NOTHING = () -> ThreadStateBindable.doNothing();

    static <T> ThreadStatePropagationStarter simplyGettingAndSettingState(ThreadStateManager<T> stateManager) {
        Objects.requireNonNull(stateManager);
        return () -> {
            T stateToPropagate = stateManager.getCurrentThreadState();
            return () -> bind(stateManager, stateToPropagate);
        };
    }

    private static <T> ThreadStateRestorable bind(ThreadStateManager<T> stateManager, T stateToBind) {
        T stateToRestore = stateManager.getCurrentThreadState();
        stateManager.setCurrentThreadState(stateToBind);
        return () -> stateManager.setCurrentThreadState(stateToRestore);
    }

    public static ThreadStatePropagationStarter composite(List<ThreadStatePropagationStarter> starters) {
        return new CompositeThreadStatePropagationStarter(starters);
    }

    private static class CompositeThreadStatePropagationStarter implements ThreadStatePropagationStarter {
        private final List<ThreadStatePropagationStarter> starters;

        private CompositeThreadStatePropagationStarter(List<ThreadStatePropagationStarter> starters) {
            this.starters = List.copyOf(starters);
        }

        @Override
        public ThreadStateBindable createBindableFromCurrentThread() {
            List<ThreadStateBindable> bindables = starters.stream()
                    .map(starter -> starter.createBindableFromCurrentThread())
                    .collect(Collectors.toList());
            return compositeBindable(bindables);
        }

        private static ThreadStateBindable compositeBindable(List<ThreadStateBindable> bindables) {
            return () -> {
                List<ThreadStateRestorable> restorables = bindables.stream()
                        .map(bindable -> bindable.bindToCurrentThread())
                        .collect(Collectors.toList());
                Collections.reverse(restorables);
                return compositeRestorable(restorables);
            };
        }

        private static ThreadStateRestorable compositeRestorable(List<ThreadStateRestorable> restorables) {
            return () -> {
                restorables.stream().forEach(restorable -> restorable.restoreToCurrentThread());
            };
        }
    }

    static ThreadStatePropagationStarter mostlyFaultTolerant(ThreadStatePropagationStarter decorated,
            PropagationFailureObserver failureObserver) {
        return new MostlyFaultTolerantThreadStatePropagationStarter(decorated, failureObserver);
    }

    private static class MostlyFaultTolerantThreadStatePropagationStarter implements ThreadStatePropagationStarter {
        private final ThreadStatePropagationStarter decorated;
        private final PropagationFailureObserver failureObserver;

        private MostlyFaultTolerantThreadStatePropagationStarter(ThreadStatePropagationStarter decorated,
                PropagationFailureObserver failureObserver) {
            this.decorated = Objects.requireNonNull(decorated);
            this.failureObserver = Objects.requireNonNull(failureObserver);
        }

        @Override
        public ThreadStateBindable createBindableFromCurrentThread() {
            try {
                ThreadStateBindable bindable = decorated.createBindableFromCurrentThread();
                return mostlyFaultTolerantBindable(bindable);
            } catch (Throwable t) {
                failureObserver.observe(t);
                return ThreadStateBindable.doNothing();
            }
        }

        private ThreadStateBindable mostlyFaultTolerantBindable(ThreadStateBindable decoratedBindable) {
            return () -> {
                try {
                    ThreadStateRestorable restorable = decoratedBindable.bindToCurrentThread();
                    return mostlyFaultTolerantRestorable(restorable);
                } catch (Throwable t) {
                    failureObserver.observe(t);
                    return ThreadStateRestorable.doNothing();
                }
            };
        }

        private ThreadStateRestorable mostlyFaultTolerantRestorable(ThreadStateRestorable decoratedRestorable) {
            return () -> {
                try {
                    decoratedRestorable.restoreToCurrentThread();
                } catch (Throwable t) {
                    failureObserver.observe(t);
                }
            };
        }
    }
}
