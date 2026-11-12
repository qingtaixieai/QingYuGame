package game.world;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.mock.web.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named="PHASE1_DATABASE_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/qingyu_phase1_[a-z0-9_]+")
class HttpContractIntegrationTest {
    @DynamicPropertySource static void config(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->System.getenv("PHASE1_DATABASE_URL"));
        r.add("spring.datasource.username",()->"worldgame");
        r.add("spring.datasource.password",()->"local-only");
        r.add("app.admin-password",()->"phase-one-local-test-only");
        r.add("app.secure-cookie",()->false);
    }
    @Autowired RequestMappingHandlerMapping mappings;
    @Autowired AccountService accounts;
    @Autowired WorldService world;
    @Autowired JdbcTemplate db;
    @Value("${local.server.port}") int port;
    final HttpClient client=HttpClient.newHttpClient();

    HttpResponse<String> get(String path,String cookie) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));
        if(cookie!=null)request.header("Cookie",cookie);
        return client.send(request.GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    Set<String> routes() {
        var result=new TreeSet<String>();
        mappings.getHandlerMethods().forEach((mapping,handler)-> {
            for(String path:mapping.getPatternValues())if(path.startsWith("/api/"))
                for(var method:mapping.getMethodsCondition().getMethods())assertTrue(result.add(method.name()+" "+path),"duplicate route "+path);
        });
        return result;
    }
    @Test void allRegisteredRoutesMatchPreRefactorContract() throws Exception {
        var json=tools.jackson.databind.json.JsonMapper.builder().build();
        var document=json.readTree(Files.readString(Path.of("../contracts/http-routes.json")));
        var expected=new TreeSet<String>();document.get("routes").forEach(v->expected.add(v.asText()));
        assertEquals(expected,routes());
    }
    @Test void everyProtectedGetRejectsAnonymous() throws Exception {
        for(String route:routes())if(route.startsWith("GET ")&&!route.endsWith("/health")) {
            String path=route.substring(4).replaceAll("\\{[^}]+}",UUID.randomUUID().toString())+"?version=test";
            var response=get(path,null);
            assertEquals(401,response.statusCode(),route+" "+response.body());
            assertTrue(response.body().contains("message"),route);
        }
    }
    @Test void approvedPlayerCannotReadAnyAdministratorRoute() throws Exception {
        String name="contract_"+UUID.randomUUID().toString().substring(0,8);
        var a=accounts.register(name,"contract-test-password",world.map().spawn());
        db.update("update accounts set approved=true where id=?",a.id());
        try {
            var request=new MockHttpServletRequest();request.setRemoteAddr("127.0.0.1");
            var response=new MockHttpServletResponse();accounts.login(name,"contract-test-password",request,response);
            String cookie=response.getHeader("Set-Cookie").split(";",2)[0];
            for(String route:routes())if(route.startsWith("GET /api/admin/")) {
                String path=route.substring(4).replaceAll("\\{[^}]+}",a.id().toString());
                assertEquals(403,get(path,cookie).statusCode(),route);
            }
            for(String route:routes())if(route.startsWith("POST /api/admin/")) {
                String path=route.substring(5).replaceAll("\\{[^}]+}",a.id().toString());
                String body=path.endsWith("/approval")?"{\"approved\":true}"
                    :path.endsWith("/password")?"{\"password\":\"contract-test-password\"}"
                    :path.endsWith("/items")?"{\"code\":\"wood\",\"quantity\":1}"
                    :path.endsWith("/behavior")?"{\"behavior\":\"cautious\"}"
                    :path.endsWith("/skills")?"{\"code\":\"test\",\"granted\":true}"
                    :path.contains("/chat/images/")?"{\"approve\":true}"
                    :path.endsWith("/deploy")?"{\"species\":\"claw\",\"q\":0,\"r\":0}"
                    :path.endsWith("/regenerate")?"{\"confirmation\":\"重新生成世界\"}"
                    :path.endsWith("/bans")?"{\"targetType\":\"id\",\"target\":\""+a.id()+"\",\"scope\":\"game\",\"minutes\":1,\"reason\":\"test\"}":"{}";
                var post=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
                    .header("Cookie",cookie).header("X-World-Request","1").header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
                assertEquals(403,client.send(post,HttpResponse.BodyHandlers.ofString()).statusCode(),route);
            }
        } finally {db.update("delete from accounts where id=?",a.id());}
    }
    @Test void inventoryAndSocketStateMatchClientRequiredFields() throws Exception {
        String name="payload_"+UUID.randomUUID().toString().substring(0,8);
        var a=world.register(name,"contract-test-password");
        try {
            var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
            String protocol=Files.readString(Path.of("../frontend/src/shared/protocol.ts"));
            var rows=mapper.readTree(mapper.writeValueAsString(world.inventory(a.id())));
            assertTrue(rows.isArray() && rows.size()>0, "Seeded item catalog must not be empty");
            var declaration=java.util.regex.Pattern.compile("(?m)^export type Item=([^\\r\\n]+)").matcher(protocol);
            assertTrue(declaration.find());
            var fields=GameplaySerializationContractTest.requiredFields(declaration.group(1));
            for(var row:rows)for(String field:fields)
                assertTrue(row.has(field), "Inventory JSON missing client field "+field);
            var state=mapper.readTree(mapper.writeValueAsString(world.snapshot(a.id())));
            declaration=java.util.regex.Pattern.compile("(?m)^export type State=([^\\r\\n]+)").matcher(protocol);
            assertTrue(declaration.find());
            for(String field:GameplaySerializationContractTest.requiredFields(declaration.group(1)))
                assertTrue(state.has(field), "Socket state missing client field "+field);
            assertEquals("state",state.get("type").asText());
        } finally {db.update("delete from accounts where id=?",a.id());}
    }
    @Test void worldRoutePreviewSharesMovementRulesWithoutQueuingMovement() throws Exception {
        var map=world.map();
        var index=map.index();
        var npcLocations=db.query("select q,r from characters where kind<>'player'",
            (row,n)->new WorldMap.Hex(row.getInt(1),row.getInt(2)));
        var battleLocations=db.query("select world_q,world_r from battle_encounters where active",
            (row,n)->new WorldMap.Hex(row.getInt(1),row.getInt(2)));
        var origin=map.tiles().stream().filter(tile->tile.walkable()&&tile.place()==null)
            .map(WorldMap.Tile::hex)
            .filter(h->npcLocations.stream().noneMatch(n->WorldMap.distance(h,n)<5))
            .filter(h->!battleLocations.contains(h)&&h.neighbors().stream()
                .anyMatch(n->index.containsKey(n)&&index.get(n).walkable()&&!battleLocations.contains(n)))
            .findFirst().orElseThrow();
        var target=origin.neighbors().stream()
            .filter(h->index.containsKey(h)&&index.get(h).walkable()&&!battleLocations.contains(h))
            .findFirst().orElseThrow();
        var name="route_"+UUID.randomUUID().toString().substring(0,8);
        var player=world.register(name,"contract-test-password");
        db.update("update accounts set approved=true,entered=true,q=?,r=? where id=?",origin.q(),origin.r(),player.id());
        try {
            var request=new MockHttpServletRequest();request.setRemoteAddr("127.0.0.1");
            var response=new MockHttpServletResponse();accounts.login(name,"contract-test-password",request,response);
            String cookie=response.getHeader("Set-Cookie").split(";",2)[0];
            String query="/api/world/route?q="+target.q()+"&r="+target.r()+"&version=";
            var result=get(query+map.version(),cookie);
            assertEquals(200,result.statusCode(),result.body());
            assertEquals("no-store",result.headers().firstValue("Cache-Control").orElseThrow());
            var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
            var preview=mapper.readValue(result.body(),WorldService.RoutePreview.class);
            assertEquals(new WorldService.RoutePreview(map.version(),origin,List.of(target)),preview);
            assertEquals(400,get(query+"expired-version",cookie).statusCode());
            synchronized(world) {
                assertFalse(worldMoving(player.id()));
                assertThrows(RuntimeException.class,()->world.move(player.id(),target.q(),target.r(),map.version(),target));
                try {
                    assertEquals(preview.path(),world.move(player.id(),target.q(),target.r(),map.version(),preview.origin()));
                    assertTrue(world.previewRoute(player.id(),origin.q(),origin.r(),map.version()).path().isEmpty());
                    assertTrue(worldMoving(player.id()));
                    var account=accounts.get(player.id());
                    assertEquals(origin,new WorldMap.Hex(account.q(),account.r()));
                } finally {world.stop(player.id());}
            }
        } finally {db.update("delete from accounts where id=?",player.id());}
    }
    private boolean worldMoving(UUID id) {
        var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
        var state=mapper.readTree(mapper.writeValueAsString(world.snapshot(id)));
        for(var player:state.get("players"))if(id.toString().equals(player.get("id").asText()))return player.get("moving").asBoolean();
        throw new AssertionError("Test player absent from world state");
    }
    @Test void healthAndMalformedBodyKeepResponseContract() throws Exception {
        assertEquals("{\"status\":\"ok\"}",get("/api/health",null).body());
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/move"))
            .header("X-World-Request","1").header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{" )).build();
        var result=client.send(request,HttpResponse.BodyHandlers.ofString());
        assertEquals(400,result.statusCode());assertTrue(result.body().contains("请求格式无效"));
        var rejected=client.send(HttpRequest.newBuilder(request.uri()).POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(403,rejected.statusCode());
    }
}
