package game.world;

import java.util.*;
import org.springframework.stereotype.Service;

/** Controller-independent definitions. The battle evaluates targets once for UI and execution. */
@Service
public class CombatActions {
    public record Definition(String id,String source,String name,String effect,int cost,int damage,int minRange,int maxRange,String item,String description) {}
    private final EquipmentService equipment;
    private final SkillService skills;
    CombatActions(EquipmentService equipment,SkillService skills){this.equipment=equipment;this.skills=skills;}
    public List<Definition> owned(UUID actor){
        var out=new ArrayList<Definition>();var loadout=equipment.loadout(actor);var weapon=loadout.weapon();var projectile=equipment.projectile(weapon.code());
        out.add(new Definition("move","common","移动","legacy:move",1,0,0,0,null,"沿可达路线移动，每格消耗1行动点。"));
        out.add(new Definition("equipment","common","装备 / 道具","legacy:equipment",2,0,0,0,null,"预览主副手、躯干护甲与道具；确认换装消耗2点。"));
        out.add(new Definition("rescue","common","救援","legacy:rescue",6,5,1,1,null,"救起相邻倒地旅人，恢复5生命并结束回合。"));
        out.add(new Definition("withdraw","common","准备撤离","legacy:withdraw",0,0,0,0,null,"从有效外圈撤离，立即结束回合，下回合离场。"));
        out.add(new Definition("force-withdraw","common","强制撤离","legacy:force-withdraw",6,0,0,0,null,"回合开始已在外圈且保留完整6点时立即离场。"));
        if(loadout.canAttack()&&projectile==null)out.add(new Definition("attack:"+weapon.code(),"weapon",weapon.name(),"legacy:attack",weapon.cost(),weapon.damage(),weapon.minRange(),weapon.maxRange(),null,weapon.damage()+"伤害；预设攻击，范围 "+weapon.minRange()+"–"+weapon.maxRange()+"格。"));
        if(loadout.canGuard())out.add(new Definition("guard:"+loadout.offHand(),"weapon","举盾","legacy:guard",1,0,0,0,null,"将最多2行动点转为反应点；举盾额外减伤"+loadout.guardReduction()+"。"));
        if(equipment.has(actor,"bandage"))out.add(new Definition("bandage","item","绷带","legacy:bandage",2,4,0,1,"bandage","消耗1绷带，治疗自己或相邻站立角色4生命。"));
        if(projectile!=null&&loadout.canAttack()){
            out.add(new Definition("quick-shot","weapon","速射","projectile",weapon.cost(),weapon.damage(),weapon.minRange(),weapon.maxRange(),projectile.ammoCode(),"立即射击一名角色；实际发射时消耗一支箭。"));
            out.add(new Definition("aim","weapon","瞄准","aim",projectile.aimCost(),projectile.aimDamage(),weapon.minRange(),weapon.maxRange(),null,"预设中心及周围一圈；下次自己的行动可选择一名目标射击。移动取消。"));
            out.add(new Definition("aimed-shot","weapon","瞄准射击","aimed_projectile",0,projectile.aimDamage(),0,99,projectile.ammoCode(),"瞄准就绪后，从区域内选择一名角色射击，不额外花行动点。"));
        }
        if(equipment.has(actor,"healing_potion"))out.add(new Definition("potion","item","治疗药水","heal_self",1,6,0,0,"healing_potion","恢复自身6点生命。"));
        if(equipment.has(actor,"stone"))out.add(new Definition("throw-stone","item","投掷石头","projectile",2,2,1,2,"stone","消耗一块石头，立即攻击两格内的一名角色。"));
        for(var s:skills.learned(actor))out.add(new Definition("skill:"+s.code(),"skill",s.name(),s.effect(),s.cost(),0,s.minRange(),s.maxRange(),null,s.description()));
        return List.copyOf(out);
    }
}
