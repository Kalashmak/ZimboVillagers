package org.villageastra.server;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import org.villageastra.world.StarterVillage;

public final class ZimboVillagersCommands {
    public static final String ROOT = "zimbovillagers";
    private ZimboVillagersCommands() {}
    private static int help(CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("ZimboVillagers — /" + ROOT), false);
        dispatcher.getSmartUsage(dispatcher.getRoot().getChild(ROOT), source).values().forEach(usage ->
                source.sendSuccess(() -> Component.literal("/" + ROOT + " " + usage), false));
        source.sendSuccess(() -> Component.literal("https://github.com/Kalashmak/ZimboVillagers/blob/main/docs/COMMANDS.md"), false);
        return 1;
    }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(ROOT)
                .executes(ctx -> help(dispatcher, ctx.getSource()))
                .then(Commands.literal("help").executes(ctx -> help(dispatcher, ctx.getSource())))
                .then(Commands.literal("dev_upgrade_hall").requires(source->source.hasPermission(2)).executes(ctx->{
                    var source=ctx.getSource();var e=SettlementData.get(source.getServer()).entries().stream().filter(v->v.dimension().equals(source.getLevel().dimension().location().toString())).min(java.util.Comparator.comparingDouble(v->v.center().distToCenterSqr(source.getPosition()))).orElse(null);
                    if(e==null)return 0;
                    try{org.villageastra.world.HallUpgradeGoal.request(source.getLevel(),e);source.sendSuccess(()->Component.literal("Hall upgrade queued for its builder; materials are required in the hall chest."),false);return 1;}
                    catch(IllegalStateException ex){source.sendFailure(Component.literal(ex.getMessage()));return 0;}
                }))
                .then(Commands.literal("dev_blueprint").requires(source->source.hasPermission(2))
                    .then(Commands.argument("design",com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((ctx,builder)->net.minecraft.commands.SharedSuggestionProvider.suggest(org.villageastra.world.BuildingBlueprints.designs().stream().map(d->d.id()),builder))
                        .then(Commands.argument("origin",BlockPosArgument.blockPos()).executes(ctx->{
                            try { org.villageastra.world.BuildingBlueprints.preview(ctx.getSource().getLevel(),
                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,"design"),BlockPosArgument.getLoadedBlockPos(ctx,"origin"));return 1; }
                            catch(IllegalStateException|NullPointerException error) {ctx.getSource().sendFailure(Component.literal(error.getMessage()));return 0;}
                        }))))
                .then(Commands.literal("annex").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("target",BlockPosArgument.blockPos()).executes(ctx -> {
                            var source=ctx.getSource();var level=source.getLevel();var data=SettlementData.get(source.getServer());
                            var mark=BlockPosArgument.getLoadedBlockPos(ctx,"target");
                            var target=data.entries().stream().filter(v->v.dimension().equals(level.dimension().location().toString())&&v.center().distSqr(mark)<=64*64).min(java.util.Comparator.comparingDouble(v->v.center().distSqr(mark))).orElse(null);
                            var buyer=data.entries().stream().filter(v->v.dimension().equals(level.dimension().location().toString())&&(target==null||!v.settlement().id().equals(target.settlement().id()))&&v.center().distToCenterSqr(source.getPosition())<=256*256).min(java.util.Comparator.comparingDouble(v->v.center().distToCenterSqr(source.getPosition()))).orElse(null);
                            if(target==null||buyer==null){source.sendFailure(Component.literal("No buyer or target settlement"));return 0;}
                            String reason=org.villageastra.world.Annexation.offer(source.getServer(),buyer,target,data.clock().ticks());
                            if(!reason.isEmpty()){source.sendFailure(Component.literal("Offer refused: "+reason));return 0;}
                            source.sendSuccess(()->Component.literal("Annexation offered for "+org.villageastra.world.Annexation.price(target)+" zindbo"),true);return 1;
                        })))
                .then(Commands.literal("annex_answer").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("accept",com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(ctx -> {
                            var source=ctx.getSource();var level=source.getLevel();var data=SettlementData.get(source.getServer());
                            var target=data.entries().stream().filter(v->v.dimension().equals(level.dimension().location().toString())&&org.villageastra.world.Annexation.record(source.getServer(),v.settlement().id())!=null).min(java.util.Comparator.comparingDouble(v->v.center().distToCenterSqr(source.getPosition()))).orElse(null);
                            if(target==null){source.sendFailure(Component.literal("No annexation offer nearby"));return 0;}
                            boolean accept=com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx,"accept");
                            String reason=org.villageastra.world.Annexation.answer(source.getServer(),target,source.getPlayer(),accept,data.clock().ticks());
                            if(!reason.isEmpty()&&!reason.equals("cancelled")){source.sendFailure(Component.literal("Answer refused: "+reason));return 0;}
                            if(accept&&reason.isEmpty()){String transfer=org.villageastra.world.Annexation.transfer(source.getServer(),target,data.clock().ticks());
                                if(!transfer.isEmpty()){source.sendFailure(Component.literal("Transfer refused: "+transfer));return 0;}}
                            source.sendSuccess(()->Component.literal(accept?"Annexation completed":"Offer declined and the reserve returned"),true);return 1;
                        })))
                .then(Commands.literal("campaign").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("target",BlockPosArgument.blockPos()).executes(ctx -> {
                            var source=ctx.getSource();var level=source.getLevel();var data=SettlementData.get(source.getServer());
                            var mark=BlockPosArgument.getLoadedBlockPos(ctx,"target");
                            var target=data.entries().stream().filter(v->v.dimension().equals(level.dimension().location().toString())&&v.center().distSqr(mark)<=64*64).min(java.util.Comparator.comparingDouble(v->v.center().distSqr(mark))).orElse(null);
                            var attacker=data.entries().stream().filter(v->v.dimension().equals(level.dimension().location().toString())&&(target==null||!v.settlement().id().equals(target.settlement().id()))&&v.center().distToCenterSqr(source.getPosition())<=256*256).min(java.util.Comparator.comparingDouble(v->v.center().distToCenterSqr(source.getPosition()))).orElse(null);
                            if(target==null||attacker==null){source.sendFailure(Component.literal("No attacker or target settlement"));return 0;}
                            var army=org.villageastra.world.Sieges.muster(level,attacker,target,data.clock().ticks());
                            if(army==null){source.sendFailure(Component.literal("The barracks has no soldiers or not enough real supplies"));return 0;}
                            source.sendSuccess(()->Component.literal("Campaign mustered: "+army.getList("soldiers",net.minecraft.nbt.Tag.TAG_INT_ARRAY).size()+" soldiers, "+org.villageastra.world.Sieges.supply(army,"fences")+" fence sections"),true);return 1;
                        })))
                .then(Commands.literal("siege").requires(source -> source.hasPermission(2)).executes(ctx -> {
                            var source=ctx.getSource();var level=source.getLevel();var data=SettlementData.get(source.getServer());
                            for(var army:org.villageastra.world.Sieges.armies(source.getServer())){
                                String reason=org.villageastra.world.Sieges.ready(level,army);
                                if(reason.isEmpty()&&org.villageastra.world.Sieges.begin(level,army,data.clock().ticks())){source.sendSuccess(()->Component.literal("Siege started"),true);return 1;}
                                if(!reason.isEmpty())source.sendFailure(Component.literal("Not ready: "+reason));
                            }
                            return 0;
                        }))
                .then(Commands.literal("dev_build").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("resident",net.minecraft.commands.arguments.EntityArgument.entity())
                                .then(Commands.argument("target",BlockPosArgument.blockPos()).executes(ctx -> {
                                    var entity=net.minecraft.commands.arguments.EntityArgument.getEntity(ctx,"resident");
                                    if (!(entity instanceof org.villageastra.world.ResidentEntity resident) || resident.settlementId()==null) return 0;
                                    var entry=SettlementData.get(ctx.getSource().getServer()).entry(resident.settlementId());
                                    if (entry==null || !entry.dimension().equals(resident.level().dimension().location().toString())
                                            || !entry.dimension().equals(ctx.getSource().getLevel().dimension().location().toString())) return 0;
                                    var person=entry.settlement().resident(resident.getUUID());
                                    var workplace=entry.settlement().workplace(resident.getUUID());
                                    if (person==null || person.profession()!=org.villageastra.domain.Profession.BUILDER || workplace==null) return 0;
                                    var target=BlockPosArgument.getLoadedBlockPos(ctx,"target");
                                    if (target.distSqr(entry.center())>32*32 || !resident.level().getBlockState(target).isAir()) return 0;
                                    try {
                                        resident.blockWork(new org.villageastra.world.BlockWork(resident.level().dimension().location().toString(),entry.center().offset(workplace.x()+1,workplace.y()+1,workplace.z()+4),target,
                                                resident.level().getBlockState(target),net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState()));
                                        return 1;
                                    } catch (IllegalStateException ex) { ctx.getSource().sendFailure(Component.translatable("error.villageastra.busy")); return 0; }
                                }))))
                .then(Commands.literal("dev_follow").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("resident", net.minecraft.commands.arguments.EntityArgument.entity()).executes(ctx -> {
                            var entity = net.minecraft.commands.arguments.EntityArgument.getEntity(ctx,"resident");
                            if (!(entity instanceof org.villageastra.world.ResidentEntity resident)) {
                                ctx.getSource().sendFailure(Component.translatable("error.villageastra.not_resident")); return 0;
                            }
                            resident.escort(ctx.getSource().getPlayerOrException().getUUID());
                            ctx.getSource().sendSuccess(() -> Component.translatable("command.villageastra.following"),false);
                            return 1;
                        })))
                .then(Commands.literal("dev_stop").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("resident", net.minecraft.commands.arguments.EntityArgument.entity()).executes(ctx -> {
                            var entity = net.minecraft.commands.arguments.EntityArgument.getEntity(ctx,"resident");
                            if (!(entity instanceof org.villageastra.world.ResidentEntity resident)) return 0;
                            resident.escort(null); return 1;
                        })))
                .then(Commands.literal("status").executes(ctx -> {
                    var data = SettlementData.get(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.translatable("command.villageastra.status",
                            data.entries().size(), data.clock().seconds()), false);
                    return data.entries().size();
                }))
                .then(Commands.literal("dev_create").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("origin", BlockPosArgument.blockPos()).executes(ctx -> {
                            var source = ctx.getSource();
                            try {
                                var settlement = StarterVillage.create(source.getLevel(), BlockPosArgument.getLoadedBlockPos(ctx, "origin"));
                                source.sendSuccess(() -> Component.translatable("command.villageastra.created", settlement.id().toString()), true);
                                return 1;
                            } catch (IllegalStateException exception) {
                                source.sendFailure(Component.translatable("error.villageastra." + exception.getMessage()));
                                return 0;
                            }
                        }))));
    }
}
