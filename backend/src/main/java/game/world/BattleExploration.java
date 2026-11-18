package game.world;

import java.util.*;
import org.springframework.stereotype.Service;

/** Resolves exploration actions before evaluating each recipient's response. */
@Service
final class BattleExploration {
    record Effect(UUID id,UUID sceneId,int q,int r,String kind,long happenedAt) {}
    record Result(List<UUID> enemies,Effect effect) {}
    private final SkillService skills;
    private final SkillPreparation preparations;
    private final CharacterService characters;
    private final EquipmentService equipment;
    private final SceneRuntime scenes;
    private final NpcRelations relations;
    private final LawService law;

    BattleExploration(SkillService skills,SkillPreparation preparations,CharacterService characters,
                      EquipmentService equipment,SceneRuntime scenes,NpcRelations relations,LawService law){
        this.skills=skills;this.preparations=preparations;this.characters=characters;this.equipment=equipment;
        this.scenes=scenes;this.relations=relations;this.law=law;
    }

    Result act(UUID actor,SceneRuntime.Position from,String action,Integer q,Integer r,long now){
        if(action==null)throw AccountService.bad("请选择动作");
        boolean spell=action.equals("skill:fireball");
        if(spell){
            skills.require(actor,SkillPreparation.FIREBALL);
            if(preparations.state(actor,now)==SkillPreparation.State.NONE&&q==null&&r==null){
                preparations.begin(actor,null,now+3000);
                return new Result(List.of(),null);
            }
        }
        if(!spell&&!action.equals("attack"))throw AccountService.bad("探索中不能使用这个动作");
        if(q==null||r==null)throw AccountService.bad("请选择目标格");
        var scene=scenes.map(from.sceneId());
        var target=new WorldMap.Hex(q,r);
        var valid=new HashSet<>(scene.cells().stream().map(SceneMap.Cell::hex).toList());
        var weapon=characters.weapon(actor);
        List<WorldMap.Hex> affected;
        int damage,piercing=0;
        if(spell){
            if(!valid.contains(target)||WorldMap.distance(from.hex(),target)>6)throw AccountService.bad("请选择六格内的落点");
            preparations.spend(actor,now);affected=List.of(target);damage=SkillPreparation.DAMAGE;
        }else{
            if(!characters.loadout(actor).canAttack())throw AccountService.bad("当前装备无法攻击");
            var projectile=equipment.projectile(weapon.code());
            affected=WeaponRules.cells(weapon,from.hex(),target);
            if(affected.isEmpty()||!valid.containsAll(affected))throw AccountService.bad("请选择武器可攻击的完整范围");
            if(projectile!=null)equipment.consume(actor,equipment.selectedAmmo(actor,projectile.ammoCode()),1);
            preparations.interrupt(actor,now);damage=weapon.damage();piercing=equipment.armorPiercing(weapon.code());
        }
        var enemies=new ArrayList<UUID>();
        for(var recipient:scenes.residents(from.sceneId())){
            if(!affected.contains(recipient.hex())||!spell&&recipient.characterId().equals(actor))continue;
            var before=characters.get(recipient.characterId());
            boolean canReceiveHit=before.alive()||before.life().equals("down");
            if(!canReceiveHit||before.protectedUntil()>now)continue;
            int effective=BattleService.afterArmor(damage,characters.loadout(before.id()).armor(),piercing);
            if(effective>0)characters.damage(before.id(),effective,now);
            if(!relations.receivedHostileAction(before.id(),actor))continue;
            law.hostile(actor,before.id(),"非法攻击",now);
            var after=characters.get(before.id());
            if(after.alive()||after.life().equals("down"))enemies.add(before.id());
        }
        return new Result(List.copyOf(enemies),spell?new Effect(UUID.randomUUID(),scene.id(),q,r,"meteor",now):null);
    }
}
