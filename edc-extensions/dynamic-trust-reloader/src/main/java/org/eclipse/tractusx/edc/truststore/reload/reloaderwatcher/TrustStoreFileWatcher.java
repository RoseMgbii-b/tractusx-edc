package org.eclipse.tractusx.edc.truststore.reload.reloaderwatcher;

import org.eclipse.edc.spi.monitor.Monitor;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TrustStore File Watcher
 * For file change detection and notification
 * Watches truststore file for changes and notifies a listener
 */
public class TrustStoreFileWatcher implements Closeable {

    public interface ChangeListener {
        void onTrustStoreChanged();
    }

    // stores the file's last known modified timestamp
    private volatile long lastKnownModified = 0L;

    private final Path trustStorePath;
    private final ChangeListener listener;
    private final Monitor monitor;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor;
    private WatchService watchService;

    private final ScheduledExecutorService debounceScheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile Future<?> debounceFuture;

    public TrustStoreFileWatcher(Path trustStorePath, ChangeListener listener, Monitor monitor) {
        this.trustStorePath = Objects.requireNonNull(trustStorePath, "TrustStorePath should not be null");
        this.listener = Objects.requireNonNull(listener, "listener should not be null");
        this.monitor = Objects.requireNonNull(monitor, "monitor should not be null");
    }

    public void start() {

        try {
            lastKnownModified = Files.getLastModifiedTime(trustStorePath).toMillis();
        } catch (Exception e) {
            monitor.warning("[TrustStoreFileWatcher] Could not read initial modified time: " + e.getMessage());
        }

        // Implementation for starting the file watcher
        if(running.compareAndSet(false, true)) {
            try {
                watchService = trustStorePath.getFileSystem().newWatchService();
                Path parentDir = trustStorePath.getParent();
                parentDir.register(watchService,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE
                );

                executor = Executors.newSingleThreadExecutor(threadFactory -> {
                    Thread newSingleThread = new Thread(threadFactory, "truststore-watcher");
                    newSingleThread.setDaemon(true);
                    return newSingleThread;
                });
                executor.submit(this::watchLoop);

                monitor.info("[TrustStoreFileWatcher] Watcher has started watching: " + trustStorePath);
            } catch (IOException e) {
                monitor.severe("[TrustStoreFileWatcher] Error while watching: " + e.getMessage(), e);
            }
        }
    }

    public void watchLoop() {
        // Implementation for the watch loop
        while (running.get()) {
            try {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    Path changed = (Path) event.context();

                    // Check if the modified file is the truststore file, ignore unrelated files like .git, .idea
                    if (!changed.getFileName().equals(trustStorePath.getFileName())) {
                        continue;
                    }

                    monitor.info("[TrustStoreFileWatcher] Detected file event: " + kind + " for file: " + changed);

                    // if event is modify or create, trigger reload with debounce
                    if (kind == StandardWatchEventKinds.ENTRY_MODIFY ||
                            kind == StandardWatchEventKinds.ENTRY_CREATE
                    ) {
                        debounceReload();
                    } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        monitor.warning("[TrustStoreFileWatcher] Truststore file deleted: " + trustStorePath);
                        lastKnownModified = 0L; // reset last known modified time
                    }
                }
                key.reset();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                monitor.severe("[TrustStoreFileWatcher] Error in watch loop: " + e.getMessage(), e);
            }
        }
    }

    private void debounceReload() {
        if (debounceFuture != null && !debounceFuture.isDone()) {
            debounceFuture.cancel(false);
        }

        debounceFuture = debounceScheduler.schedule(() ->
                {
                    monitor.info("[TrustStoreFileWatcher] Detected change in truststore file, triggering reload " + trustStorePath);

                    // For modification-time checking,
                    // checks the file's last modified timestamp and only reload if actual changes happened in the file
                    // then store the current modified timestamp
                    try{
                        long currentModified = Files.getLastModifiedTime(trustStorePath).toMillis();

                        // Compare timestamps BEFORE firing reload
                        if (currentModified != lastKnownModified) {
                            monitor.info("[TrustStoreFileWatcher] Detected modification, triggering reload for: " + trustStorePath);
                            lastKnownModified = currentModified;
                            listener.onTrustStoreChanged();
                        } else {
                            monitor.info("[TrustStoreFileWatcher] File modification time unchanged, skipping reload.");
                        }

                    } catch (Exception e) {
                        monitor.warning("[TrustStoreFileWatcher] Failed to read lastModified time: " + e.getMessage());
                    }
                }
                , 500, java.util.concurrent.TimeUnit.MILLISECONDS
        );  // 500ms debounce to avoid multiple triggers within 500ms
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            try {
                if (watchService != null) { watchService.close(); }
                if (executor != null) {
                    executor.shutdownNow();
                    debounceScheduler.shutdownNow();
                }
            } catch (IOException e) {
                monitor.severe("[TrustStoreFileWatcher] Error while executing stop operation: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void close() throws IOException {
        stop();
    }

}





