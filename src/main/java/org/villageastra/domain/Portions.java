package org.villageastra.domain;
/** AD-139: the restaurant's portioned serving, pure arithmetic (JUnit DiningMathTest). A meal is {@code meal} rations. The hall eats first what
 *  the last dish left over, then opens whole dishes of {@code ration} rations each, and keeps what the meal leaves of the last one (up to
 *  {@code max}, the rest is lost) for the next diner. The pantry path (AD-104) opens whole items and throws the rest away. */
public final class Portions {
 private Portions(){}
 /** One served meal: {dishes opened, leftover after, rations eaten}. A dish of no rations is never opened (0 dishes, nothing eaten). */
 public static int[] serve(int leftover,int ration,int meal,int max){
  if(meal<1||leftover<0)throw new IllegalArgumentException("Invalid meal");
  int eaten=Math.min(leftover,meal),left=leftover-eaten,dishes=0;
  if(ration>0)while(eaten<meal){int take=Math.min(ration,meal-eaten);eaten+=take;left+=ration-take;dishes++;}
  return new int[]{dishes,Math.min(max,left),eaten};
 }
 /** Dishes the hall opens for {@code meals} meals from an empty leftover: ⌈meal·meals/ration⌉ (while nothing is capped away). */
 public static int dishes(int meals,int ration,int meal,int max){int left=0,dishes=0;for(int i=0;i<meals;i++){var r=serve(left,ration,meal,max);dishes+=r[0];left=r[1];}return dishes;}
 /** Items the pantry takes for {@code meals} meals of this food: whole items to the meal each time (Rations.itemsPerMeal). */
 public static int pantryItems(int meals,int ration,int meal){return meals*Rations.itemsPerMeal(ration,meal);}
}
