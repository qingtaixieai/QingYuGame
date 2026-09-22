package game.world;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.net.InetAddress;
import java.time.Instant;
import java.util.*;

@Service
public class ModerationService {
    private final JdbcTemplate db;
    ModerationService(JdbcTemplate db){this.db=db;}
    public static String ip(String value){
        try{
            if(value==null||value.length()>45)throw new IllegalArgumentException();
            if(value.contains(":")){if(!value.matches("[0-9a-fA-F:.]+"))throw new IllegalArgumentException();}
            else{String[] parts=value.split("\\.",-1);if(parts.length!=4)throw new IllegalArgumentException();for(String p:parts)if(!p.matches("[0-9]{1,3}")||Integer.parseInt(p)>255)throw new IllegalArgumentException();}
            return InetAddress.getByName(value).getHostAddress();
        }catch(Exception e){throw AccountService.bad("请输入有效的IPv4或IPv6地址");}
    }
    public boolean blocked(UUID id,String ip,String scope){return db.queryForObject("select exists(select 1 from moderation_bans where scope=? and revoked_at is null and (expires_at is null or expires_at>current_timestamp) and ((target_type='account' and target=?) or (target_type='ip' and target=?)))",Boolean.class,scope,id==null?"":id.toString(),ip==null?"":ip);}
    public void requireGame(UUID id,String ip){if(blocked(id,ip,"game"))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"账号或当前IP已被封禁，请联系管理员");}
    public void requireChat(UUID id,String ip){if(blocked(id,ip,"chat"))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"你当前被禁言，暂时不能发送聊天消息");}
    public List<Map<String,Object>> accounts(){return db.queryForList("select id,username,admin,last_login_ip as \"lastIp\",last_login_at as \"lastLogin\" from accounts order by created_at");}
    public List<Map<String,Object>> list(){return db.queryForList("select b.id,b.target_type as \"targetType\",b.target,b.scope,b.reason,b.created_at as \"createdAt\",b.expires_at as \"expiresAt\",b.revoked_at as \"revokedAt\", (b.revoked_at is null and (b.expires_at is null or b.expires_at>current_timestamp)) as active, a.username as \"targetName\" from moderation_bans b left join accounts a on a.id::text=b.target and b.target_type='account' order by b.created_at desc limit 200");}
    public UUID create(UUID actor,String actorIp,String type,String target,String scope,Long minutes,String reason){
        if(!Set.of("account","ip").contains(Objects.requireNonNullElse(type,""))||!Set.of("chat","game").contains(Objects.requireNonNullElse(scope,"")))throw AccountService.bad("封禁类型无效");
        if(minutes!=null&&(minutes<1||minutes>5256000))throw AccountService.bad("时长需为1分钟至10年，或选择永久");
        if(reason!=null&&reason.length()>200)throw AccountService.bad("原因最多200字");
        if(type.equals("account")){
            UUID uid;try{uid=UUID.fromString(target);}catch(Exception e){throw AccountService.bad("账号ID无效");}
            var admins=db.queryForList("select admin from accounts where id=?",Boolean.class,uid);
            if(admins.isEmpty())throw AccountService.bad("账号不存在");
            if(admins.getFirst())throw AccountService.bad("不能封禁管理员账号");target=uid.toString();
        }else{target=ip(target);if(target.equals(actorIp))throw AccountService.bad("不能封禁当前管理员使用的IP");}
        UUID id=UUID.randomUUID();
        db.update("insert into moderation_bans(id,target_type,target,scope,reason,created_by,expires_at) values(?,?,?,?,?,?,?)",id,type,target,scope,Objects.requireNonNullElse(reason,""),actor,minutes==null?null:java.sql.Timestamp.from(Instant.now().plusSeconds(minutes*60)));
        db.update("insert into admin_audit(actor,action,target) values(?,?,?)",actor,"ban-"+scope,type+":"+target);return id;
    }
    public void revoke(UUID actor,UUID id){if(db.update("update moderation_bans set revoked_at=current_timestamp where id=? and revoked_at is null",id)==0)throw AccountService.bad("封禁不存在或已经解除");db.update("insert into admin_audit(actor,action,target) values(?,?,?)",actor,"unban",id.toString());}
}
