// 앱 안 3D 차고 페이지 생성: template.html → app/src/main/assets/garage/index.html
//  - dot_faces.js의 도트 표정 8종을 data URI로 넣는다
//    (file:// 페이지에서 외부 그림 파일을 쓰면 캔버스가 오염돼 저장용 정지 그림을 못 찍는다. 그래서 페이지 안에 넣는다)
//  - 색 팔레트는 docs/character/car3d/palette.py(Blender 렌더와 같은 원본)에서 읽어 넣는다
// 차 메시(assets/garage/meshes/*.js)는 docs/character/car3d/export_all.sh 가 Blender로 만든다.
// 실행: node docs/character/garage/build.js
const fs = require("fs");
const path = require("path");
const { dotFaces } = require("./dot_faces.js");
const out = {};
for (const [k, v] of Object.entries(dotFaces)) out[k] = "data:image/svg+xml;base64," + Buffer.from(v).toString("base64");
const palSrc = fs.readFileSync(path.join(__dirname, "../car3d/palette.py"), "utf8");
const palette = {};
const re = /"([a-z_]+)":\s*\("([^"]+)",\s*"([0-9A-Fa-f]{6})",\s*"(\w+)"\)/g;
let m;
while ((m = re.exec(palSrc))) palette[m[1]] = { name: m[2], hex: m[3], finish: m[4] };
// 옆면 데칼: garage/decals/*.svg (2048×742, 가로 = 차 길이 앞→뒤, 세로 = 바닥→지붕)
const decals = {};
for (const file of fs.readdirSync(path.join(__dirname, "decals")).filter((n) => n.endsWith(".svg")))
  decals[file.replace(".svg", "")] = "data:image/svg+xml;base64," + fs.readFileSync(path.join(__dirname, "decals", file)).toString("base64");
const tpl = fs.readFileSync(path.join(__dirname, "template.html"), "utf8");
const target = path.join(__dirname, "../../../app/src/main/assets/garage/index.html");
fs.writeFileSync(target, tpl.replace("/*FACES*/{}", JSON.stringify(out)).replace("/*PALETTE*/{}", JSON.stringify(palette)).replace("/*DECALS*/{}", JSON.stringify(decals)));
console.log("wrote", path.relative(process.cwd(), target), "palette", Object.keys(palette).length);
