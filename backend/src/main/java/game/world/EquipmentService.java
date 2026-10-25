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
    public record Loadout(String mainHand,String offHand,WeaponRules.Weapon weapon,Item offhand,int armor,boolean canGuard,int guardReduction,int guardMinDamage,String body) {
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
    public record Projectile(String ammoCode,int aimCost,int aimDamage) {}
    public String selectedAmmo(UUID actor,String family){
        String selected=db.queryForObject("select selected_ammo from characters where id=?",String.class,actor);
        return Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from item_definitions where code=? and ammo_family=?)",Boolean.class,selected,family))?selected:family;
    }
    public void selectAmmo(UUID actor,String code){
        if(!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from item_definitions where code=? and ammo_family='arrow')",Boolean.class,code)))throw AccountService.bad("不兼容的弹药");
        db.update("update characters set selected_ammo=? where id=?",code,actor);
    }
    public String ammoStatus(String code){return db.queryForObject("select applied_status from item_definitions where code=?",String.class,code);}
    public Projectile projectile(String code){return db.query("select ammo_code,aim_cost,aim_damage from item_definitions where code=? and ammo_code is not null",(r,n)->new Projectile(r.getString(1),r.getInt(2),r.getInt(3)),code).stream().findFirst().orElse(null);}
    public Loadout loadout(UUID id){
        var row=db.queryForMap("select weapon,offhand,body from characters where id=?",id);
        String main=(String)row.get("weapon"),off=(String)row.get("offhand");
        Item mainItem=main==null?null:item(main),offItem=off==null?null:item(off);
        String body=(String)row.get("body");Item bodyItem=item(body);
        int armor=(mainItem==null?0:mainItem.passiveArmor())+(offItem==null?0:offItem.passiveArmor())+(bodyItem==null?0:bodyItem.passiveArmor());
        Item guard=java.util.stream.Stream.of(mainItem,offItem).filter(Objects::nonNull).filter(i->i.guardReduction()>0).findFirst().orElse(null);
        WeaponRules.Weapon weapon=(mainItem!=null&&mainItem.grantsAttack())?weapon(main):WeaponRules.UNARMED;
        return new Loadout(main,off,weapon,offItem,armor,guard!=null,guard==null?0:guard.guardReduction(),guard==null?1:guard.guardMinDamage(),body);
    }
    public void equip(UUID id,String code){
        equip(id,code,null);
    }
    public void equip(UUID id,String main,String off){
        equip(id,main,off,db.queryForObject("select body from characters where id=?",String.class,id));
    }
    public void equip(UUID id,String main,String off,String body){
        Item bodyItem=item(body);
        if(bodyItem!=null&&!bodyItem.kind().equals("armor"))throw AccountService.bad("躯干只能装备护甲");
        Item mainItem=main==null?null:item(main),offItem=off==null?null:item(off);
        if(mainItem!=null&&!(mainItem.kind().equals("weapon")||mainItem.kind().equals("shield")))throw AccountService.bad("主手只能装备武器或双手盾");
        if(offItem!=null&&!offItem.kind().equals("shield"))throw AccountService.bad("副手只能装备盾牌");
        if(mainItem!=null&&mainItem.handUsage().equals("off_one")){offItem=mainItem;mainItem=null;off=offItem.code();main=null;}
        if(mainItem!=null&&mainItem.handUsage().equals("off_two")){offItem=mainItem;mainItem=null;off=offItem.code();main=null;}
        if(mainItem!=null&&mainItem.handUsage().equals("main_two"))off=null;
        if(offItem!=null&&offItem.handUsage().equals("off_two"))main=null;
        if(mainItem!=null&&mainItem.handUsage().equals("none"))throw AccountService.bad("这件物品不能装备");
        if(offItem!=null&&offItem.handUsage().equals("none"))throw AccountService.bad("这件物品不能装备");
        Map<String,Object> old=db.queryForMap("select weapon,offhand,body from characters where id=?",id);
        String oldMain=(String)old.get("weapon"),oldOff=(String)old.get("offhand");
        String oldBody=(String)old.get("body");
        if(Objects.equals(oldMain,main)&&Objects.equals(oldOff,off)&&Objects.equals(oldBody,body))return;
        Map<String,Integer> need=new HashMap<>();
        if(main!=null&&!main.equals(oldMain)&&!main.equals(oldOff))need.merge(main,1,Integer::sum);
        if(off!=null&&!off.equals(oldMain)&&!off.equals(oldOff))need.merge(off,1,Integer::sum);
        if(body!=null&&!body.equals(oldBody))need.merge(body,1,Integer::sum);
        for(var e:need.entrySet())consume(id,e.getKey(),e.getValue());
        if(oldMain!=null&&!oldMain.equals(main)&&!oldMain.equals(off))give(id,oldMain,1);
        if(oldOff!=null&&!oldOff.equals(main)&&!oldOff.equals(off))give(id,oldOff,1);
        if(oldBody!=null&&!oldBody.equals(body))give(id,oldBody,1);
        db.update("update characters set weapon=?,offhand=?,body=? where id=?",main,off,body,id);
    }
    public void give(UUID id,String code,int quantity){
        if(code==null)throw AccountService.bad("请选择武器");
        if(quantity<1||quantity>100)throw AccountService.bad("数量须在1至100之间");
        if(item(code)==null)throw AccountService.bad("物品不存在");
        if(Set.of("unarmed","claws").contains(code))throw AccountService.bad("请选择可发放的物品");
        db.update("insert into inventories(character_id,item_code,quantity) values(?,?,?) on conflict(character_id,item_code) do update set quantity=inventories.quantity+excluded.quantity",id,code,quantity);
    }
    public void consume(UUID id,String code,int quantity){
        if(quantity<1||db.update("update inventories set quantity=quantity-? where character_id=? and item_code=? and quantity-reserved_quantity(character_id,item_code)>=?",quantity,id,code,quantity)==0)throw AccountService.bad("背包中没有足够可用物品，预留材料不能使用");
    }
    public long available(UUID id,String code){return db.queryForObject("select coalesce((select quantity-reserved_quantity(character_id,item_code) from inventories where character_id=? and item_code=?),0)",Long.class,id,code);}
    public boolean has(UUID id,String code){return available(id,code)>0;}
}
