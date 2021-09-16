package io.github.graydavid.threadstatepropagation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

public class ThreadStatePropagationStarterTest {
    private final ThreadStatePropagationStarter starter = mock(ThreadStatePropagationStarter.class);
    private final ThreadStateBindable bindable = mock(ThreadStateBindable.class);
    private final ThreadStateRestorable restorable = mock(ThreadStateRestorable.class);
    private final PropagationFailureObserver observer = mock(PropagationFailureObserver.class);

    @BeforeEach
    public void setUp() {
        when(starter.createBindableFromCurrentThread()).thenReturn(bindable);
        when(bindable.bindToCurrentThread()).thenReturn(restorable);
    }

    @Test
    public void doNothingDoesNothingAndReturnsBindableThatDoesNothingAndReturnsRestorableThatDoesNothing() {
        ThreadStatePropagationStarter.doNothing()
                .createBindableFromCurrentThread()
                .bindToCurrentThread()
                .restoreToCurrentThread();
    }

    @Test
    public void simplyGettingAndSettingStateThrowsExceptionGivenNullStateManager() {
        assertThrows(NullPointerException.class,
                () -> ThreadStatePropagationStarter.simplyGettingAndSettingState(null));
    }

    @Test
    public void simplyGettingAndSettingFollowsReadOverwriteRestoreCycle() {
        Integer originThreadState = 34;
        IntegerThreadStateManager stateManager = new IntegerThreadStateManager();
        stateManager.setCurrentThreadState(originThreadState);
        ThreadStatePropagationStarter starter = ThreadStatePropagationStarter
                .simplyGettingAndSettingState(stateManager);

        assertThat(stateManager.getCurrentThreadState(), is(originThreadState));
        ThreadStateBindable bindable = starter.createBindableFromCurrentThread();

        assertThat(stateManager.getCurrentThreadState(), is(originThreadState));
        // Assume here that the user jumps to the destination thread
        Integer destinationThreadOriginalState = 93;
        stateManager.setCurrentThreadState(destinationThreadOriginalState);
        ThreadStateRestorable restorable = bindable.bindToCurrentThread();

        assertThat(stateManager.getCurrentThreadState(), is(originThreadState));
        restorable.restoreToCurrentThread();

        assertThat(stateManager.getCurrentThreadState(), is(destinationThreadOriginalState));
    }

    private static class IntegerThreadStateManager implements ThreadStateManager<Integer> {
        private Integer integer;

        @Override
        public Integer getCurrentThreadState() {
            return integer;
        }

        @Override
        public void setCurrentThreadState(Integer state) {
            integer = state;
        }
    }

    @Test
    public void compositeThrowsExceptionGivenNullList() {
        assertThrows(NullPointerException.class, () -> ThreadStatePropagationStarter.composite(null));
    }

    @Test
    public void compositeThrowsExceptionGivenNullElementInList() {
        assertThrows(NullPointerException.class,
                () -> ThreadStatePropagationStarter.composite(Arrays.asList((ThreadStatePropagationStarter) null)));
    }

    @Test
    public void compositeAllowsEmptyListsAndDoesNothing() {
        ThreadStatePropagationStarter.composite(List.of())
                .createBindableFromCurrentThread()
                .bindToCurrentThread()
                .restoreToCurrentThread();
    }

    @Test
    public void compositeRunsThroughSingleStartBindRestoreCycle() {
        ThreadStatePropagationStarter composite = ThreadStatePropagationStarter.composite(List.of(starter));

        verifyNoInteractions(starter, bindable, restorable);
        ThreadStateBindable compositeBindable = composite.createBindableFromCurrentThread();

        verify(starter).createBindableFromCurrentThread();
        verifyNoInteractions(bindable, restorable);
        ThreadStateRestorable compositeRestorable = compositeBindable.bindToCurrentThread();

        verify(bindable).bindToCurrentThread();
        verifyNoInteractions(restorable);
        compositeRestorable.restoreToCurrentThread();

        verify(restorable).restoreToCurrentThread();
    }

    @Test
    public void compositeRunsThroughMultipleStartBindRestoreCycleDoingRestoresInReverse() {
        ThreadStatePropagationStarter starter2 = mock(ThreadStatePropagationStarter.class);
        ThreadStateBindable bindable2 = mock(ThreadStateBindable.class);
        ThreadStateRestorable restorable2 = mock(ThreadStateRestorable.class);
        when(starter2.createBindableFromCurrentThread()).thenReturn(bindable2);
        when(bindable2.bindToCurrentThread()).thenReturn(restorable2);

        ThreadStatePropagationStarter.composite(List.of(starter, starter2))
                .createBindableFromCurrentThread()
                .bindToCurrentThread()
                .restoreToCurrentThread();

        InOrder inOrder = Mockito.inOrder(starter, starter2, bindable, bindable2, restorable, restorable2);
        inOrder.verify(starter).createBindableFromCurrentThread();
        inOrder.verify(starter2).createBindableFromCurrentThread();
        inOrder.verify(bindable).bindToCurrentThread();
        inOrder.verify(bindable2).bindToCurrentThread();
        inOrder.verify(restorable2).restoreToCurrentThread();
        inOrder.verify(restorable).restoreToCurrentThread();
    }

    @Test
    public void mostlyFaultTolerantThrowsExceptionGivenArguments() {
        assertThrows(NullPointerException.class,
                () -> ThreadStatePropagationStarter.mostlyFaultTolerant(null, observer));
        assertThrows(NullPointerException.class,
                () -> ThreadStatePropagationStarter.mostlyFaultTolerant(starter, null));
    }

    @Test
    public void mostlyFaultTolerantRunsThroughSingleStartBindRestoreCycleOnSuccess() {
        ThreadStatePropagationStarter tolerant = ThreadStatePropagationStarter.mostlyFaultTolerant(starter, observer);

        verifyNoInteractions(starter, bindable, restorable);
        ThreadStateBindable compositeBindable = tolerant.createBindableFromCurrentThread();

        verify(starter).createBindableFromCurrentThread();
        verifyNoInteractions(bindable, restorable);
        ThreadStateRestorable compositeRestorable = compositeBindable.bindToCurrentThread();

        verify(bindable).bindToCurrentThread();
        verifyNoInteractions(restorable);
        compositeRestorable.restoreToCurrentThread();

        verify(restorable).restoreToCurrentThread();
        verifyNoInteractions(observer);
    }

    @Test
    public void mostlyFaultTolerantObservesExceptionsThrownByDecoratedRestorable() {
        Error restorableError = new Error();
        doThrow(restorableError).when(restorable).restoreToCurrentThread();

        ThreadStatePropagationStarter.mostlyFaultTolerant(starter, observer)
                .createBindableFromCurrentThread()
                .bindToCurrentThread()
                .restoreToCurrentThread();

        InOrder inOrder = Mockito.inOrder(starter, bindable, observer);
        inOrder.verify(starter).createBindableFromCurrentThread();
        inOrder.verify(bindable).bindToCurrentThread();
        inOrder.verify(observer).observe(restorableError);
    }

    @Test
    public void mostlyFaultTolerantObservesExceptionsThrownByDecoratedBindable() {
        Error bindableError = new Error();
        when(bindable.bindToCurrentThread()).thenThrow(bindableError);

        ThreadStatePropagationStarter.mostlyFaultTolerant(starter, observer)
                .createBindableFromCurrentThread()
                .bindToCurrentThread()
                .restoreToCurrentThread();

        InOrder inOrder = Mockito.inOrder(starter, observer);
        inOrder.verify(starter).createBindableFromCurrentThread();
        inOrder.verify(observer).observe(bindableError);
        verifyNoInteractions(restorable);
    }

    @Test
    public void mostlyFaultTolerantObservesExceptionsThrownByDecoratedStarter() {
        Error starterError = new Error();
        when(starter.createBindableFromCurrentThread()).thenThrow(starterError);

        ThreadStatePropagationStarter.mostlyFaultTolerant(starter, observer)
                .createBindableFromCurrentThread()
                .bindToCurrentThread()
                .restoreToCurrentThread();

        verify(observer).observe(starterError);
        verifyNoInteractions(bindable, restorable);
    }

    @Test
    public void compositeOfMostlyFaultTolerantThrowsExceptionGivenNullArguments() {
        assertThrows(NullPointerException.class,
                () -> ThreadStatePropagationStarter.compositeOfMostlyFaultTolerant(null, observer));
        assertThrows(NullPointerException.class,
                () -> ThreadStatePropagationStarter.compositeOfMostlyFaultTolerant(List.of(), null));
    }

    @Test
    public void compositeOfMostlyFaultTolerantThrowsExceptionGivenNullElementInList() {
        assertThrows(NullPointerException.class, () -> ThreadStatePropagationStarter
                .compositeOfMostlyFaultTolerant(Arrays.asList((ThreadStatePropagationStarter) null), observer));
    }

    @Test
    public void compositeOfMostlyFaultTolerantAllowsEmptyListsAndDoesNothing() {
        ThreadStatePropagationStarter.compositeOfMostlyFaultTolerant(List.of(), observer)
                .createBindableFromCurrentThread()
                .bindToCurrentThread()
                .restoreToCurrentThread();
    }

    @Test
    public void compositeOfMostlyFaultTolerantCreatesACompositeListOfMostlyFaultTolerantStarters() {
        // One starter throws...
        Error starterError = new Error();
        when(starter.createBindableFromCurrentThread()).thenThrow(starterError);
        // ... and the other doesn't
        ThreadStatePropagationStarter starter2 = mock(ThreadStatePropagationStarter.class);
        ThreadStateBindable bindable2 = mock(ThreadStateBindable.class);
        ThreadStateRestorable restorable2 = mock(ThreadStateRestorable.class);
        when(starter2.createBindableFromCurrentThread()).thenReturn(bindable2);
        when(bindable2.bindToCurrentThread()).thenReturn(restorable2);

        ThreadStatePropagationStarter.compositeOfMostlyFaultTolerant(List.of(starter, starter2), observer)
                .createBindableFromCurrentThread()
                .bindToCurrentThread()
                .restoreToCurrentThread();

        InOrder inOrder = Mockito.inOrder(starter, starter2, bindable2, restorable2, observer);
        inOrder.verify(starter).createBindableFromCurrentThread();
        inOrder.verify(observer).observe(starterError);
        inOrder.verify(starter2).createBindableFromCurrentThread();
        inOrder.verify(bindable2).bindToCurrentThread();
        inOrder.verify(restorable2).restoreToCurrentThread();
        verifyNoInteractions(bindable, restorable);
    }
}
