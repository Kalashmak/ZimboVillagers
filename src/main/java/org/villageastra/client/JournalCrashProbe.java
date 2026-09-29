package org.villageastra.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
import com.mojang.logging.LogUtils;
import java.io.*;
import java.util.UUID;

/** Explicit process-crash regression, only in a fresh or named smoke-harness world. */
final class JournalCrashProbe {
    private static boolean started;
    static boolean enabled() { return System.getProperty("villageastra.journalCrash")!=null || Boolean.getBoolean("villageastra.journalRecover"); }
    static void run(Minecraft mc) {
        if(started)return;started=true;
        var server=mc.getSingleplayerServer();
        server.execute(()-> {
            try {
                var level=server.overworld(); var entry=SettlementData.get(server).entries().iterator().next();
                var pos=entry.center().offset(1,1,4); var chest=(ChestBlockEntity)level.getBlockEntity(pos);
                var file=server.getWorldPath(LevelResource.ROOT).resolve("data/journal-crash-probe.bin");
                if(Boolean.getBoolean("villageastra.journalRecover")) {
                    var record=NbtIo.read(new DataInputStream(new ByteArrayInputStream(AtomicRecord.read(file))));
                    UUID id=record.getUUID("operation");int baseline=record.getInt("count");
                    var cargo=WorldJournal.recoverTake(level,id);
                    if(cargo.getCount()!=1 || chest.getItem(2).getCount()!=baseline-1) throw new IllegalStateException("Crash debit recovery lost or duplicated material");
                    var again=WorldJournal.recoverTake(level,id);
                    if(again.getCount()!=1 || chest.getItem(2).getCount()!=baseline-1) throw new IllegalStateException("Duplicate recovery debited twice");
                    LogUtils.getLogger().info("ASTRA_CRASH_RECOVERY VERIFIED original={} remaining={} recovered=1 operation={}",baseline,chest.getItem(2).getCount(),id);
                    mc.execute(mc::stop);
                } else {
                    UUID id=UUID.randomUUID();CompoundTag record=new CompoundTag();record.putUUID("operation",id);record.putInt("count",chest.getItem(2).getCount());
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();NbtIo.write(record,new DataOutputStream(bytes));AtomicRecord.write(file,bytes.toByteArray());
                    server.saveEverything(false,true,true);
                    WorldJournal.take(level,id,pos,2,chest.getItem(2).copy());
                    throw new IllegalStateException("Requested crash boundary was not reached");
                }
            } catch(Exception error) { LogUtils.getLogger().error("ASTRA_CRASH_RECOVERY FAILED",error);mc.execute(mc::stop); }
        });
    }
}
