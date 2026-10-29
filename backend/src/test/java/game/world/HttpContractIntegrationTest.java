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
