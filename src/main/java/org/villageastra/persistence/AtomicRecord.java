package org.villageastra.persistence;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.*;
import java.util.Arrays;

/** Atomic, checksummed record. Never interpret corruption as an empty operation. */
public final class AtomicRecord {
    private static final int MAGIC=0x41535452;
    private AtomicRecord() {}
    public static void write(Path file,byte[] payload) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary=file.resolveSibling(file.getFileName()+".pending");
        ByteBuffer bytes=ByteBuffer.allocate(8+payload.length+32).putInt(MAGIC).putInt(payload.length).put(payload).put(hash(payload));
        bytes.flip();
        try(FileChannel channel=FileChannel.open(temporary,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)) {
            while(bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
        // Windows readers/scanners may briefly deny rename. Keep the same durable pending payload.
        for(int attempt=0;;attempt++) {
            try {Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);break;}
            catch(AccessDeniedException busy) {
                if(attempt>=5)throw busy;
                try {Thread.sleep(10L<<attempt);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new InterruptedIOException("Interrupted atomic record commit");}
            }
        }
    }
    public static byte[] read(Path file) throws IOException {
        byte[] bytes=Files.readAllBytes(file);
        if(bytes.length<40) throw new IOException("Truncated Astra journal: "+file);
        ByteBuffer buffer=ByteBuffer.wrap(bytes);
        if(buffer.getInt()!=MAGIC) throw new IOException("Invalid Astra journal header");
        int length=buffer.getInt();
        if(length<0 || length!=bytes.length-40) throw new IOException("Invalid Astra journal size");
        byte[] payload=new byte[length];buffer.get(payload);
        byte[] digest=new byte[32];buffer.get(digest);
        if(!Arrays.equals(hash(payload),digest)) throw new IOException("Astra journal checksum mismatch");
        return payload;
    }
    private static byte[] hash(byte[] payload) {
        try { return MessageDigest.getInstance("SHA-256").digest(payload); }
        catch(NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}
