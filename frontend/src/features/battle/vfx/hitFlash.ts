export const HIT_FLASH_MS=220;

export function hitFlashTint(age:number){
  const rest=Math.max(0,Math.min(1,(age-70)/(HIT_FLASH_MS-70)));
  const green=Math.round(75+180*rest);
  const blue=Math.round(62+193*rest);
  return 0xff0000|(green<<8)|blue;
}
