package game.world;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/** Same-origin JSON commands plus a custom header prevent cookie-authenticated CSRF. */
@Component
public class RequestGuard extends OncePerRequestFilter {
    private final String origin;
    private final ConcurrentHashMap<String,Window> attempts=new ConcurrentHashMap<>();
    private record Window(long start,int count) {}
    RequestGuard(@Value("${app.origin}") String origin){this.origin=origin;}
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        res.setHeader("X-Content-Type-Options","nosniff");
        res.setHeader("Referrer-Policy","same-origin");
        if(req.getRequestURI().startsWith("/api")) res.setHeader("Cache-Control","no-store");
        if(!req.getMethod().equals("GET") && !req.getMethod().equals("HEAD")) {
            String supplied=req.getHeader("Origin");
            if(!"1".equals(req.getHeader("X-World-Request")) || (supplied!=null && !origin.equals(supplied))) {res.sendError(403);return;}
            long limit=req.getRequestURI().equals("/api/chat/image")?6L*1024*1024:16384;
            if(req.getContentLengthLong()>limit){res.sendError(413);return;}
            if(req.getRequestURI().equals("/api/login") || req.getRequestURI().equals("/api/register")) {
                long now=System.currentTimeMillis();
                attempts.entrySet().removeIf(e->now-e.getValue().start>60000);
                // RemoteAddr is supplied by the trusted local reverse proxy, not a client-provided identity.
                Window w=attempts.compute(req.getRemoteAddr(),(k,v)->v==null?new Window(now,1):new Window(v.start,v.count+1));
                if(w.count>20 || attempts.size()>10000){res.setHeader("Retry-After","60");res.sendError(429);return;}
            }
        }
        chain.doFilter(req,res);
    }
}
