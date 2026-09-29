package org.villageastra.domain;
import java.util.*;
/** Owner 2026-09-24: every village has its own name from the moment it appears (the starter one, the generated ones, those founded later),
 *  never the same twice in one world. Russian village names of the old kind — a root of a tree, a field, water or stone with the endings
 *  of place it takes (Берёзовка, Дубрава, Каменец, Ольховец) —, then with Малая/Большая (agreeing with the name) and last with a numeral when a world
 *  has used them all. The name is kept in Russian; {@link #en} spells it in Latin letters for an English client. Pure: the choice depends only
 *  on the village's id and the names already taken, so the same world gives the same names. */
public final class VillageNames {
 private VillageNames(){}
 /** Each root with the endings Russian gives it (Берёзовка, Берёзово, Берёзники…): only names that sound like real villages. */
 static final String[][] ROOTS={
  {"Берёз","овка","ово","ники","ец"},{"Дуб","овка","ово","ки","рава"},{"Сосн","овка","ово","ы","ицы"},{"Ольх","овка","ово","овец"},
  {"Рябин","овка","ки"},{"Ясен","евка","ево","ки"},{"Лип","овка","ово","ки","ецк"},{"Калин","овка","ино","ки"},{"Клён","овка","ово","ки"},
  {"Вишн","ёвка","ёво","яки"},{"Осин","овка","ово","ки","ники"},{"Ель","ник","ники","ня"},{"Ореш","ек","ково","ники"},
  {"Камен","ка","ец","ное","ск"},{"Ключ","и","евое","ики"},{"Брод","ы","ки"},{"Луг","овое","а"},{"Бор","ки","ок","овое"},
  {"Холм","ы","ец"},{"Озер","ки","ное","цы"},{"Руч","ьи","ейки"},{"Мох","овое","овка"},{"Полян","ка","ы"},{"Гор","ки","ка"},
  {"Криниц","а","ы"},{"Снеж","ное","ки","ино"},{"Солов","ьёвка","ьи"},{"Журавл","ёво","и","иха"},{"Лебед","ёвка","и","янь"},
  {"Сокол","овка","ово"},{"Медвеж","ье","ий Лог"},{"Зарец","кое"},{"Заозер","ье"},{"Подгор","ное"},{"Белояр","ово"},{"Светл","ое","овка"},
  {"Ржан","ое","ица"},{"Овс","янка","яное"},{"Мельнич","ное"},{"Родник","и","овое"},{"Ивов","ое"},{"Верб","овка","ки"}};
 /** All the plain names, in a fixed order. */
 static List<String> plain(){var out=new ArrayList<String>();for(var r:ROOTS)for(int i=1;i<r.length;i++)out.add(r[0]+r[i]);return out;}
 /** The size words agreeing with a name's gender, read from its ending (a plural, a feminine -а/-я/-ь, a neuter -о/-е, else masculine). */
 static String[] size(String name){
  var word=name.contains(" ")?name.substring(0,name.indexOf(' ')):name;
  if(name.endsWith(" Лог"))return new String[]{"Малый","Большой"};
  if(word.endsWith("ы")||word.endsWith("и"))return new String[]{"Малые","Большие"};
  if(word.endsWith("а")||word.endsWith("я")||word.endsWith("нь"))return new String[]{"Малая","Большая"};
  if(word.endsWith("о")||word.endsWith("е"))return new String[]{"Малое","Большое"};
  return new String[]{"Малый","Большой"};
 }
 /** A name not in {@code taken} for the village of this id: a plain one first, then Малая/Большая, then a numeral. */
 public static String unique(UUID village,Set<String> taken){
  var names=plain();
  // The plain names in an order of this village's own (the same id, the same order), the first one free.
  var order=new ArrayList<>(names);Collections.shuffle(order,new Random(village.getMostSignificantBits()^village.getLeastSignificantBits()));
  for(var n:order)if(!taken.contains(n))return n;
  for(var p:names)for(var size:size(p)){var n=size+" "+p;if(!taken.contains(n))return n;}
  for(int k=2;;k++)for(var n:names){var m=n+" "+roman(k);if(!taken.contains(m))return m;}
 }
 static String roman(int n){var v=new int[]{1000,900,500,400,100,90,50,40,10,9,5,4,1};var s=new String[]{"M","CM","D","CD","C","XC","L","XL","X","IX","V","IV","I"};
  var out=new StringBuilder();for(int i=0;i<v.length;i++)while(n>=v[i]){out.append(s[i]);n-=v[i];}return out.toString();}
 private static final Map<Character,String> LATIN=new HashMap<>();
 static{String[][] t={{"а","a"},{"б","b"},{"в","v"},{"г","g"},{"д","d"},{"е","e"},{"ё","yo"},{"ж","zh"},{"з","z"},{"и","i"},{"й","y"},{"к","k"},{"л","l"},
  {"м","m"},{"н","n"},{"о","o"},{"п","p"},{"р","r"},{"с","s"},{"т","t"},{"у","u"},{"ф","f"},{"х","kh"},{"ц","ts"},{"ч","ch"},{"ш","sh"},{"щ","shch"},
  {"ъ",""},{"ы","y"},{"ь",""},{"э","e"},{"ю","yu"},{"я","ya"}};
  for(var p:t){LATIN.put(p[0].charAt(0),p[1]);var up=p[1].isEmpty()?"":Character.toUpperCase(p[1].charAt(0))+p[1].substring(1);LATIN.put(Character.toUpperCase(p[0].charAt(0)),up);}}
 /** The name in Latin letters (Berezovka, Malaya Dubovo, Kamenets II) for an English client. */
 public static String en(String ru){
  if(ru==null)return "";var out=new StringBuilder();for(char c:ru.toCharArray()){var l=LATIN.get(c);out.append(l==null?String.valueOf(c):l);}return out.toString();
 }
}
