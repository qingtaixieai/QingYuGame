package game.world;

import java.util.*;

/** Recipes describe a batch, not a single arrow. No caller invents costs or output quantities. */
public final class ProductionRecipes {
 public record Recipe(String code,String name,Map<String,Integer> materials,String facility,long duration,int output,boolean batch) {}
 public static final List<Recipe> ALL=List.of(
  new Recipe("iron_ingot","铁锭",Map.of("iron_ore",2),"furnace",10000,1,true),
  new Recipe("arrow","普通箭矢",Map.of("wood",1,"iron_ingot",1),"workbench",3000,10,true),
  new Recipe("bandage","绷带",Map.of("fiber",3),"portable",5000,1,true),
  new Recipe("axe","斧头",Map.of("wood",3,"iron_ingot",3),"anvil",20000,1,false),
  new Recipe("pickaxe","镐子",Map.of("wood",3,"iron_ingot",4),"anvil",20000,1,false),
  new Recipe("skinning_knife","剥皮刀",Map.of("wood",1,"iron_ingot",2),"anvil",15000,1,false),
  new Recipe("spear","长枪",Map.of("wood",4,"iron_ingot",2),"workbench",20000,1,false),
  new Recipe("light_armor","轻甲",Map.of("hide",4,"fiber",2),"workbench",25000,1,false));
 public static Recipe get(String code){return ALL.stream().filter(r->r.code().equals(code)).findFirst().orElseThrow(()->AccountService.bad("配方不存在"));}
 private ProductionRecipes(){}
}
