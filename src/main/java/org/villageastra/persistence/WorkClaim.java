package org.villageastra.persistence;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import java.util.UUID;

/** Durable identity binding; another NPC cannot replay this job's material receipt. */
public final class WorkClaim {
    private WorkClaim() {}
    public static boolean retired(Path directory,UUID job){return Files.exists(directory.resolve(job+".retired"));}
    public static void retire(Path directory,UUID job,UUID worker){try{AtomicRecord.write(directory.resolve(job+".retired"),worker.toString().getBytes(StandardCharsets.UTF_8));}catch(IOException e){throw new IllegalStateException(e);}}

    public static synchronized void acquire(Path directory,UUID job,UUID worker) {
        if(retired(directory,job))throw new IllegalStateException("Work already retired");
        Path file=directory.resolve(job+".claim");
        try {
            if(Files.exists(file)) {
                String owner=new String(AtomicRecord.read(file),StandardCharsets.UTF_8);
                if(!owner.equals(worker.toString()))throw new IllegalStateException("Job belongs to a different resident");
            } else AtomicRecord.write(file,worker.toString().getBytes(StandardCharsets.UTF_8));
        } catch(IOException e) { throw new IllegalStateException("Cannot establish durable work owner",e); }
    }
}
