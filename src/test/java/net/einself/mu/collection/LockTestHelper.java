package net.einself.mu.collection;

import java.nio.file.Path;

import net.einself.mu.collection.api.CollectionRoot;
import net.einself.mu.collection.internal.CollectionLock;
import net.einself.mu.shared.MuException;

public class LockTestHelper {

    public static void main(String[] args) {
        CollectionRoot root = new CollectionRoot(Path.of(args[0]));
        try {
            CollectionLock lock = CollectionLock.acquire(root);
            lock.close();
            System.exit(0);
        } catch (MuException e) {
            System.exit(e.exitCode().value());
        }
    }
}