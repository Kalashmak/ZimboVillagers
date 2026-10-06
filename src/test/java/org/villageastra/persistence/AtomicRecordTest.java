package org.villageastra.persistence;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class AtomicRecordTest {
    @TempDir Path directory;
    @Test void watchedReplacementInvalidatesEvenWithTheSameTimestampAndSize() throws Exception {
        Path file=directory.resolve("plan.bin");AtomicRecord.write(file,new byte[]{1,2,3});
        long first=AtomicRecord.revision(file);var timestamp=Files.getLastModifiedTime(file);long size=Files.size(file);
        AtomicRecord.write(directory.resolve(".").resolve("plan.bin"),new byte[]{4,5,6});
        Files.setLastModifiedTime(file,timestamp);
        assertEquals(size,Files.size(file));assertEquals(timestamp,Files.getLastModifiedTime(file));
        assertEquals(first+1,AtomicRecord.revision(file));
        assertArrayEquals(new byte[]{4,5,6},AtomicRecord.read(file));
    }
    @Test void pendingWriteCannotReplaceCommittedRecord() throws Exception {
        Path file=directory.resolve("op.bin");AtomicRecord.write(file,new byte[]{1,2,3});
        Files.write(file.resolveSibling("op.bin.pending"),new byte[]{9});
        assertArrayEquals(new byte[]{1,2,3},AtomicRecord.read(file));
        AtomicRecord.write(file,new byte[]{4,5});assertArrayEquals(new byte[]{4,5},AtomicRecord.read(file));
    }
    @Test void restoredWorkCannotBeClaimedByAnotherResident() {
        var job=java.util.UUID.randomUUID();var owner=java.util.UUID.randomUUID();
        WorkClaim.acquire(directory,job,owner);
        WorkClaim.acquire(directory,job,owner);
        assertThrows(IllegalStateException.class,()->WorkClaim.acquire(directory,job,java.util.UUID.randomUUID()));
        WorkClaim.acquire(directory,job,owner);
    }
    @Test void corruptionAndTruncationFailClosed() throws Exception {
        Path file=directory.resolve("op.bin");AtomicRecord.write(file,new byte[]{1,2,3});
        byte[] bytes=Files.readAllBytes(file);bytes[8]^=1;Files.write(file,bytes);
        assertThrows(java.io.IOException.class,()->AtomicRecord.read(file));
        Files.write(file,new byte[]{1});assertThrows(java.io.IOException.class,()->AtomicRecord.read(file));
    }
}
