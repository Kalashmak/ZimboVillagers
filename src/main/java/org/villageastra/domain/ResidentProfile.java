package org.villageastra.domain;
import java.util.*;
/** Stable random selection per identity, independent of profession and settlement membership. */
public record ResidentProfile(String name,int skin) {
    public static final List<String> SKINS=List.of("steve","alex","ari","efe","kai","makena","noor","sunny","zuri");
    private static final String[] FIRST={"Алекс","Мира","Тео","Лея","Никита","Рада","Ян","Ника","Марк","Ася","Рен","Лина","Лев","Эмма","Дан","Майя","Илья","Таис","Лука","Нора","Ари","Саша","Кай","Женя"};
    private static final String[] LAST={"Берег","Сосна","Ручей","Клён","Роса","Ветер","Долина","Искра","Камень","Заря","Вереск","Лист","Ясень","Луг","Снег","Ольха"};
    public ResidentProfile {
        if(name==null || name.isBlank() || name.length()>64 || skin<0 || skin>=SKINS.size())throw new IllegalArgumentException("Invalid resident profile");
    }
    public static ResidentProfile generate(UUID id) {
        Random random=new Random(id.getMostSignificantBits()^Long.rotateLeft(id.getLeastSignificantBits(),23));
        return new ResidentProfile(FIRST[random.nextInt(FIRST.length)]+" "+LAST[random.nextInt(LAST.length)],random.nextInt(SKINS.size()));
    }
}
