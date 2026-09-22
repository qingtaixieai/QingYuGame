package game.world;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class AccountService {
    public record Account(UUID id,String username,boolean approved,boolean admin,int q,int r,String color) {}
    private final JdbcTemplate db;
    private final ModerationService moderation;
    private final org.springframework.transaction.support.TransactionTemplate tx;
    private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(11);
    private final SecureRandom random=new SecureRandom();
    private final String adminName,adminPassword;
    private final boolean secureCookie;
    private final String dummyHash;
    AccountService(JdbcTemplate db,ModerationService moderation,org.springframework.transaction.PlatformTransactionManager manager,@Value("${app.admin-name}") String name,@Value("${app.admin-password}") String pass,
                   @Value("${app.secure-cookie}") boolean secure) {
        this.db=db;this.moderation=moderation;this.tx=new org.springframework.transaction.support.TransactionTemplate(manager);adminName=name;adminPassword=pass;secureCookie=secure;
        dummyHash=encoder.encode(UUID.randomUUID().toString());
    }
    @PostConstruct void bootstrap() {
        if(db.queryForObject("select count(*) from accounts where admin=true",Integer.class)==0) {
            validate(adminName,adminPassword);
            db.update("insert into accounts(id,username,password_hash,approved,admin,color,q,r) values(?,?,?,true,true,?,-3,3)",
                UUID.randomUUID(),adminName,encoder.encode(adminPassword),"#d39456");
        }
    }
    static void validate(String name,String pass) {
        if(name==null || !name.matches("[\\p{L}\\p{N}_-]{2,24}")) throw bad("用户名需为2～24位文字、数字、下划线或短横线");
        if(pass==null || pass.length()<10 || pass.getBytes(StandardCharsets.UTF_8).length>72) throw bad("密码至少10位，且UTF-8长度不超过72字节");
    }
    public Account register(String name,String password,WorldMap.Hex spawn) {
        validate(name,password);
        String[] colors={"#6a91cf","#b779ab","#60a58a","#c99254","#8f86c3","#ce7971"};
        UUID id=UUID.randomUUID();
        try { db.update("insert into accounts(id,username,password_hash,color,q,r) values(?,?,?,?,?,?)",id,name,
            encoder.encode(password),colors[random.nextInt(colors.length)],spawn.q(),spawn.r()); }
        catch(org.springframework.dao.DuplicateKeyException e) {throw new ResponseStatusException(HttpStatus.CONFLICT,"用户名已被使用");}
        return get(id);
    }
    public Account login(String name,String password,HttpServletRequest request,HttpServletResponse response) {
        if(name==null || name.length()>24 || password==null || password.getBytes(StandardCharsets.UTF_8).length>72) throw unauthorized();
        var rows=db.queryForList("select id,password_hash from accounts where lower(username)=lower(?)",name);
        String hash=rows.isEmpty()?dummyHash:(String)rows.getFirst().get("password_hash");
        if(!encoder.matches(password,hash) || rows.isEmpty()) throw unauthorized();
        UUID id=(UUID)rows.getFirst().get("id");
        String ip=ModerationService.ip(request.getRemoteAddr());moderation.requireGame(id,ip);
        byte[] bytes=new byte[32];random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tx.executeWithoutResult(s->{
            db.update("delete from sessions where account_id=? or expires_at < current_timestamp",id);
            db.update("insert into sessions values(?,?,?)",digest(token),id,java.sql.Timestamp.from(Instant.now().plus(Duration.ofDays(7))));
            db.update("update accounts set last_login_ip=?,last_login_at=current_timestamp where id=?",ip,id);
        });
        response.addHeader("Set-Cookie",cookie(token,604800));
        return get(id);
    }
    public Account get(UUID id) {
        return db.queryForObject("select * from accounts where id=?",(rs,n)->new Account(id,rs.getString("username"),
            rs.getBoolean("approved"),rs.getBoolean("admin"),rs.getInt("q"),rs.getInt("r"),rs.getString("color")),id);
    }
    public List<Account> list() {
        return db.query("select * from accounts order by created_at",(rs,n)->new Account(rs.getObject("id",UUID.class),rs.getString("username"),
            rs.getBoolean("approved"),rs.getBoolean("admin"),rs.getInt("q"),rs.getInt("r"),rs.getString("color")));
    }
    public String token(HttpServletRequest request) {
        if(request.getCookies()!=null) for(Cookie c:request.getCookies()) if(c.getName().equals("WORLD_SESSION")) return c.getValue();
        return "";
    }
    public Account authenticate(String token) {
        if(token==null || token.length()!=43) throw unauthorized();
        var ids=db.query("select account_id from sessions where token_hash=? and expires_at>current_timestamp",(rs,n)->rs.getObject(1,UUID.class),digest(token));
        if(ids.isEmpty()) throw unauthorized();
        moderation.requireGame(ids.getFirst(),null);return get(ids.getFirst());
    }
    public Account require(HttpServletRequest request,boolean approved,boolean admin) {
        Account a=authenticate(token(request));
        moderation.requireGame(a.id,ModerationService.ip(request.getRemoteAddr()));
        if((approved&&!a.approved) || (admin&&!a.admin)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"账号尚未获准进入，或权限已被撤销");
        return a;
    }
    public void logout(HttpServletRequest req,HttpServletResponse res) {
        db.update("delete from sessions where token_hash=?",digest(token(req)));
        res.addHeader("Set-Cookie",cookie("",0));
    }
    public void resetPassword(UUID id,String password) {
        Account a=get(id);validate(a.username,password);
        db.update("update accounts set password_hash=? where id=?",encoder.encode(password),id);
        db.update("delete from sessions where account_id=?",id);
    }
    private String cookie(String token,int age) {return "WORLD_SESSION="+token+"; Path=/; HttpOnly; SameSite=Strict; Max-Age="+age+(secureCookie?"; Secure":"");}
    static String digest(String s) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    static ResponseStatusException bad(String message) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    static ResponseStatusException unauthorized() {return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"请重新登录，或检查用户名和密码");}
}
