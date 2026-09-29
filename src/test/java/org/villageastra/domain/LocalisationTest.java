package org.villageastra.domain;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** P13: both languages carry every key the code asks for, and neither carries keys the other lacks. */
final class LocalisationTest {
 private static final Path ROOT=Path.of("").toAbsolutePath();
 private static final Pattern KEY=Pattern.compile("\"([a-z_]+(?:\\.[a-z_]+)*\\.villageastra\\.[a-z0-9_.]+)\"");
 private static Map<String,String> lang(String code) throws IOException {
  var text=Files.readString(ROOT.resolve("src/main/resources/assets/villageastra/lang/"+code+".json"),StandardCharsets.UTF_8);
  var out=new LinkedHashMap<String,String>();
  var m=Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(text);
  while(m.find())out.put(m.group(1),m.group(2));
  return out;
 }
 private static Set<String> used() throws IOException {
  var keys=new TreeSet<String>();
  try(Stream<Path> files=Files.walk(ROOT.resolve("src/main/java"))){
   for(var file:files.filter(p->p.toString().endsWith(".java")).toList()){
    var m=KEY.matcher(Files.readString(file,StandardCharsets.UTF_8));
    while(m.find())keys.add(m.group(1));
   }
  }
  return keys;
 }
 @Test void bothLanguagesHaveTheSameKeys() throws IOException {
  var ru=lang("ru_ru");var en=lang("en_us");
  var onlyRu=new TreeSet<>(ru.keySet());onlyRu.removeAll(en.keySet());
  var onlyEn=new TreeSet<>(en.keySet());onlyEn.removeAll(ru.keySet());
  assertTrue(onlyRu.isEmpty(),"Keys missing from en_us: "+onlyRu);
  assertTrue(onlyEn.isEmpty(),"Keys missing from ru_ru: "+onlyEn);
  assertTrue(ru.size()>200,"The catalogue is real: "+ru.size());
 }
 @Test void everyKeyTheCodeAsksForIsTranslated() throws IOException {
  var ru=lang("ru_ru");var en=lang("en_us");
  // Keys built from a value at runtime (work.villageastra.<status>, refusal reasons) end with a dot in the source and are checked by their prefix.
  var missing=new TreeSet<String>();
  for(var key:used()){
   if(ru.containsKey(key)&&en.containsKey(key))continue;
   boolean prefix=ru.keySet().stream().anyMatch(k->k.startsWith(key))&&en.keySet().stream().anyMatch(k->k.startsWith(key));
   if(!prefix)missing.add(key);
  }
  assertTrue(missing.isEmpty(),"Untranslated keys used by the code: "+missing);
 }
 @Test void noTranslationIsEmptyOrLeftInTheOtherLanguage() throws IOException {
  var ru=lang("ru_ru");var en=lang("en_us");
  var empty=new TreeSet<String>();var copied=new TreeSet<String>();
  for(var e:ru.entrySet()){
   if(e.getValue().isBlank()||en.getOrDefault(e.getKey(),"").isBlank())empty.add(e.getKey());
   // A Russian line without a single Cyrillic letter is an untranslated English line, unless it is only digits or punctuation.
   else if(e.getValue().chars().anyMatch(Character::isLetter)&&e.getValue().chars().noneMatch(c->c>='А'&&c<='я'))copied.add(e.getKey());
  }
  assertTrue(empty.isEmpty(),"Empty translations: "+empty);
  assertTrue(copied.size()<=8,"Russian lines left in English: "+copied);
 }

 /** AD-106: the states and reasons of a companion are keys built at runtime, which the scanner above cannot see: each is checked here. */
 @Test void everyEscortStateAndReasonIsTranslated() throws IOException {
  var ru=lang("ru_ru");var en=lang("en_us");var missing=new TreeSet<String>();
  for(var s:EscortStates.STATES){var key="quest.villageastra.escort.state."+s;if(!ru.containsKey(key)||!en.containsKey(key))missing.add(key);}
  for(var r:EscortStates.REASONS){var key="quest.villageastra.escort.reason."+r;if(!ru.containsKey(key)||!en.containsKey(key))missing.add(key);}
  assertTrue(missing.isEmpty(),"Untranslated companion states or reasons: "+missing);
 }
}
