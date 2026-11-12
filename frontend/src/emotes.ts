import type {Graphics} from 'pixi.js';

// Shared original pixel artwork: the picker and world bubbles use the same pixels.
export const emotePalette:Record<string,string>={y:'#edc775',l:'#ffe4a5',d:'#5b5944',r:'#d77c67',p:'#efafa0',g:'#6d9e79',w:'#fff7d8',b:'#7fa4ba'};
export const emoteArt=[
 {code:'happy',name:'开心',pixels:['....yyyy....','..yyyyyyyy..','.yyllllllyy.','.ylllllllly.','yyldlllldlyy','yyldlllldlyy','yyllllllll yy'.replace(' ',''),'yyldlllldlyy','.ylddddddy.','.yyllllllyy.','..yyyyyyyy..','....yyyy....']},
 {code:'sad',name:'难过',pixels:['....yyyy....','..yyyyyyyy..','.yyllllllyy.','.ylllllllly.','yyldlllldlyy','yyldlllldlyy','yylbllllllyy','yylblddlllyy','.yldlllldly.','.yyllllllyy.','..yyyyyyyy..','....yyyy....']},
 {code:'angry',name:'生气',pixels:['....rrrr....','..rrrrrrrr..','.rrpppppprr.','.rppppppppr.','rrpdppppdprr','rrppddddpprr','rrpddppddprr','rrpppppppprr','.rppddddppr.','.rrpppppprr.','..rrrrrrrr..','....rrrr....']},
 {code:'question',name:'疑问',pixels:['...gggggg...','..ggwwwwgg..','..gg....gg..','........gg..','.......gg...','......gg....','.....gg.....','.....gg.....','............','.....gg.....','.....gg.....','............']},
 {code:'thumb',name:'赞',pixels:['.....yy.....','.....yly....','.....yly....','....ylly....','...ylllyyyy.','..yllll llly.'.replace(' ',''),'ggyllllllly.','ggyllllllly.','ggyllllllly.','ggylllllly..','ggyyyyyyy...','............']},
 {code:'heart',name:'爱心',pixels:['............','..rrr..rrr..','.rppprrpppr.','rrpprrrrprrr','rrrrrrrrrrrr','rrrrrrrrrrrr','.rrrrrrrrrr.','..rrrrrrrr..','...rrrrrr...','....rrrr....','.....rr.....','............']},
 {code:'help',name:'求助',pixels:['....rrrr....','....rwwr....','....rwwr....','....rwwr....','....rwwr....','....rwwr....','....rrrr....','.....rr.....','............','....rrrr....','....rwwr....','....rrrr....']},
 {code:'ok',name:'收到',pixels:['............','..........gg','.........ggg','........ggg.','.......ggg..','.gg...ggg...','.ggg.ggg....','..ggggg.....','...ggg......','....g.......','............','............']},
] as const;

export function drawEmoteBubble(graphics:Graphics,code:string){
  const art=emoteArt.find(entry=>entry.code===code);
  graphics.clear();
  if(!art)return;
  graphics.poly([-19,-18,19,-18,19,-15,22,-15,22,12,19,12,19,15,5,15,0,20,-3,15,-19,15,-19,12,-22,12,-22,-15,-19,-15])
    .fill(0xfff9e5).stroke({color:0x607753,width:1.5});
  art.pixels.forEach((row,y)=>[...row].forEach((pixel,x)=>{
    const color=emotePalette[pixel];
    if(color)graphics.rect(x*2-12,y*2-13,2,2).fill(color);
  }));
}
