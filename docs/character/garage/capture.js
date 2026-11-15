// 꾸미기 전 기본 캐릭터 그림(drawable-nodpi/car3d_face_*.png)을 차고 페이지로 찍는다.
// 앱 없이 열기 위해 Kotlin GarageBridge 대신 가짜 NaechamonApp을 넣은 _capture.html을 assets/garage 에 잠깐 만들고,
// 헤드리스 Chrome으로 표정 8종을 한 줄로 찍은 뒤 ffmpeg로 잘라 낸다. 종족 장식 없음, 기본 차종·색.
// 실행: node docs/character/garage/capture.js  (CHROME, FFMPEG 환경변수로 경로 지정 가능)
const fs = require("fs");
const path = require("path");
const { execFileSync } = require("child_process");
const CHROME = process.env.CHROME || "C:/Program Files/Google/Chrome/Application/chrome.exe";
const FFMPEG = process.env.FFMPEG || "ffmpeg";
const garage = path.join(__dirname, "../../../app/src/main/assets/garage");
const drawables = path.join(__dirname, "../../../app/src/main/res/drawable-nodpi");
const FACES = ["face_normal", "face_happy", "face_sad", "face_hurt", "face_sleepy", "face_hungry", "face_bored", "face_proud"];
const SIZE = 800;
// 항목 목록은 그리기에만 쓰이므로 id만 맞으면 된다 (잠김·문구는 앱이 정한다)
const state = { name: "", plate: "에토스 1호", shape: process.env.SHAPE || "sedan_m", color: process.env.COLOR || "white", finish: "gloss", roof: "none",
  stripe: "none", sticker: "none", wheelStyle: "aero", wheelColor: "silver", lamp: "white", wheelSize: "m",
  head: "none", roofAcc: "none", rear: "none", glasses: "none", blush: "off", fuel: "gas" };
const init = { labels: {}, slots: {}, state, face: "face_normal", species: "none", faces: FACES, nameMax: 10, dark: false };
const mock = `<script>
window.NaechamonApp = { initialState: () => ${JSON.stringify(JSON.stringify(init))}, save() {}, saveSnapshot() {}, done() {}, close() {} };
window.addEventListener('load', async () => {
  await new Promise(r => setTimeout(r, 300));
  const row = document.createElement('div'); row.style.cssText = 'position:fixed;left:0;top:0;display:flex;flex-wrap:wrap;width:${SIZE * FACES.length}px;background:transparent;z-index:9';
  document.body.appendChild(row);
  const put = (f, url) => { const im = new Image(); im.width = im.height = ${SIZE}; im.style.display = 'block'; im.src = url; row.appendChild(im); };
  await window.snapshotAll(put, false);   // 윗줄: 낮
  await window.snapshotAll(put, true);    // 아랫줄: 밤 (전조등·후미등 켜짐)
  document.querySelector('.app').style.display = 'none'; document.body.style.background = 'transparent';
});
</script>`;
const html = fs.readFileSync(path.join(garage, "index.html"), "utf8").replace("<script src=\"three.min.js\">", mock + "\n<script src=\"three.min.js\">");
const tmp = path.join(garage, "_capture.html");
fs.writeFileSync(tmp, html);
const sheet = path.join(__dirname, "capture_sheet.png");
try {
  execFileSync(CHROME, [
    "--headless=new", "--user-data-dir=" + path.join(require("os").tmpdir(), "naechamon_capture"), "--hide-scrollbars",
    "--force-device-scale-factor=1", "--default-background-color=00000000", "--use-angle=swiftshader", "--enable-unsafe-swiftshader",
    `--window-size=${SIZE * FACES.length},${SIZE * 2}`, "--virtual-time-budget=30000", "--screenshot=" + sheet, "file:///" + tmp.replace(/\\/g, "/"),
  ], { stdio: "ignore" });
} finally { fs.unlinkSync(tmp); }
const outDir = process.env.OUT || drawables;
FACES.forEach((f, i) => {
  execFileSync(FFMPEG, ["-v", "error", "-y", "-i", sheet, "-vf", `crop=${SIZE}:${SIZE}:${i * SIZE}:0`, path.join(outDir, "car3d_" + f + ".png")]);
  execFileSync(FFMPEG, ["-v", "error", "-y", "-i", sheet, "-vf", `crop=${SIZE}:${SIZE}:${i * SIZE}:${SIZE}`, path.join(outDir, "car3d_night_" + f + ".png")]);
});
fs.unlinkSync(sheet);
console.log("wrote", FACES.length, "images to", path.relative(process.cwd(), outDir));
