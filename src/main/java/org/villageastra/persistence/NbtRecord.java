package org.villageastra.persistence;
import net.minecraft.nbt.*;
import java.io.*;
import java.nio.file.Path;
/** Checksummed, atomic authoritative NBT record. */
public final class NbtRecord {
 private NbtRecord(){}
 public static CompoundTag read(Path p){try{return NbtIo.read(new DataInputStream(new ByteArrayInputStream(AtomicRecord.read(p))));}catch(IOException e){throw new IllegalStateException("Cannot read "+p,e);}}
 public static void write(Path p,CompoundTag t){try{var out=new ByteArrayOutputStream();NbtIo.write(t,new DataOutputStream(out));AtomicRecord.write(p,out.toByteArray());}catch(IOException e){throw new IllegalStateException("Cannot write "+p,e);}}
}
