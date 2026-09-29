package org.villageastra.domain;
import java.util.*;
/** AD-124: the one rule of whether a technology can be studied now, shared by the server (BookResearch), the office tree and the
 *  Overview's attention list. No Minecraft types: the caller passes what the next hall level needs (BuildingTiers.hallResearch(hall+1)).
 *  AD-136: a level-I node is paid in resources; the server passes whether the village's stock holds them (null: not looked at). */
public final class ResearchRules {
 private ResearchRules(){}
 /** "completed", "hall_level", "dependencies" or "available". AD-073: research the next hall level itself needs opens one hall tier early,
  *  or the hall could never be raised to open that tier. */
 public static String reason(Set<String> done,int hall,ResearchCatalog.Node n,Collection<String> hallNext){return reason(done,hall,n,hallNext,null);}
 /** The same, then "resources" for a level-I node whose price the stock does not hold (stock false). */
 public static String reason(Set<String> done,int hall,ResearchCatalog.Node n,Collection<String> hallNext,Boolean stock){
  if(done.contains(n.id()))return "completed";
  if(hall<n.tier()&&!(n.tier()==hall+1&&hallNext!=null&&hallNext.contains(n.id())))return "hall_level";
  if(!done.containsAll(n.requires()))return "dependencies";
  if(n.resourcePaid()&&Boolean.FALSE.equals(stock))return "resources";
  return "available";
 }
}
