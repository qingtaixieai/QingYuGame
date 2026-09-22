package game.world;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.*;

@Service
public class ChatService {
    private final JdbcTemplate db;private final AccountService accounts;private final ModerationService moderation;private final TransactionTemplate tx;
    private static final Set<String> EMOTES=Set.of("happy","sad","angry","question","thumb","heart","help","ok");
    public record Message(long id,UUID senderId,String username,UUID recipientId,String kind,String body,UUID imageId,boolean imageAvailable,String createdAt) {}
    public record ImageData(byte[] bytes,String mime) {}
    ChatService(JdbcTemplate db,AccountService accounts,ModerationService moderation,PlatformTransactionManager manager){this.db=db;this.accounts=accounts;this.moderation=moderation;tx=new TransactionTemplate(manager);}
    private String where(UUID me,UUID peer,List<Object> args){
        if(peer==null)return "m.recipient_id is null";
        if(peer.equals(me))throw AccountService.bad("不能给自己发私聊");
        args.add(me);args.add(peer);args.add(peer);args.add(me);return "((m.sender_id=? and m.recipient_id=?) or (m.sender_id=? and m.recipient_id=?))";
    }
    private final String select="select m.*,a.username,(i.status='active') as available from chat_messages m join accounts a on a.id=m.sender_id left join chat_images i on i.id=m.image_id ";
    private Message message(java.sql.ResultSet r)throws java.sql.SQLException{return new Message(r.getLong("id"),r.getObject("sender_id",UUID.class),r.getString("username"),r.getObject("recipient_id",UUID.class),r.getString("kind"),r.getString("body"),r.getObject("image_id",UUID.class),r.getBoolean("available"),r.getTimestamp("created_at").toInstant().toString());}
    public Map<String,Object> history(UUID me,UUID peer,Long before){
        List<Object> args=new ArrayList<>();String condition=where(me,peer,args);if(before!=null){condition+=" and m.id<?";args.add(before);}
        var result=new ArrayList<>(db.query(select+"where "+condition+" order by m.id desc limit 41",(rs,n)->message(rs),args.toArray()));
        boolean more=result.size()>40;if(more)result.removeLast();Collections.reverse(result);return Map.of("messages",result,"hasMore",more);
    }
    public Map<String,Object> status(UUID me,String ip){
        boolean unread=db.queryForObject("select exists(select 1 from chat_messages where recipient_id is null and sender_id<>? and id>coalesce((select last_id from chat_reads where account_id=? and conversation='public'),0))",Boolean.class,me,me);
        var contacts=db.queryForList("select a.id,a.username,a.color,exists(select 1 from chat_messages m where m.sender_id=a.id and m.recipient_id=? and m.id>coalesce((select last_id from chat_reads r where r.account_id=? and r.conversation=a.id::text),0)) as unread from accounts a where (a.approved=true or exists(select 1 from chat_messages h where (h.sender_id=a.id and h.recipient_id=?) or (h.recipient_id=a.id and h.sender_id=?))) and a.id<>? order by a.username",me,me,me,me,me);
        return Map.of("publicUnread",unread,"contacts",contacts,"muted",moderation.blocked(me,ip,"chat"),"images",ownImages(me));
    }
    public List<Map<String,Object>> ownImages(UUID me){return db.queryForList("select id,status,mime,size,created_at as \"createdAt\" from chat_images where owner_id=? and status in ('active','pending') order by created_at",me);}
    public boolean read(UUID me,UUID peer,long upTo){
        List<Object> args=new ArrayList<>();String condition=where(me,peer,args);args.add(Math.max(0,upTo));
        Long latest=db.queryForObject("select coalesce(max(m.id),0) from chat_messages m where "+condition+" and m.id<=?",Long.class,args.toArray());
        return db.update("insert into chat_reads(account_id,conversation,last_id) values(?,?,?) on conflict(account_id,conversation) do update set last_id=excluded.last_id where chat_reads.last_id<excluded.last_id",me,peer==null?"public":peer.toString(),latest)>0;
    }
    public synchronized Message send(UUID me,String ip,UUID peer,String kind,String body){return tx.execute(s->{
        if(!accounts.get(me).approved())throw AccountService.bad("账号尚未获准进入");moderation.requireGame(me,ip);moderation.requireChat(me,ip);
        if(peer!=null&&(peer.equals(me)||!accounts.get(peer).approved()))throw AccountService.bad("请选择其他已获准的玩家");
        if(kind==null||!Set.of("text","emote","image").contains(kind))throw AccountService.bad("不支持的消息类型");
        String value=Objects.requireNonNullElse(body,"").strip();UUID image=null;
        if(kind.equals("text")&&(value.isEmpty()||value.length()>1000))throw AccountService.bad("消息需为1至1000个字符");
        if(kind.equals("emote")&&!EMOTES.contains(value))throw AccountService.bad("不支持的表情");
        if(kind.equals("image")){var rows=db.queryForList("select id from chat_images where owner_id=? and status='active'",UUID.class,me);if(rows.isEmpty())throw AccountService.bad("请先上传一张图片");image=rows.getFirst();value="";}
        long now=System.currentTimeMillis();if(db.update("update accounts set last_chat_at=? where id=? and last_chat_at<=?",now,me,now-700)==0)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"发送太快，请稍后再试");
        Long id=db.queryForObject("insert into chat_messages(sender_id,recipient_id,kind,body,image_id) values(?,?,?,?,?) returning id",Long.class,me,peer,kind,value,image);
        return db.queryForObject(select+"where m.id=?",(rs,n)->message(rs),id);
    });}
    public synchronized Map<String,Object> upload(UUID me,String ip,MultipartFile file){
        moderation.requireChat(me,ip);if(file.isEmpty()||file.getSize()>5*1024*1024)throw AccountService.bad("图片不能为空，且最多5MB");
        byte[] bytes;String mime;
        try{bytes=file.getBytes();try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw AccountService.bad("仅支持真实的JPG、PNG或GIF图片");var reader=readers.next();try{reader.setInput(input);String format=reader.getFormatName().toLowerCase(Locale.ROOT);mime=switch(format){case "jpeg","jpg"->"image/jpeg";case "png"->"image/png";case "gif"->"image/gif";default->throw AccountService.bad("仅支持JPG、PNG或GIF图片");};int frames=reader.getNumImages(true);if(frames<1||frames>500)throw AccountService.bad("动图帧数过多，请使用较短的动图");long pixels=0;for(int i=0;i<frames;i++){int w=reader.getWidth(i),h=reader.getHeight(i);pixels+=(long)w*h;if(w<1||h<1||w>4096||h>4096||pixels>40000000)throw AccountService.bad("图片尺寸过大，请缩小后上传");}if(reader.read(0)==null)throw AccountService.bad("无法读取图片");}finally{reader.dispose();}}}
        catch(ResponseStatusException e){throw e;}catch(Exception e){throw AccountService.bad("图片损坏或格式不支持");}
        final String contentType=mime;
        return tx.execute(s->{moderation.requireGame(me,ip);moderation.requireChat(me,ip);
            if(db.queryForObject("select count(*) from chat_images where owner_id=? and status='pending'",Integer.class,me)>0)throw AccountService.bad("已有换图申请，请等待管理员处理");
            boolean active=db.queryForObject("select count(*) from chat_images where owner_id=? and status='active'",Integer.class,me)>0;
            UUID id=UUID.randomUUID();String status=active?"pending":"active";
            db.update("insert into chat_images(id,owner_id,status,mime,data,size) values(?,?,?,?,?,?)",id,me,status,contentType,bytes,bytes.length);
            return Map.of("id",id,"status",status,"message",active?"换图申请已提交，审批期间仍可发送当前图片":"图片已添加，可以重复发送");
        });
    }
    public List<Map<String,Object>> pending(){return db.queryForList("select i.id,i.owner_id as \"ownerId\",a.username,i.mime,i.size,i.created_at as \"createdAt\" from chat_images i join accounts a on a.id=i.owner_id where i.status='pending' order by i.created_at");}
    public synchronized UUID review(UUID actor,UUID id,boolean approve){return tx.execute(s->{
        var rows=db.queryForList("select owner_id from chat_images where id=? and status='pending'",UUID.class,id);if(rows.isEmpty())throw AccountService.bad("申请已处理或不存在");UUID owner=rows.getFirst();
        if(approve)db.update("update chat_images set status='retired',data=null where owner_id=? and status='active'",owner);
        db.update("update chat_images set status=?,data=case when ? then data else null end,reviewed_by=?,reviewed_at=current_timestamp where id=?",approve?"active":"rejected",approve,actor,id);
        db.update("insert into admin_audit(actor,action,target) values(?,?,?)",actor,approve?"approve-image":"reject-image",id.toString());return owner;
    });}
    public ImageData image(AccountService.Account viewer,UUID id){
        var rows=db.query("select data,mime from chat_images i where i.id=? and i.data is not null and (i.owner_id=? or (i.status='pending' and ?=true) or (i.status='active' and exists(select 1 from chat_messages m where m.image_id=i.id and (m.recipient_id is null or m.sender_id=? or m.recipient_id=?))))",(rs,n)->new ImageData(rs.getBytes("data"),rs.getString("mime")),id,viewer.id(),viewer.admin(),viewer.id(),viewer.id());
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"图片已更换或无权查看");return rows.getFirst();
    }
}
