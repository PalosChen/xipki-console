package org.xipki.mgmt.web.service;

import org.xipki.mgmt.web.core.*;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Coordinates profile checks and engine writes within the single Web deployment. */
public final class ProfileOperationLock implements AutoCloseable {
    private static final ReentrantReadWriteLock LOCK=new ReentrantReadWriteLock(true);
    private final java.util.concurrent.locks.Lock held;
    private ProfileOperationLock(java.util.concurrent.locks.Lock held) {this.held=held;held.lock();}
    public static ProfileOperationLock issuance() {return new ProfileOperationLock(LOCK.readLock());}
    public static ProfileOperationLock mutation() {return new ProfileOperationLock(LOCK.writeLock());}
    @Override public void close() {held.unlock();}
}
