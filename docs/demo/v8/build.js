// 데모 영상 v8 편집: 왼쪽 글 + 오른쪽 앱 화면. node build.js [preview] [shots]
// 이 폴더에 녹화본 raw_a~d.mp4가 있어야 한다 (record_main.sh, record_faces.sh로 만든다)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const DIR = __dirname;
// 실행 파일 위치는 환경 변수로 준다 (예: FFMPEG=ffmpeg, CHROME=chrome)
const FF = process.env.FFMPEG || 'ffmpeg';
const CHROME = process.env.CHROME || 'chrome';
const PREVIEW = process.argv[2] === 'preview';

const W = 2560, H = 1440;
const BG = '#ECEBE7';
// 앱 창: 녹화본에서 앱 영역만 잘라(1706x1210) 0.94배로 놓는다
const WIN = { w: 1604, h: 1156, x: 860, y: 142 };
const CROP = 'crop=1680:1210:854:90';
// zoom: 장면별로 앱 창 대신 잘라 쓸 영역 (가로세로 비율은 WIN과 같게 1604:1156)
const INSET = { x: 120, y: 880, w: 616, h: 336, crop: 'crop=600:327:1410:575' };
// 장면 사이를 겹쳐 넘기는 시간(초). 장면이 뚝 끊기지 않게 앞 장면이 흐려지면서 다음 장면이 나타난다
const XF = 0.6;
// 글을 읽는 데 필요한 시간(초): 글자 수 / 6 + 2. 장면이 이보다 짧으면 마지막 화면을 멈춰 채운다
const needSec = (sc) => (sc.title + sc.body).replace(/<br>|\s/g, '').length / 6 + 2;

// src: 녹화 파일, in/out: 초, speed: 배속
const scenes = [
  { src: 'raw_a.mp4', in: 2.0, out: 9.6, speed: 1, title: '시동을 걸면', body: '인사 한 줄로 시작합니다.<br>처음 2주는 알 상태로 운전 습관을 모읍니다.' },
  { src: 'raw_a.mp4', in: 13, out: 47, speed: 4, title: '주행 중', body: '화면에는 정지 그림 하나뿐입니다.<br>누를 것도, 움직이는 것도 없습니다.<br>기록은 내비에 가려져도 계속됩니다.', note: '4배속' },
  { src: 'raw_a.mp4', in: 96.0, out: 102.0, speed: 1, title: '2주가 차면', body: '그동안의 주행 기록으로 운전 성향을 판정합니다.<br>짧은 주행이 잦으면 ‘동네 다람쥐형’입니다.' },
  { src: 'raw_a.mp4', in: 102.3, out: 107.8, speed: 1, title: '주차하면', body: '상태 네 칸과 정비 기준 한 줄이 보입니다.<br>눈으로만 보는 동승자에게는 여기까지면 충분합니다.' },
  { src: 'raw_a.mp4', in: 108.3, out: 111.8, speed: 1, zoom: 'crop=1220:879:1274:190', title: '누르면 상세', body: '소모품마다 남은 거리와 교체 주기를 보여 줍니다.<br>취급설명서에 주기가 없는 항목은 ‘권장값’이라고 구분해 적습니다.' },
  { src: 'raw_a.mp4', in: 115.0, out: 121.0, speed: 1, title: '운전 습관이<br>정비 주기를 정합니다', body: '짧은 주행과 정체가 잦으면 취급설명서의 가혹 조건에 해당합니다.<br>엔진오일을 10,000km가 아니라 7,500km마다 바꾸도록 안내합니다.' },
  { src: 'raw_c.mp4', in: 6.5, out: 17.5, speed: 1, inset: true, title: '급정거하면', body: '표정이 잠깐 시무룩해집니다.<br>조금 뒤 원래 표정으로 돌아옵니다.<br>소리도 움직임도 없이 그림만 바뀝니다.', note: '2배속' },
  { src: 'raw_d.mp4', in: 4.5, out: 13.5, speed: 1, inset: true, title: '정체가 길어지면', body: '답답한 표정으로 바뀝니다.<br>표정은 상황이 바뀔 때만 바뀝니다.', note: '4배속' },
  { src: 'raw_a.mp4', in: 151.6, out: 158.2, speed: 1, title: '안전 경고가 켜지면', body: '캐릭터는 빠지고 안내문만 남습니다.<br>문구는 “점검을 받아보세요”까지입니다.<br>고장이라고 단정하지 않습니다.' },
  { src: 'raw_b.mp4', in: 12.6, out: 19.4, speed: 1, zoom: 'crop=1100:793:1414:130', title: '월간 점검표', body: '경고등과 소모품을 여덟 부위로 정리합니다.<br>방금 켜졌던 공기압 경고는 ‘관찰 필요’로 남습니다.' },
  { src: 'raw_b.mp4', in: 22.4, out: 36.2, speed: 1, title: '정비를 기록하면', body: '정비 수첩에 도장이 찍힙니다.<br>관리 등급은 정비 기록으로만 오릅니다.<br>주행거리로는 오르지 않습니다.' },
  { src: 'raw_b.mp4', in: 40.0, out: 46.4, speed: 1, title: '내 차 설정', body: '차급과 색을 내 차에 맞춥니다.<br>정차 중에만 열리고, 달리기 시작하면 바로 닫힙니다.' },
  { src: 'raw_b.mp4', in: 49.6, out: 66.4, speed: 1.6, title: '꾸미기는<br>정비 기록으로 열립니다', body: '잠긴 항목은 정비를 기록해야 열립니다.<br>방금 엔진오일을 기록해 휠 하나가 열렸습니다.', note: '1.6배속' },
  { src: 'raw_b.mp4', in: 67.0, out: 75.6, speed: 1, title: '저장하면', body: '주행 화면과 주차 화면의 캐릭터가 내 차로 바뀝니다.' },
  { src: 'raw_b.mp4', in: 80.5, out: 89.0, speed: 1, inset: true, insetCrop: 'crop=520:284:1985:535', title: '세워 둔 동안', body: '캐릭터가 천천히 숨쉬고, 가끔 표정을 바꿉니다.<br>달리기 시작하면 바로 멈춥니다.' },
];

const css = `
*{box-sizing:border-box;margin:0;padding:0;word-break:keep-all}
html,body{width:${W}px;height:${H}px;background:transparent;overflow:hidden}
body{font-family:'Noto Sans KR','Noto Sans CJK KR',sans-serif;color:#1b1b1a;-webkit-font-smoothing:antialiased}
.pg{position:absolute;inset:0;display:none}
.pg:target{display:block}
.hole{position:absolute;left:${WIN.x}px;top:${WIN.y}px;width:${WIN.w}px;height:${WIN.h}px;border-radius:18px;
  box-shadow:0 28px 80px -18px rgba(20,20,18,.22),0 0 0 9999px ${BG};outline:1px solid rgba(20,20,18,.10);outline-offset:-1px}
.foot{position:absolute;left:100px;bottom:80px;font-size:30px;font-weight:400;color:#6f6e69;letter-spacing:.01em}
.mark{position:absolute;left:100px;top:80px;font-family:'Noto Serif KR','Noto Serif CJK KR',serif;font-size:40px;font-weight:600;letter-spacing:-.01em}
.col{position:absolute;left:100px;top:0;width:700px;height:${H}px;display:flex;flex-direction:column;justify-content:center}
.rule{width:44px;height:2px;background:#1b1b1a;margin-bottom:38px}
.t{font-family:'Noto Serif KR','Noto Serif CJK KR',serif;font-size:78px;font-weight:600;line-height:1.28;letter-spacing:-.025em}
.b{font-size:42px;font-weight:500;line-height:1.6;color:#3d3c39;margin-top:36px;letter-spacing:-.012em}
.col.hasin{height:830px;justify-content:flex-end}
.ins{position:absolute;left:${INSET.x}px;top:${INSET.y}px;width:${INSET.w}px;height:${INSET.h}px;border-radius:14px;box-shadow:0 0 0 18px ${BG};outline:1px solid rgba(20,20,18,.12);outline-offset:-1px}
.insl{position:absolute;left:${INSET.x + 2}px;top:${INSET.y + INSET.h + 14}px;font-size:30px;color:#6f6e69}
.note{position:absolute;right:${W - WIN.x - WIN.w + 4}px;top:${WIN.y + WIN.h + 22}px;font-size:30px;color:#6f6e69}
.card{background:${BG}}
.card .c{position:absolute;left:120px;top:0;height:${H}px;display:flex;flex-direction:column;justify-content:center;width:1500px}
.name{font-family:'Noto Serif KR','Noto Serif CJK KR',serif;font-size:168px;font-weight:700;letter-spacing:-.04em;line-height:1.05}
.sub{font-size:56px;font-weight:400;color:#555451;margin-top:44px;line-height:1.6;letter-spacing:-.015em}
.car{position:absolute;right:140px;top:50%;transform:translateY(-50%);width:1000px}
.three{display:flex;gap:64px;margin-top:84px}
.three div{font-size:40px;line-height:1.55;color:#3d3c39;border-top:2px solid #1b1b1a;padding-top:22px;width:540px}
.three b{display:block;font-family:'Noto Serif KR','Noto Serif CJK KR',serif;font-weight:600;font-size:46px;color:#1b1b1a;margin-bottom:8px}
`;

let html = `<!doctype html><html lang="ko"><head><meta charset="utf-8"><style>${css}</style></head><body>`;
// 창 틀: 배경 + 둥근 구멍 + 표시
html += `<div class="pg" id="frame"><div class="hole"></div><div class="mark">에토스</div><div class="foot">Pleos Connect 에뮬레이터 화면 · 시나리오 재생으로 촬영</div></div>`;
scenes.forEach((s, i) => {
  html += `<div class="pg" id="cap${i}"><div class="col${s.inset ? ' hasin' : ''}"><div class="rule"></div><div class="t">${s.title}</div><div class="b">${s.body}</div></div>${s.note ? `<div class="note">${s.note}</div>` : ''}${s.inset ? '<div class="ins"></div><div class="insl">앞유리 표정 확대</div>' : ''}</div>`;
});
html += `<div class="pg card" id="title"><div class="c"><div class="name">에토스</div><div class="sub">운전 습관에 맞춰 정비 시기를 알려 주는<br>차량 관리 앱</div></div><img class="car" src="car_normal.png"><div class="foot">현대자동차그룹 차량용 앱 개발 공모전 · Pleos Connect</div></div>`;
html += `<div class="pg card" id="end"><div class="c" style="width:2200px"><div class="t" style="font-size:92px">습관이 나아지면,<br>정비 주기도 달라집니다.</div><div class="three"><div><b>주행 중</b>그림 한 장만 보입니다. 누를 것도 움직이는 것도 없습니다.</div><div><b>정차 중</b>리포트, 점검표, 정비 기록, 내 차 설정을 엽니다.</div><div><b>안전 경고</b>캐릭터 없이 안내문만 보여 줍니다.</div></div></div><div class="mark">에토스</div><div class="foot">시나리오 재생 모드로 촬영 · Pleos Connect Emulator v2.1.0</div></div>`;
html += `</body></html>`;
fs.writeFileSync(path.join(DIR, 'caps.html'), html);

const shot = (id, transparent) => {
  const out = path.join(DIR, `${id}.png`);
  const args = ['--headless=new', '--hide-scrollbars', `--window-size=${W},${H}`, '--force-device-scale-factor=1', '--virtual-time-budget=4000'];
  if (transparent) args.push('--default-background-color=00000000');
  args.push(`--screenshot=${out}`, 'file:///' + path.join(DIR, 'caps.html').replace(/\\/g, '/') + '#' + id);
  execFileSync(CHROME, args, { stdio: 'ignore' });
  return out;
};
if (process.argv.includes('shots') || !fs.existsSync(path.join(DIR, 'frame.png'))) {
  shot('frame', true); scenes.forEach((_, i) => shot('cap' + i, true)); shot('title', false); shot('end', false);
}
if (process.argv.includes('shotsonly')) process.exit(0);

const ENC = PREVIEW
  ? ['-r', '30', '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '30', '-pix_fmt', 'yuv420p', '-an']
  : ['-r', '30', '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-pix_fmt', 'yuv420p', '-an'];
const run = (args) => execFileSync(FF, ['-v', 'error', '-y', ...args], { stdio: 'inherit', cwd: DIR });

// 장면 조각과 길이. 맨 끝에서 XF초씩 겹쳐 이어 붙인다
const list = [];
const card = (id, dur) => {
  const out = `c_${id}.mp4`;
  run(['-loop', '1', '-t', String(dur), '-i', `${id}.png`, '-vf', 'fps=30', ...ENC, out]);
  list.push({ file: out, dur });
};
card('title', 4.5);
scenes.forEach((s, i) => {
  const out = `c_${String(i).padStart(2, '0')}.mp4`;
  const play = (s.out - s.in) / s.speed;
  // 겹쳐 넘기는 동안은 글을 읽기 어려우므로 그만큼 더 멈춰 둔다
  const hold = Math.max(0, needSec(s) - play) + XF;
  const dur = play + hold;
  const pad = hold > 0 ? `,tpad=stop_mode=clone:stop_duration=${hold.toFixed(3)}` : '';
  const fc = [
    `color=c=${BG.replace('#', '0x')}:s=${W}x${H}:r=30:d=${dur.toFixed(3)}[bg]`,
    s.inset
      ? `[0:v]split[m][n];[m]${s.zoom || CROP},scale=${WIN.w}:${WIN.h}:flags=lanczos,setpts=(PTS-STARTPTS)/${s.speed},fps=30${pad}[v];[n]${s.insetCrop || INSET.crop},scale=${INSET.w}:${INSET.h}:flags=lanczos,setpts=(PTS-STARTPTS)/${s.speed},fps=30${pad}[ins]`
      : `[0:v]${s.zoom || CROP},scale=${WIN.w}:${WIN.h}:flags=lanczos,setpts=(PTS-STARTPTS)/${s.speed},fps=30${pad}[v]`,
    `[bg][v]overlay=${WIN.x}:${WIN.y}[a]`,
    s.inset ? `[a][1:v]overlay=0:0[b0];[b0][ins]overlay=${INSET.x}:${INSET.y}[b]` : `[a][1:v]overlay=0:0[b]`,
    `[b][2:v]overlay=0:0[o]`,
  ].join(';');
  run(['-ss', String(s.in), '-to', String(s.out), '-i', s.src, '-loop', '1', '-t', dur.toFixed(3), '-i', 'frame.png', '-loop', '1', '-t', dur.toFixed(3), '-i', `cap${i}.png`,
    '-filter_complex', fc, '-map', '[o]', '-t', dur.toFixed(3), ...ENC, out]);
  list.push({ file: out, dur });
});
card('end', 8);
// 이어 붙이기: 앞 장면과 다음 장면을 XF초 동안 겹친다 (xfade). 맨 처음과 맨 끝만 밝아졌다 어두워진다
const final = PREVIEW ? 'preview.mp4' : 'ethos_demo_v8.mp4';
let chain = '', prev = '[0:v]', at = 0;
for (let k = 1; k < list.length; k++) {
  at += list[k - 1].dur - XF;
  chain += `${prev}[${k}:v]xfade=transition=fade:duration=${XF}:offset=${at.toFixed(3)}[x${k}];`;
  prev = `[x${k}]`;
}
const total = at + list[list.length - 1].dur;
chain += `${prev}fade=in:st=0:d=0.5,fade=out:st=${(total - 0.6).toFixed(3)}:d=0.6,format=yuv420p[o]`;
run([...list.flatMap(c => ['-i', c.file]), '-filter_complex', chain, '-map', '[o]', ...ENC, '-movflags', '+faststart', final]);
console.log('length', total.toFixed(1), 's');
console.log('done', final);
