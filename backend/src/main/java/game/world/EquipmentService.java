package game.world;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Item definitions live in data; ownership and slot changes remain server-authoritative. */
@Service
public class EquipmentService {
    private final JdbcTemplate db;
    EquipmentService(JdbcTemplate db){this.db=db;}
    public record Item(String code,String name,String description,String kind,String handUsage,int passiveArmor,int guardReduction,int guardMinDamage,boolean grantsAttack) {}
    public record Loadout(String mainHand,String offHand,WeaponRules.Weapon weapon,Item offhand,int armor,boolean canGuard,int guardReduction,int guardMinDamage) {
        public boolean canAttack(){return weapon.damage()>0&&(offhand==null||!offhand.handUsage().equals("off_two"));}
    }
    private Item item(String code){
        if(code==null)return null;
        return db.query("select code,name,description,kind,hand_usage,passive_armor,guard_reduction,guard_min_damage,grants_attack from item_definitions where code=?",
            (r,n)->new Item(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getInt(6),r.getInt(7),r.getInt(8),r.getBoolean(9)),code)
            .stream().findFirst().orElseThrow(()->AccountService.bad("物品不存在"));
    }
    public WeaponRules.Weapon weapon(String code){
        if(code==null||code.equals("unarmed"))return WeaponRules.UNARMED;
        if(code.equals("claws"))return WeaponRules.CLAWS;
        return db.query("select code,name,shape,min_range,max_range,attack_cost,damage,move_rule from item_definitions where code=? and kind='weapon' and grants_attack=true",
            (r,n)->new WeaponRules.Weapon(r.getString(1),r.getString(2),r.getString(3),r.getInt(4),r.getInt(5),r.getInt(6),r.getInt(7),r.getString(8)),code).stream().findFirst().orElseThrow(()->AccountService.bad("武器不存在"));
    }
    public List<Map<String,Object>> catalog(){return db.queryForList("select * from item_definitions order by code");}
    public Loadout loadout(UUID id){
        var row=db.queryForMap("select weapon,offhand from characters where id=?",id);
        String main=(String)row.get("weapon"),off=(String)row.get("offhand");
        Item mainItem=main==null?null:item(main),offItem=off==null?null:item(off);
        int armor=(mainItem==null?0:mainItem.passiveArmor())+(offItem==null?0:offItem.passiveArmor());
        Item guard=java.util.stream.Stream.of(mainItem,offItem).filter(Objects::nonNull).filter(i->i.guardReduction()>0).findFirst().orElse(null);
        WeaponRules.Weapon weapon=(mainItem!=null&&mainItem.grantsAttack())?weapon(main):WeaponRules.UNARMED;
        return new Loadout(main,off,weapon,offItem,armor,guard!=null,guard==null?0:guard.guardReduction(),guard==null?1:guard.guardMinDamage());
    }
    public void equip(UUID id,String code){
        equip(id,code,null);
    }
    public void equip(UUID id,String main,String off){
        Item mainItem=main==null?null:item(main),offItem=off==null?null:item(off);
        if(mainItem!=null&&!(mainItem.kind().equals("weapon")||mainItem.kind().equals("shield")))throw AccountService.bad("主手只能装备武器或双手盾");
        if(offItem!=null&&!offItem.kind().equals("shield"))throw AccountService.bad("副手只能装备盾牌");
        if(mainItem!=null&&mainItem.handUsage().equals("off_one")){offItem=mainItem;mainItem=null;off=offItem.code();main=null;}
        if(mainItem!=null&&mainItem.handUsage().equals("off_two")){offItem=mainItem;mainItem=null;off=offItem.code();main=null;}
        if(mainItem!=null&&mainItem.handUsage().equals("main_two"))off=null;
        if(offItem!=null&&offItem.handUsage().equals("off_two"))main=null;
        if(mainItem!=null&&mainItem.handUsage().equals("none"))throw AccountService.bad("这件物品不能装备");
        if(offItem!=null&&offItem.handUsage().equals("none"))throw AccountService.bad("这件物品不能装备");
        Map<String,Object> old=db.queryForMap("select weapon,offhand from characters where id=?",id);
        String oldMain=(String)old.get("weapon"),oldOff=(String)old.get("offhand");
        if(Objects.equals(oldMain,main)&&Objects.equals(oldOff,off))return;
        Map<String,Integer> need=new HashMap<>();
        if(main!=null&&!main.equals(oldMain)&&!main.equals(oldOff))need.merge(main,1,Integer::sum);
        if(off!=null&&!off.equals(oldMain)&&!off.equals(oldOff))need.merge(off,1,Integer::sum);
        for(var e:need.entrySet())if(db.update("update inventories set quantity=quantity-? where character_id=? and item_code=? and quantity>=?",e.getValue(),id,e.getKey(),e.getValue())==0)throw AccountService.bad("背包中缺少需要装备的物品");
        if(oldMain!=null&&!oldMain.equals(main)&&!oldMain.equals(off))give(id,oldMain,1);
        if(oldOff!=null&&!oldOff.equals(main)&&!oldOff.equals(off))give(id,oldOff,1);
        db.update("update characters set weapon=?,offhand=? where id=?",main,off,id);
    }
    public void give(UUID id,String code,int quantity){
        if(code==null)throw AccountService.bad("请选择武器");
        if(quantity<1||quantity>100)throw AccountService.bad("数量须在1至100之间");
        if(item(code)==null)throw AccountService.bad("物品不存在");
        if(Set.of("unarmed","claws").contains(code))throw AccountService.bad("请选择可发放的物品");
        db.update("insert into inventories(character_id,item_code,quantity) values(?,?,?) on conflict(character_id,item_code) do update set quantity=inventories.quantity+excluded.quantity",id,code,quantity);
    }
    public void consume(UUID id,String code,int quantity){
        if(db.update("update inventories set quantity=quantity-? where character_id=? and item_code=? and quantity>=?",quantity,id,code,quantity)==0)throw AccountService.bad("背包中没有可用物品");
    }
    public boolean has(UUID id,String code){return Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from inventories where character_id=? and item_code=? and quantity>0)",Boolean.class,id,code));}
}
