package io.github.graydavid.threadstatepropagation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

public class PropagationFailureObserverTest {

    @Test
    public void faultSwallowingSwallowsExceptionsFromDecorated() {
        PropagationFailureObserver decorated = throwable -> {
            throw new Error();
        };
        PropagationFailureObserver observer = PropagationFailureObserver.faultSwallowing(decorated);

        observer.observe(new Throwable());
    }

    @Test
    public void faultSwallowingAllowsSuccessfulDecorated() {
        PropagationFailureObserver decorated = mock(PropagationFailureObserver.class);
        PropagationFailureObserver observer = PropagationFailureObserver.faultSwallowing(decorated);
        Error error = new Error();

        observer.observe(error);

        verify(decorated).observe(error);
    }
}
