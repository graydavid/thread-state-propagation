package io.github.graydavid.threadstatepropagation;

import org.junit.jupiter.api.Test;

public class ThreadStateBindableTest {

    @Test
    public void doNothingDoesNothingAndReturnsRestorableThatDoesNothing() {
        ThreadStateBindable.doNothing().bindToCurrentThread().restoreToCurrentThread();
    }
}
