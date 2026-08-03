package net.einself.mu.collection.api;

public interface LockHandle extends AutoCloseable {
    @Override
    void close();
}
