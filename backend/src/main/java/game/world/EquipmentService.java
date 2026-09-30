package game.world;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Item definitions live in data; ownership and slot changes remain server-authoritative. */
@Service
public class EquipmentService {
    private final JdbcTemplate db;
    EquipmentService(JdbcTemplate db){this.db=db;}
    public WeaponRules.Weapon weapon(String code){
        if(code==null||code.equals("unarmed"))return WeaponRules.UNARMED;
        if(code.equals("claws"))return WeaponRules.CLAWS;
        return db.query("select code,name,shape,min_range,max_range,attack_cost,damage,move_rule from item_definitions where code=? and kind='weapon'",
            (r,n)->new WeaponRules.Weapon(r.getString(1),r.getString(2),r.getString(3),r.getInt(4),r.getInt(5),r.getInt(6),r.getInt(7),r.getString(8)),code).stream().findFirst().orElseThrow(()->AccountService.bad("武器不存在"));
    }
    public List<Map<String,Object>> catalog(){return db.queryForList("select * from item_definitions order by code");}
    public void equip(UUID id,String code){
        String old=db.queryForObject("select weapon from characters where id=?",String.class,id);
        if(Objects.equals(old,code))return;
        if(code!=null){weapon(code);if(code.equals("unarmed")||code.equals("claws"))throw AccountService.bad("不能装备此物品");
            if(db.update("update inventories set quantity=quantity-1 where account_id=? and item_code=? and quantity>0",id,code)==0)throw AccountService.bad("背包中没有这件武器");}
        if(old!=null)give(id,old,1);
        db.update("update characters set weapon=? where id=?",code,id);
    }
    public void give(UUID id,String code,int quantity){
        if(code==null)throw AccountService.bad("请选择武器");
        if(quantity<1||quantity>100)throw AccountService.bad("数量须在1至100之间");
        weapon(code);if(Set.of("unarmed","claws").contains(code))throw AccountService.bad("请选择可发放的武器");
        db.update("insert into inventories(account_id,item_code,quantity) values(?,?,?) on conflict(account_id,item_code) do update set quantity=inventories.quantity+excluded.quantity",id,code,quantity);
    }
}
