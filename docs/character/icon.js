// 앱 아이콘 전경(적응형 아이콘 foreground): 흰 선으로 그린 차 옆모습 + 눈 두 점 (라인 모노그램).
// Pleos 기본 앱 아이콘(검정 바탕·흰 선)과 같은 줄에 놓여도 어색하지 않게 한다. 배경은 res/drawable/ic_launcher_background.xml(어두운 회색 그라데이션).
// 108×108 단위 캔버스(적응형 아이콘 108dp), 투명 배경. 안전 영역은 가운데 지름 66 → 그림은 지름 60 안에 둔다.
// 사용: node docs/character/icon.js → docs/character/ic_launcher_foreground.svg 생성 후, 헤드리스 Chrome으로 mipmap PNG(108·162·216·324·432px)를 뽑는다
const fs = require("fs");
const path = require("path");

// 512 캔버스에서 그린 선 차(가로 110~426, 세로 200~346)를 안전 영역(지름 60)에 맞춰 0.18배로 줄여 가운데에 둔다
const foreground = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="108" height="108">
  <g transform="translate(54 56) scale(0.18) translate(-268 -266)">
    <g fill="none" stroke="#F4F5F7" stroke-width="18" stroke-linecap="round" stroke-linejoin="round">
      <path d="M110 300 C 110 272 134 264 162 260 L 204 214 C 214 204 228 200 244 200 L 318 200 C 336 200 352 208 364 222 L 392 258 C 414 262 426 274 426 292 L 426 318 L 110 318 Z"/>
      <circle cx="166" cy="318" r="28"/><circle cx="356" cy="318" r="28"/>
    </g>
    <g fill="#F4F5F7"><circle cx="232" cy="244" r="9"/><circle cx="296" cy="244" r="9"/></g>
  </g>
</svg>`;
fs.writeFileSync(path.join(__dirname, "ic_launcher_foreground.svg"), foreground);
console.log("wrote ic_launcher_foreground.svg");
