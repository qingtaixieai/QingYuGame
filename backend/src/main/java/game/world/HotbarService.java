package game.world;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** Called under the world monitor and transaction, including reconciliation on reads. */
@Service
public class HotbarService {
    public record Action(String id,String source,String name,String icon,String command,String description,int cost,String costKind,Long quantity,String disabledReason) {}
    public record State(long revision,List<ActionSlots.Slot> slots,List<Action> actions) {}
    public record Edit(long revision,String operation,int from,Integer to,String actionRef) {}
    private record Saved(long revision,ActionSlots.Layout layout) {}
    private final JdbcTemplate db;
    private final CharacterService characters;
    private final JsonMapper json=JsonMapper.builder().build();
    HotbarService(JdbcTemplate db,CharacterService characters){this.db=db;this.characters=characters;}
    private List<Action> owned(UUID id){
        var l=characters.loadout(id);var out=new ArrayList<Action>();
        out.add(action("move","common","移动","move","move","沿可达路线移动，每格消耗1行动点。",1,"movement",null));
        out.add(action("equipment","common","装备 / 道具","equipment","equipment","预览主副手与道具；确认换装消耗2点，取消不消耗。",2,"action",null));
        out.add(action("rescue","common","救援","rescue","rescue","救起相邻倒地旅人，恢复5生命并结束回合。",6,"action",null));
        out.add(action("withdraw","common","准备撤离","withdraw","withdraw","从有效外圈撤离，立即结束回合，下回合离场。",0,"turn",null));
        out.add(action("force-withdraw","common","强制撤离","withdraw","force-withdraw","回合开始已在外圈且保留完整6点时立即离场。",6,"action",null));
        if(l.canAttack()){var w=l.weapon();out.add(action("attack:"+w.code(),"weapon",w.name(),"attack","attack",w.damage()+"伤害；预设攻击，范围 "+w.minRange()+"–"+w.maxRange()+"格。",w.cost(),"action",null));}
        if(l.canGuard())out.add(action("guard:"+l.offHand(),"weapon","举盾","guard","guard","将最多2行动点转为反应点；举盾额外减伤"+l.guardReduction()+"。",1,"reaction",null));
        long bandages=db.queryForObject("select coalesce((select quantity from inventories where account_id=? and item_code='bandage'),0)",Long.class,id);
        if(bandages>0)out.add(action("bandage","item","绷带","bandage","bandage","消耗1绷带，治疗自己或相邻站立角色4生命。",2,"action",bandages));
        return out;
    }
    private static Action action(String id,String source,String name,String icon,String command,String description,int cost,String kind,Long quantity){return new Action(id,source,name,icon,command,description,cost,kind,quantity,"");}
    private Map<String,String> ownership(List<Action> actions){var owned=new LinkedHashMap<String,String>();actions.forEach(a->owned.put(a.id(),a.source()));return owned;}
    private Saved reconcile(UUID id,List<Action> actions){
        var rows=db.query("select revision,document from action_layouts where account_id=?",(r,n)->new Saved(r.getLong(1),json.readValue(r.getString(2),ActionSlots.Layout.class)),id);
        var old=rows.isEmpty()?new Saved(0,ActionSlots.empty()):rows.getFirst();
        var next=ActionSlots.reconcile(old.layout(),ownership(actions));
        if(rows.isEmpty()||!next.equals(old.layout())){
            var saved=new Saved(old.revision()+1,next);save(id,saved);return saved;
        }
        return old;
    }
    private void save(UUID id,Saved saved){db.update("insert into action_layouts(account_id,revision,document) values(?,?,?) on conflict(account_id) do update set revision=excluded.revision,document=excluded.document",id,saved.revision(),json.writeValueAsString(saved.layout()));}
    public void sync(UUID id){reconcile(id,owned(id));}
    public void edit(UUID id,Edit edit){
        var actions=owned(id);var saved=reconcile(id,actions);
        if(edit.revision()!=saved.revision())throw new ResponseStatusException(HttpStatus.CONFLICT,"栏位已更新，请重新整理");
        var next=ActionSlots.edit(saved.layout(),edit.operation(),edit.from(),edit.to(),edit.actionRef(),ownership(actions));
        if(!next.equals(saved.layout()))save(id,new Saved(saved.revision()+1,next));
    }
    @SuppressWarnings("unchecked")
    public State state(UUID id,Object battle){
        var actions=owned(id);var saved=reconcile(id,actions);
        var b=(Map<String,Object>)battle;
        var actors=(List<BattleService.Actor>)b.getOrDefault("actors",List.of());
        var own=actors.stream().filter(a->a.accountId().equals(id)).findFirst().orElse(null);
        int points=(Integer)b.getOrDefault("turnPoints",0);
        String common=own==null?"当前不在战斗中":!own.character().alive()?"倒地或灵魂状态无法行动":own.entryRound()>(Integer)b.get("round")?"下一轮开始行动":!id.equals(b.get("turnAccountId"))?"现在不是你的回合":"";
        var available=new ArrayList<Action>();
        for(var a:actions){
            int cost=a.cost();String reason=common;
            if(own!=null&&a.command().equals("guard"))cost=Math.max(0,Math.min(2-own.reactionPoints(),points));
            if(reason.isEmpty()){
                if(points<a.cost())reason="行动点不足";
                else if(a.command().equals("guard")&&own.reactionPoints()>=2)reason="反应点已满";
                else if(a.command().equals("rescue")&&actors.stream().noneMatch(t->t.character().life().equals("down")&&!t.character().npc()&&distance(own,t)==1))reason="没有相邻倒地旅人";
                else if(a.command().equals("bandage")&&actors.stream().noneMatch(t->t.character().alive()&&t.character().hp()<t.character().maxHp()&&distance(own,t)<=1))reason="身边没有需要治疗的角色";
                else if(a.command().equals("move")&&new WorldMap.Hex(own.q(),own.r()).neighbors().stream().noneMatch(h->WeaponRules.distance(h,new WorldMap.Hex(0,0))<=BattleService.RADIUS&&actors.stream().noneMatch(t->t.q()==h.q()&&t.r()==h.r())))reason="没有可达空格";
                else if(a.command().contains("withdraw")){
                    var edges=(List<BattleService.Edge>)b.get("edges");
                    if(BattleService.sides(own.q(),own.r()).stream().noneMatch(d->edges.get(d).walkable()))reason="需要站在可撤离外圈";
                    else if(a.command().equals("force-withdraw")&&!Boolean.TRUE.equals(b.get("turnStartEdge")))reason="本回合开始时不在外圈";
                }
            }
            available.add(new Action(a.id(),a.source(),a.name(),a.icon(),a.command(),a.description(),cost,a.costKind(),a.quantity(),reason));
        }
        return new State(saved.revision(),saved.layout().slots(),available);
    }
    private static int distance(BattleService.Actor a,BattleService.Actor b){return WeaponRules.distance(new WorldMap.Hex(a.q(),a.r()),new WorldMap.Hex(b.q(),b.r()));}
}
