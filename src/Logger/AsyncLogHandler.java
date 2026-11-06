package Logger;

import java.util.concurrent.*;

public class AsyncLogHandler implements LogHandler {

    private final LogHandler delegate;
    private final ExecutorService executor = new ThreadPoolExecutor(
            4,
            4,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(100),
            new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    public AsyncLogHandler(LogHandler logManager) {
        this.delegate = logManager;
    }

    public void log(String message) {
        executor.execute(() -> {
            try {
                delegate.log(message);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            };
        });
    }
    public void close() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}
