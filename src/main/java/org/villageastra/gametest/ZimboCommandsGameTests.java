package org.villageastra.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.villageastra.VillageAstra;
import org.villageastra.server.SettlementData;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ZimboCommandsGameTests {
    @GameTest(template="empty", batch="zimbo_commands")
    public static void registeredCommandsRespectPermissions(GameTestHelper h) throws CommandSyntaxException {
        var server = h.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var visitor = server.createCommandSourceStack().withLevel(h.getLevel()).withPermission(0);
        var operator = visitor.withPermission(2);
        var root = dispatcher.getRoot().getChild("zimbovillagers");
        h.assertTrue(root != null && dispatcher.getRoot().getChild("astra") == null, "Only the current command root is registered");
        h.assertTrue(dispatcher.execute("zimbovillagers", visitor) == 1, "Root shows help to visitors");
        h.assertTrue(dispatcher.execute("zimbovillagers help", visitor) == 1, "Explicit help works");
        h.assertTrue(dispatcher.execute("zimbovillagers status", visitor) == SettlementData.get(server).entries().size(), "Status reads real settlement data");
        var visible = dispatcher.getSmartUsage(root, visitor).keySet();
        h.assertTrue(visible.size() == 2, "Visitors see only help and status");
        for (var child : root.getChildren()) {
            if (child.getName().equals("help") || child.getName().equals("status")) continue;
            h.assertTrue(!visible.contains(child) && !child.canUse(visitor) && child.canUse(operator), "Permission gate for " + child.getName());
        }
        boolean denied = false;
        try { dispatcher.execute("zimbovillagers dev_create 0 90 0", visitor); }
        catch (CommandSyntaxException expected) { denied = true; }
        h.assertTrue(denied, "Unauthorized world mutation is rejected by dispatcher");
        h.succeed();
    }

    @GameTest(template="empty", batch="zimbo_commands")
    public static void renamedCommandsControlActualResident(GameTestHelper h) throws CommandSyntaxException {
        var level = h.getLevel();
        var resident = VillageAstra.RESIDENT.get().create(level);
        var pos = h.absolutePos(net.minecraft.core.BlockPos.ZERO).above();
        resident.moveTo(pos.getX()+0.5, pos.getY(), pos.getZ()+0.5);
        h.assertTrue(level.addFreshEntity(resident), "Resident added");
        try {
            var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "CommandsTest"));
            var source = level.getServer().createCommandSourceStack().withLevel(level).withEntity(player).withPermission(2);
            var dispatcher = level.getServer().getCommands().getDispatcher();
            h.assertTrue(dispatcher.execute("zimbovillagers dev_follow " + resident.getUUID(), source) == 1, "Follow executed");
            h.assertTrue(player.getUUID().equals(resident.escortPlayer()), "Resident follows command executor");
            h.assertTrue(dispatcher.execute("zimbovillagers dev_stop " + resident.getUUID(), source) == 1, "Stop executed");
            h.assertTrue(resident.escortPlayer() == null, "Escort released");
        } finally { resident.discard(); }
        h.succeed();
    }
}
