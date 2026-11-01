package hems.util;

import javafx.application.Platform;
import javafx.scene.Node;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles offloading JDBC and heavy operations from the JavaFX Application Thread.
 * Automatically manages node disabling (preventing double clicks) and dispatches
 * completion and error callbacks on the JavaFX thread.
 */
public final class AsyncExecutor {
    private static final Logger LOGGER = Logger.getLogger(AsyncExecutor.class.getName());

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread thread = new Thread(r, "hems-worker-thread");
        thread.setDaemon(true);
        return thread;
    });

    private AsyncExecutor() {}

    /**
     * Executes a background task returning a value and dispatches results to UI thread.
     */
    public static <T> void execute(Callable<T> backgroundAction,
                                   Consumer<T> onSuccess,
                                   Consumer<Throwable> onError,
                                   Node... controlsToDisable) {
        for (Node node : controlsToDisable) {
            if (node != null) node.setDisable(true);
        }

        EXECUTOR.submit(() -> {
            try {
                T result = backgroundAction.call();
                Platform.runLater(() -> {
                    try {
                        if (onSuccess != null) {
                            onSuccess.accept(result);
                        }
                    } finally {
                        for (Node node : controlsToDisable) {
                            if (node != null) node.setDisable(false);
                        }
                    }
                });
            } catch (Throwable ex) {
                LOGGER.log(Level.WARNING, "Async task encountered an exception", ex);
                Platform.runLater(() -> {
                    try {
                        if (onError != null) {
                            onError.accept(ex);
                        }
                    } finally {
                        for (Node node : controlsToDisable) {
                            if (node != null) node.setDisable(false);
                        }
                    }
                });
            }
        });
    }

    /**
     * Executes a background action with no return value and invokes runnable on completion.
     */
    public static void executeAction(Runnable backgroundAction,
                                     Runnable onSuccess,
                                     Consumer<Throwable> onError,
                                     Node... controlsToDisable) {
        execute(() -> {
            backgroundAction.run();
            return null;
        }, res -> {
            if (onSuccess != null) onSuccess.run();
        }, onError, controlsToDisable);
    }
}
