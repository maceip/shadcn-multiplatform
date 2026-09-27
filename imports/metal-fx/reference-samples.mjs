// Numeric reference evaluation of the pinned GLSL, independently executed in JavaScript.
// This checks equation parity, not a GPU screenshot or SVG rasterization golden.
import { readFileSync, writeFileSync } from 'node:fs';
const source = readFileSync(new URL('./presets.ts', import.meta.url), 'utf8');
const presets = Object.fromEntries(['CHROMATIC', 'SILVER', 'GOLD'].map(name => {
  const literal = source.match(new RegExp(`const ${name}: Preset = ([\\s\\S]*?);`))[1];
  return [name.toLowerCase(), Function(`return (${literal})`)().modes];
}));
const fract = x => x - Math.floor(x);
const mod289 = x => x - Math.floor(x / 289) * 289;
const permute = x => mod289((x * 34 + 1) * x);
function noise(v) {
  const C = [0.211324865405187, 0.366025403784439, -0.577350269189626, 0.024390243902439];
  let i = v.map(x => Math.floor(x + (v[0] + v[1]) * C[1]));
  const x0 = v.map((x, j) => x - i[j] + (i[0] + i[1]) * C[0]);
  const i1 = x0[0] > x0[1] ? [1, 0] : [0, 1];
  const x12 = [x0[0]+C[0]-i1[0], x0[1]+C[0]-i1[1], x0[0]+C[2], x0[1]+C[2]];
  i = i.map(mod289);
  const p = [0,1,2].map(j => permute(permute(i[1]+[0,i1[1],1][j])+i[0]+[0,i1[0],1][j]));
  const points = [x0, x12.slice(0,2), x12.slice(2)];
  let sum = 0;
  for(let j=0;j<3;j++) {
    let m = Math.max(0.5 - points[j][0]**2 - points[j][1]**2, 0)**4;
    const x = 2*fract(p[j]*C[3])-1, h = Math.abs(x)-0.5, a = x-Math.floor(x+0.5);
    m *= 1.79284291400159-0.85373472095314*(a*a+h*h);
    sum += m*(a*points[j][0]+h*points[j][1]);
  }
  return 130*sum;
}
function fbm(p, octaves) {
  let v=0, amplitude=.5;
  for(let i=0;i<Math.trunc(octaves);i++) { v += amplitude*noise(p); p=p.map(x=>x*2); amplitude*=.5; }
  return v;
}
function effect(uv,t,preset) {
  let p=uv.map((x,j)=>(x-.5)*preset.scale+[Math.cos(preset.direction*Math.PI/180),Math.sin(preset.direction*Math.PI/180)][j]*t*.15);
  const f=3+preset.complexity*8;
  let v=Math.sin(p[0]*f+t)+Math.sin(p[1]*f+t*1.3)+Math.sin((p[0]+p[1])*f*.7+t*.7)+Math.sin(Math.hypot(...p)*f*.8-t*1.5);
  const w=[fbm([p[0]+t*.1,p[1]],3+preset.complexity*4),fbm([p[0]+5,p[1]+t*.12+5],3+preset.complexity*4)].map(x=>x*preset.distortion*2);
  v=Math.max(0,Math.min(1,(v+(w[0]+w[1])*preset.distortion)*.2*preset.intensity+.5));
  v=v*v*(3-2*v);
  const weights=[0,.25,.5,.75,1].map((stop,i)=>preset.alphas[i]*Math.exp(-64*(v-stop)**2));
  const colors=preset.colors.slice(0,5).map(hex=>[1,3,5].map(i=>parseInt(hex.slice(i,i+2),16)/255));
  return [0,1,2].map(channel=>weights.reduce((s,w,i)=>s+w*colors[i][channel],0)/(weights.reduce((s,w)=>s+w,0)+.0001));
}
function sample(uv,seconds,preset) {
  const t=seconds*preset.speed;
  const offsets=[[0,0],[.02,0],[-.02,0],[0,.02],[0,-.02]];
  const colors=offsets.map(o=>effect(uv.map((v,i)=>v+o[i]),t,preset));
  const color=[0,1,2].map(i=>colors[0][i]*.4+colors.slice(1).reduce((s,c)=>s+c[i]*.15,0));
  const edge=Math.min(uv[0],1-uv[0],uv[1],1-uv[1]);
  const range=40/96*(1+preset.vignette*3);
  let vig=Math.max(0,Math.min(1,edge*edge/(range*range))); vig=vig*vig*(3-2*vig);
  return color.map(c=>c**1.3*(1+(vig-1)*preset.vignette*preset.vigOpacity));
}
const samples=[];
for (const preset of Object.keys(presets)) for(const dark of [true,false])
  for(const [u,v,seconds] of [[.5,.5,0],[.1,.9,2.5],[.82,.31,11]])
    samples.push({preset,dark,u,v,seconds,rgb:sample([u,v],seconds,presets[preset][dark?'dark':'light'])});
writeFileSync(new URL('./reference-samples.json', import.meta.url), JSON.stringify(samples,null,2)+'\n');
console.log(JSON.stringify(samples));
