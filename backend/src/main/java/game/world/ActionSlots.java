package game.world;

import java.util.*;

/** Slot references and user choices are independent of action definitions. */
public final class ActionSlots {
    public static final List<String> SOURCES=List.of("common","weapon","item","skill","spell","class");
    public record Slot(String source,int index,String actionRef,boolean locked) {}
    public record Layout(List<Slot> slots,List<String> known) {}
    public static Layout empty(){
        var slots=new ArrayList<Slot>();
        for(String source:SOURCES)for(int i=0;i<6;i++)slots.add(new Slot(source,i,null,false));
        return new Layout(slots,List.of());
    }
    public static Layout reconcile(Layout old,Map<String,String> owned){
        var slots=new ArrayList<Slot>();
        for(var s:old.slots())slots.add(new Slot(s.source(),s.index(),s.actionRef()!=null&&owned.containsKey(s.actionRef())?s.actionRef():null,s.locked()));
        for(String source:SOURCES)if(slots.stream().noneMatch(s->s.source().equals(source)))
            for(int i=0;i<6;i++)slots.add(new Slot(source,i,null,false));
        for(var entry:owned.entrySet())if(!old.known().contains(entry.getKey())){
            int index=-1;
            for(int i=0;i<slots.size();i++){var s=slots.get(i);if(s.source().equals(entry.getValue())&&!s.locked()&&s.actionRef()==null){index=i;break;}}
            if(index<0){int next=(int)slots.stream().filter(s->s.source().equals(entry.getValue())).count();slots.add(new Slot(entry.getValue(),next,entry.getKey(),false));}
            else{var s=slots.get(index);slots.set(index,new Slot(s.source(),s.index(),entry.getKey(),false));}
        }
        return new Layout(List.copyOf(slots),List.copyOf(owned.keySet()));
    }
    public static Layout edit(Layout old,String operation,int from,Integer to,String actionRef,Map<String,String> owned){
        var slots=new ArrayList<>(old.slots());
        if(from<0||from>=slots.size())throw AccountService.bad("槽位不存在");
        var a=slots.get(from);
        if("lock".equals(operation))slots.set(from,new Slot(a.source(),a.index(),a.actionRef(),!a.locked()));
        else {
            if(a.locked())throw AccountService.bad("请先解锁槽位");
            switch(Objects.requireNonNullElse(operation,"")){
                case "clear" -> slots.set(from,new Slot(a.source(),a.index(),null,false));
                case "swap" -> {
                    if(to==null||to<0||to>=slots.size())throw AccountService.bad("目标槽位不存在");
                    var b=slots.get(to);
                    if(b.locked()||!a.source().equals(b.source()))throw AccountService.bad("只能整理同一分类的未锁定槽位");
                    slots.set(from,new Slot(a.source(),a.index(),b.actionRef(),false));
                    slots.set(to,new Slot(b.source(),b.index(),a.actionRef(),false));
                }
                case "assign" -> {
                    if(actionRef==null||!a.source().equals(owned.get(actionRef)))throw AccountService.bad("当前没有这个分类的动作");
                    if(slots.stream().anyMatch(s->actionRef.equals(s.actionRef())))throw AccountService.bad("动作已在栏中");
                    slots.set(from,new Slot(a.source(),a.index(),actionRef,false));
                }
                default -> throw AccountService.bad("整理操作不存在");
            }
        }
        return new Layout(List.copyOf(slots),old.known());
    }
    private ActionSlots(){}
}
