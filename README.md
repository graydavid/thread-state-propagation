# Thread-State-Propagation

Thread-State-Propagation is a simple java library that defines helpful interfaces for propagating state from one Thread to another. 

"Thread state" means some sort of state/data specific to a Thread. This usually means a ThreadLocal or some similar concept. Propagating state from one thread to another is a useful thing to do in multithreaded code. E.g. say you're running a request/response server. Suppose you generate a request id on entry to the server and want to insert that id into all of your log statements. One way to do that is to pass the request id around through every function call and explicitly include it in every log statement. Another way is to store the id in some sort of ThreadLocal or similar concept and then retrieve the id at every logging site (either manually or automatically, say through log4j 2's ThreadContext). The ThreadLocal option can be more convenient, but what happens when you want to run multi-threaded code? When you jump to a new thread, you want that request id to jump with you. That's where Thread-State-Propagation comes in handy.

Thread-State-Propagation only focuses on providing the pieces for thread state propagation. One of the most common use cases for doing this is while executing code via an Executor. (To avoid bringing in extra dependencies) Thread-State-Propagation delegates defining these utilities to the `thread-state-propagation-runnable-adorner` project. 

Thread-State-Propagation uses three steps to define propagation from an origin thread to a destination thread:
1. ThreadStatePropagationStarter -- read the origin Thread's state and use that to create a way to make and bind new thread state to the destination thread.
2. ThreadStateBindable -- bind the new thread state to the destination thread and create a way to restore its original state. (Note: this assumes that the client has jumped from the origin thread to the destination thread between steps 1 and 2. How that happens is left up to the client/is the responsibility of the client.)
3. ThreadStateRestorable -- restore the destination thread's original state.

(Note: Thread-State-Propagation includes ways to define composite and fault-tolerant variations of these classes.)

In addition, Thread-State-Propagation also defines a helpful ThreadStateManager interface. ThreadStateManager defines a typical interface for getting and setting the current Thread's state. Using this definition, Thread-State-Propagation also provides the most common type of ThreadStatePropagationStarter: one that uses simple gets and sets to read and transfer state. Sometimes, thread propagation can be more complicated than that (e.g. maybe you need to create a new object as a part of propagation instead of simply transferring state), but a lot of use cases do match this simple transfer process.

## Adding this project to your build

This project follows [semantic versioning](https://semver.org/). It's currently available via the Maven snapshot repository only as version 0.0.1-SNAPSHOT. The "0.0.1" part is because it's still in development, waiting for feedback or sufficient usage until its first official release. The "SNAPSHOT" part is because no one has requested a stable, non-SNAPSHOT development version. If you need a non-SNAPSHOT development version, feel free to reach out, and I can build this project into the Maven central repository.

## Usage

This project requires JDK version 11 or higher.

The below examples show how to use this project's major utilities.

### Fully-defined ThreadStatePropagationStarter

This example shows how to create a ThreadStatePropagationStarter that uses ThreadStateManager to transfer state. This example is only illustrative, since Thread-State-Propagation already provides much-more-robust, built-in support for this usecase (as will be demonstrated in the example following this one).

```java
ThreadStateManager<Integer> stateManager = ...; //How this is done is up to clients
ThreadStatePropagationStarter starter = () -> {
    //In ThreadStatePropagationStarter: Read the origin thread's state
    Integer originThreadState = stateManager.getCurrentThreadState();
    return () -> {
        //In ThreadStateBindable: Save the destination thread's original state...
        Integer destinationOriginalThreadState = stateManager.getCurrentThreadState();
        //... and Propagate the origin thread's state
        stateManager.setCurrentThreadState(originThreadState);
        return () -> {
            //In ThreadStateRestorable: Restore the destination thread's state
            stateManager.setCurrentThreadState(destinationOriginalThreadState);
        };
    };
};
```

Although this example is a simple starter process, Thread-State-Propagation also provides a way to define composite ThreadStatePropagationStarters based on a list of ThreadStatePropagationStarters (i.e. the composite design pattern) as well as fault-tolerant ThreadStatePropagationStarters (which can suppress failures).

### ThreadStatePropagationStarter#simplyGettingAndSettingState

This example shows how to create a ThreadStatePropagationStarter using the simplyGettingAndSettingState method that matches the functionality provided in the previous example.


```java
ThreadStateManager<Integer> stateManager = ...; //How this is done is up to clients
ThreadStatePropagationStarter starter = ThreadStatePropagationStarter
        .simplyGettingAndSettingState(stateManager);
```

## Contributions

Contributions are welcome! See the [graydavid-parent](https://github.com/graydavid/graydavid-parent) project for details.