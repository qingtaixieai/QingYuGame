import {Mountain, TreePine} from 'lucide-react';
import type {FerryState, Item} from './types';

type Props = {
  ferry: FerryState;
  items: Item[];
  clock: number;
  enabled: boolean;
  onContribute: (item: 'wood' | 'stone', quantity: number) => void;
};

export function FerryPanel({ferry, items, clock, enabled, onContribute}: Props) {
  if (!ferry.built) {
    return <div className="ferry-details">
      <p>建成后，渡船会在两岸之间自动往返。</p>
      {(['wood', 'stone'] as const).map(item => {
        const needed = item === 'wood' ? ferry.woodNeeded : ferry.stoneNeeded;
        const amount = ferry[item];
        const owned = items.find(i => i.code === item)?.quantity ?? 0;
        const disabled = !enabled || !owned || amount >= needed;
        return <div className="ferry-material" key={item}>
          <div>{item === 'wood' ? <TreePine size={15}/> : <Mountain size={15}/>}<b>{item === 'wood' ? '木头' : '石头'}</b><span>{amount} / {needed}</span></div>
          <progress aria-label={item === 'wood' ? '木头进度' : '石头进度'} value={amount} max={needed}/>
          <div>
            <small>背包 {owned}</small>
            <button disabled={disabled} onClick={() => onContribute(item, 1)}>投入 1 个</button>
            <button disabled={disabled} onClick={() => onContribute(item, needed - amount)}>补齐所需</button>
          </div>
        </div>;
      })}
    </div>;
  }

  const docked = ferry.phase === 'mainland' || ferry.phase === 'island';
  const index = ferry.route.findIndex(h => h.q === ferry.q && h.r === ferry.r);
  const step = ferry.travelMs / Math.max(1, ferry.route.length - 1);
  const remaining = docked ? 0 : ferry.phase === 'outbound' ? ferry.route.length - 2 - index : index - 1;
  const seconds = Math.max(0, Math.ceil((ferry.nextAt - clock + Math.max(0, remaining) * step) / 1000));
  const status = {mainland: '停靠望潮港', island: '停靠远屿浅滩', outbound: '驶向远屿浅滩', inbound: '驶向望潮港'}[ferry.phase];

  return <div className="ferry-details">
    <p><strong>{status}</strong> · {docked ? `${seconds} 秒后出发` : `约 ${seconds} 秒后靠岸`}</p>
    <p>船上 {ferry.passengerIds.length} 人 · 停靠 {Math.round(ferry.dwellMs / 1000)} 秒 · 单程约 {Math.round(ferry.travelMs / 1000)} 秒</p>
    <small>靠岸后可下船，留在船上会继续往返。</small>
  </div>;
}
