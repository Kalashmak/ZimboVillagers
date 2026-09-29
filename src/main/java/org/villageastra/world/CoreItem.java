package org.villageastra.world;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import org.villageastra.domain.CoreCatalog;
import org.villageastra.domain.CoreEffects;
/** AD-112: the item of a building core. It names its block (so a design's core cell costs this item) but places nothing:
 *  only the builders set a core, after the research of the building's branch for that level. The tooltip says what the core
 *  is, which building it serves and, for every effect that works, its numbers at levels I..VI; the others say "not yet active". */
public class CoreItem extends BlockItem {
 private final String type;
 public CoreItem(BuildingCoreBlock block,Item.Properties properties){super(block,properties);this.type=block.type();}
 public String type(){return type;}
 /** A player cannot set a core by hand. */
 @Override public InteractionResult place(BlockPlaceContext context){return InteractionResult.FAIL;}
 @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
  lines.add(Component.translatable("core.villageastra.desc."+type).withStyle(ChatFormatting.GRAY));
  MutableComponent serves=Component.translatable("building.villageastra."+type);if(type.equals("mine"))serves=serves.append(", ").append(Component.translatable("building.villageastra.quarry"));
  lines.add(Component.translatable("core.villageastra.serves",serves).withStyle(ChatFormatting.GRAY));
  lines.addAll(effectLines(type));
  lines.add(Component.translatable("core.villageastra.builder_only").withStyle(ChatFormatting.DARK_GRAY));
 }
 /** The effect table of a core type: one line per active effect with its values I..VI, a grey "not yet active" line for the others. */
 public static List<Component> effectLines(String coreType){
  return CoreEffects.effects(coreType).stream().map(e->e.active()
   ?(Component)Component.translatable("core.villageastra.table",Component.translatable("core.villageastra.effect."+e.id()),IntStream.of(e.values()).mapToObj(String::valueOf).collect(Collectors.joining(" · "))).withStyle(ChatFormatting.GRAY)
   :Component.translatable("core.villageastra.effect.inactive",Component.translatable("core.villageastra.effect."+e.id())).withStyle(ChatFormatting.DARK_GRAY)).toList();
 }
 private static final String[] ROMAN={"","I","II","III","IV","V","VI"};
 public static String roman(int level){return level>=1&&level<ROMAN.length?ROMAN[level]:String.valueOf(level);}
 /** A universal ring (III copper, IV iron, V gold, VI netherite): set into the core of any work building for that level. */
 public static class CoreRingItem extends Item {
  private final int grade;
  public CoreRingItem(int grade,Item.Properties properties){super(properties);this.grade=grade;}
  public int grade(){return grade;}
  public String id(){return CoreCatalog.ringId(grade);}
  @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
   lines.add(Component.translatable("item.villageastra.core_ring.tooltip",roman(grade)).withStyle(ChatFormatting.GRAY));
   lines.add(Component.translatable("core.villageastra.ring_research",roman(grade)).withStyle(ChatFormatting.DARK_GRAY));
   if(grade==CoreCatalog.LAST_RING)lines.add(Component.translatable("item.villageastra.core_ring_6.trade").withStyle(ChatFormatting.GOLD));
  }
 }
}
