package io.github.graydavid.threadstatepropagation;

import org.junit.jupiter.api.Test;

public class ThreadStateRestorableTest {

    @Test
    public void doNothingDoesNothing() {
        ThreadStateRestorable.doNothing().restoreToCurrentThread();
    }
}
