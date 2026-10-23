package game.world;

import java.util.*;

/** One persisted connection owns both ends and its public open/closed state. */
public record ScenePortal(UUID id,Endpoint first,Endpoint second,boolean open) {
    public record Endpoint(UUID sceneId,WorldMap.Hex cell){
        public Endpoint {Objects.requireNonNull(sceneId);Objects.requireNonNull(cell);}
    }
    public ScenePortal {
        Objects.requireNonNull(id);Objects.requireNonNull(first);Objects.requireNonNull(second);
        if(first.sceneId().equals(second.sceneId()))throw new IllegalArgumentException("Portal must connect different scenes");
    }
    public Endpoint destination(Endpoint source){
        if(!first.equals(source)&&!second.equals(source))throw new IllegalArgumentException("Not at this portal");
        if(!open)throw new IllegalStateException("Portal is closed");
        return first.equals(source)?second:first;
    }
}
