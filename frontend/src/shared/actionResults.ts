import type {ActionResult} from './protocol';

/** Keeps history in the journal, but never replays a snapshot's historical effects. */
export class ActionResultFeed {
  private seen=new Set<string>();
  private initialized=false;
  private newest=0;

  receive(results:readonly ActionResult[],actor:string,clock:number):ActionResult[]{
    const own=results.filter(result=>result.actor===actor);
    const fresh=own.filter(result=>{
      const unseen=!this.seen.has(result.id);this.seen.add(result.id);
      return unseen&&result.happenedAt>=this.newest-60000;
    });
    this.newest=Math.max(this.newest,...own.map(result=>result.happenedAt));
    // Server keeps the latest 100. Bound a long-running client's retained IDs as well.
    if(this.seen.size>1000)this.seen=new Set(own.map(result=>result.id));
    if(!this.initialized){this.initialized=true;return [];}
    return fresh.filter(result=>clock-result.happenedAt<=6000&&clock>=result.happenedAt-1000);
  }
}
