// 에토스 캐릭터 v2: "운전자의 차" 마스코트. 1024×1024, 4겹(몸·표정·상태 표시·꾸미기) 겹쳐 그리기
// 기준 좌표: 앞유리(얼굴) 중심 (512, 430). 눈 (438, 420)·(586, 420). 입 (512, 505). 바퀴 (340, 790)·(684, 790) r 92
const INK = "#2F3A47";

const defs = `
<defs>
  <linearGradient id="body" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#C9F0DF"/><stop offset=".55" stop-color="#9FDCC2"/><stop offset="1" stop-color="#6FBFA0"/>
  </linearGradient>
  <linearGradient id="roof" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#E6F8F0"/><stop offset="1" stop-color="#B9E8D4"/>
  </linearGradient>
  <linearGradient id="glass" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#FFFFFF"/><stop offset="1" stop-color="#E4F1FB"/>
  </linearGradient>
  <linearGradient id="bumper" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#F7FBF9"/><stop offset="1" stop-color="#D5E3DD"/>
  </linearGradient>
  <radialGradient id="lamp" cx=".4" cy=".35" r=".7">
    <stop offset="0" stop-color="#FFF9DD"/><stop offset=".6" stop-color="#FFE08A"/><stop offset="1" stop-color="#F2B94B"/>
  </radialGradient>
  <radialGradient id="tire" cx=".4" cy=".35" r=".8">
    <stop offset="0" stop-color="#5A6678"/><stop offset="1" stop-color="#232B36"/>
  </radialGradient>
  <radialGradient id="hub" cx=".4" cy=".35" r=".8">
    <stop offset="0" stop-color="#FFFFFF"/><stop offset="1" stop-color="#B8C2CE"/>
  </radialGradient>
  <radialGradient id="shadow" cx=".5" cy=".5" r=".5">
    <stop offset="0" stop-color="#2F3A47" stop-opacity=".22"/><stop offset="1" stop-color="#2F3A47" stop-opacity="0"/>
  </radialGradient>
  <linearGradient id="egg" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#FFFBF2"/><stop offset="1" stop-color="#F6E6CF"/>
  </linearGradient>
  <filter id="soft" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="6"/></filter>
  <filter id="glow" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="14"/></filter>
</defs>`;

const svg = (inner) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" width="1024" height="1024">${defs}${inner}</svg>`;

// 바퀴 하나
const wheel = (cx, cy) => `
  <circle cx="${cx}" cy="${cy}" r="92" fill="url(#tire)"/>
  <circle cx="${cx}" cy="${cy}" r="92" fill="none" stroke="#1B222B" stroke-width="6"/>
  <circle cx="${cx}" cy="${cy}" r="48" fill="url(#hub)"/>
  <circle cx="${cx}" cy="${cy}" r="16" fill="#9AA6B4"/>
  <path d="M${cx - 30} ${cy - 30} l 60 60 M${cx + 30} ${cy - 30} l -60 60" stroke="#C9D2DC" stroke-width="10" stroke-linecap="round"/>`;

const bodies = {
  // 알: 부화 전 2주. 바퀴 자국 무늬
  body_egg: svg(`
    <ellipse cx="512" cy="900" rx="260" ry="46" fill="url(#shadow)"/>
    <path d="M512 160 C 700 160, 800 420, 800 600 C 800 790, 670 900, 512 900 C 354 900, 224 790, 224 600 C 224 420, 324 160, 512 160 Z" fill="url(#egg)" stroke="#D9C3A3" stroke-width="10"/>
    <path d="M400 300 C 460 240, 560 230, 620 290" fill="none" stroke="#fff" stroke-width="26" stroke-linecap="round" opacity=".8"/>
    <g fill="#C9F0DF" opacity=".9">
      <ellipse cx="420" cy="560" rx="70" ry="90" transform="rotate(-18 420 560)"/>
      <ellipse cx="640" cy="700" rx="60" ry="76" transform="rotate(16 640 700)"/>
    </g>
    <g fill="#DCEBFA" opacity=".9"><circle cx="630" cy="440" r="42"/><circle cx="470" cy="760" r="34"/></g>
    <g stroke="#B9E8D4" stroke-width="10" stroke-linecap="round" fill="none" opacity=".9">
      <path d="M330 680 q 30 -18 60 0 q 30 18 60 0"/><path d="M560 560 q 30 -18 60 0 q 30 18 60 0"/>
    </g>
  `),
  // 운전자의 차: 둥근 가상의 소형 전기차. 앞유리가 얼굴 자리
  body_car: svg(`
    <ellipse cx="512" cy="880" rx="400" ry="60" fill="url(#shadow)"/>
    <!-- 차체 -->
    <path d="M160 560 C 160 430, 250 300, 340 250 C 390 222, 634 222, 684 250 C 774 300, 864 430, 864 560 L 864 700 C 864 760, 820 790, 760 790 L 264 790 C 204 790, 160 760, 160 700 Z" fill="url(#body)" stroke="#4FA586" stroke-width="12" stroke-linejoin="round"/>
    <!-- 지붕 광택 -->
    <path d="M300 268 C 360 236, 664 236, 724 268 C 700 252, 324 252, 300 268 Z" fill="#fff" opacity=".7"/>
    <path d="M250 330 C 300 290, 360 272, 420 266" fill="none" stroke="#fff" stroke-width="22" stroke-linecap="round" opacity=".55"/>
    <!-- 앞유리 (얼굴 자리) -->
    <path d="M300 300 C 360 272, 664 272, 724 300 C 760 380, 776 470, 776 560 C 776 584, 758 596, 734 596 L 290 596 C 266 596, 248 584, 248 560 C 248 470, 264 380, 300 300 Z" fill="url(#glass)" stroke="#5EB497" stroke-width="10" stroke-linejoin="round"/>
    <path d="M330 330 C 380 306, 460 296, 520 296" fill="none" stroke="#fff" stroke-width="18" stroke-linecap="round" opacity=".9"/>
    <!-- 범퍼 -->
    <path d="M200 690 L 824 690 C 848 690, 864 706, 864 730 L 864 760 C 864 776, 850 790, 834 790 L 190 790 C 174 790, 160 776, 160 760 L 160 730 C 160 706, 176 690, 200 690 Z" fill="url(#bumper)" stroke="#B8CCC4" stroke-width="8"/>
    <!-- 헤드라이트 (양 볼) -->
    <ellipse cx="300" cy="640" rx="46" ry="34" fill="url(#lamp)" stroke="#E5A93A" stroke-width="6"/>
    <ellipse cx="724" cy="640" rx="46" ry="34" fill="url(#lamp)" stroke="#E5A93A" stroke-width="6"/>
    <ellipse cx="286" cy="630" rx="14" ry="9" fill="#fff" opacity=".9"/>
    <ellipse cx="710" cy="630" rx="14" ry="9" fill="#fff" opacity=".9"/>
    <!-- 충전구 표시 -->
    <rect x="482" y="652" width="60" height="18" rx="9" fill="#4FA586" opacity=".7"/>
    <!-- 바퀴 -->
    ${wheel(340, 790)}${wheel(684, 790)}
  `),
};

// 표정: 앞유리 안. 눈 (432,420)·(592,420), 입 (512,472)
// 원칙: 표정은 눈 모양으로 먼저 갈린다 (작은 화면에서는 입이 잘 안 보임). 선 굵기 24, 끝은 둥글게, 꾸밈 그림 없음.
//   평소 세로 캡슐+작은 미소 · 기쁨 ^ 꺾쇠+벌린 입 · 뿌듯 ^ 꺾쇠(높게)+다문 미소+반짝 · 슬픔 작은 캡슐+처진 눈썹+처진 입
//   아픔 X+물결 입 · 배고픔 캡슐+동그란 입 · 졸림 감은 눈 ‿+짧은 입 · 답답 반쯤 덮인 눈+일자 입
// 차 앞유리에서는 이 그림을 반전해 어두운 유리 속 빛나는 선으로 그린다 (garage/template.html, car3d/car.py)
const EX = [432, 592], EY = 420, MY = 472, W = 24;
const line = (d, w = W) => `<path d="${d}" fill="none" stroke="${INK}" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round"/>`;
const capsule = (cx, ry = 32) => `<rect x="${cx - 17}" y="${EY - ry}" width="34" height="${ry * 2}" rx="17" fill="${INK}"/>`;
const chevron = (cx, y = EY) => line(`M${cx - 38} ${y + 16} L ${cx} ${y - 22} L ${cx + 38} ${y + 16}`);
const closed = (cx) => line(`M${cx - 38} ${EY - 6} Q ${cx} ${EY + 30} ${cx + 38} ${EY - 6}`);
const lidded = (cx) => `<path d="M${cx - 30} ${EY - 2} L ${cx + 30} ${EY - 2} A 30 26 0 0 1 ${cx - 30} ${EY - 2} Z" fill="${INK}"/>` + line(`M${cx - 40} ${EY - 8} L ${cx + 40} ${EY - 8}`, 16);
const cross = (cx) => line(`M${cx - 28} ${EY - 28} L ${cx + 28} ${EY + 28} M${cx + 28} ${EY - 28} L ${cx - 28} ${EY + 28}`);
const smile = (w, d) => line(`M${512 - w} ${MY} Q 512 ${MY + d} ${512 + w} ${MY}`);

const faces = {
  face_normal: svg(`${capsule(EX[0])}${capsule(EX[1])}${smile(30, 26)}`),
  face_happy: svg(`${chevron(EX[0])}${chevron(EX[1])}<path d="M466 ${MY - 6} Q 512 ${MY + 62} 558 ${MY - 6} Z" fill="${INK}" stroke="${INK}" stroke-width="12" stroke-linejoin="round"/>`),
  face_proud: svg(`${chevron(EX[0], EY - 10)}${chevron(EX[1], EY - 10)}${smile(40, 30)}${line("M652 352 L 652 384 M636 368 L 668 368", 12)}`),
  face_sad: svg(`${capsule(EX[0], 26)}${capsule(EX[1], 26)}${line("M396 366 L 456 384 M628 366 L 568 384", 16)}${line(`M482 ${MY + 14} Q 512 ${MY - 12} 542 ${MY + 14}`)}`),
  face_hurt: svg(`${cross(EX[0])}${cross(EX[1])}${line(`M476 ${MY + 4} Q 492 ${MY - 8} 508 ${MY + 4} Q 524 ${MY + 16} 540 ${MY + 4}`, 18)}`),
  face_hungry: svg(`${capsule(EX[0])}${capsule(EX[1])}<ellipse cx="512" cy="${MY + 8}" rx="22" ry="26" fill="none" stroke="${INK}" stroke-width="${W}"/>`),
  face_sleepy: svg(`${closed(EX[0])}${closed(EX[1])}${line(`M498 ${MY + 4} L 526 ${MY + 4}`, 18)}`),
  face_bored: svg(`${lidded(EX[0])}${lidded(EX[1])}${line(`M482 ${MY + 4} L 542 ${MY + 4}`)}`),
};

const marks = {
  // 땀방울: 냉각수 과열 경고등
  mark_sweat: svg(`
    <g filter="url(#glow)" opacity=".5"><circle cx="286" cy="330" r="36" fill="#9CC9F4"/></g>
    <path d="M250 340 C 250 300, 286 272, 286 232 C 286 272, 322 300, 322 340 A 36 36 0 0 1 250 340 Z" fill="#A9D4F7" stroke="#5B9BD5" stroke-width="8"/>
    <ellipse cx="272" cy="330" rx="8" ry="14" fill="#fff" opacity=".8"/>
    <path d="M216 440 C 216 414, 240 396, 240 370 C 240 396, 264 414, 264 440 A 24 24 0 0 1 216 440 Z" fill="#A9D4F7" stroke="#5B9BD5" stroke-width="8"/>
  `),
  // 바퀴 붕대: 타이어 공기압 경고등 (왼쪽 바퀴)
  mark_foot_cast: svg(`
    <circle cx="340" cy="790" r="100" fill="#FFF8E8" stroke="#E9A23B" stroke-width="12"/>
    <g stroke="#E9A23B" stroke-width="10" stroke-linecap="round" opacity=".85">
      <path d="M268 740 L 412 840 M268 840 L 412 740 M340 686 L 340 894 M236 790 L 444 790"/>
    </g>
    <circle cx="340" cy="790" r="100" fill="none" stroke="#fff" stroke-width="4" opacity=".6"/>
  `),
  // 닳은 타이어: 타이어 소모품 '주의'
  mark_worn_shoes: svg(`
    <g stroke="#B0B8C4" stroke-width="12" stroke-linecap="round" fill="none" stroke-dasharray="20 14">
      <path d="M262 840 Q 340 806 418 840"/><path d="M606 840 Q 684 806 762 840"/>
    </g>
    <g stroke="#8A94A3" stroke-width="8" stroke-linecap="round">
      <path d="M300 736 l 22 22 M330 722 l 22 22 M644 736 l 22 22 M674 722 l 22 22"/>
    </g>
    <g fill="#B0B8C4" opacity=".9"><circle cx="300" cy="900" r="6"/><circle cx="324" cy="908" r="5"/><circle cx="648" cy="900" r="6"/><circle cx="672" cy="908" r="5"/></g>
  `),
};

module.exports = { bodies, faces, marks };
