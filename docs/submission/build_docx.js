// 기획안(markdown) → 제출용 DOCX. 접수양식 8개 항목 순서 그대로. 익명(이름·소속 없음).
// 실행: node docs/submission/build_docx.js  (docs/proposal.md와 docs/character/preview_3d.png를 읽어 같은 폴더에 DOCX 생성. docx npm 패키지 필요)
const fs = require("fs");
const path = require("path");
const {
  Document, Packer, Paragraph, TextRun, HeadingLevel, Table, TableRow, TableCell, WidthType,
  AlignmentType, ShadingType, BorderStyle, ImageRun, LevelFormat, PageOrientation,
} = require("docx");

const md = fs.readFileSync(path.join(__dirname, "..", "proposal.md"), "utf8").split("\n");
const FONT = { ascii: "Malgun Gothic", eastAsia: "Malgun Gothic", hAnsi: "Malgun Gothic" };
const INK = "2F3A47", SOFT = "6B7684", LINE = "D9D4C7", HEAD_BG = "E9F3EE";

// 인라인: **굵게**, `코드`
function runs(text, base = {}) {
  const out = [];
  const re = /(\*\*[^*]+\*\*|`[^`]+`)/g;
  let last = 0, m;
  while ((m = re.exec(text))) {
    if (m.index > last) out.push(new TextRun({ text: text.slice(last, m.index), font: FONT, ...base }));
    const tok = m[0];
    if (tok.startsWith("**")) out.push(new TextRun({ text: tok.slice(2, -2), bold: true, font: FONT, ...base }));
    else out.push(new TextRun({ text: tok.slice(1, -1), font: { ascii: "Consolas", eastAsia: "Malgun Gothic", hAnsi: "Consolas" }, size: 18, color: "445566", ...base }));
    last = m.index + tok.length;
  }
  if (last < text.length) out.push(new TextRun({ text: text.slice(last), font: FONT, ...base }));
  return out;
}

const para = (text, opts = {}) => new Paragraph({ children: runs(text, opts.run || {}), spacing: { after: 120, line: 320 }, ...opts });

function table(rows) {
  const cols = rows[0].length;
  const total = 9000;
  const widths = Array(cols).fill(Math.floor(total / cols));
  widths[cols - 1] += total - widths.reduce((a, b) => a + b, 0);
  const border = { style: BorderStyle.SINGLE, size: 4, color: LINE };
  return new Table({
    width: { size: total, type: WidthType.DXA },
    columnWidths: widths,
    borders: { top: border, bottom: border, left: border, right: border, insideHorizontal: border, insideVertical: border },
    rows: rows.map((cells, r) => new TableRow({
      tableHeader: r === 0,
      children: cells.map((c, i) => new TableCell({
        width: { size: widths[i], type: WidthType.DXA },
        shading: r === 0 ? { type: ShadingType.CLEAR, fill: HEAD_BG, color: "auto" } : undefined,
        margins: { top: 60, bottom: 60, left: 100, right: 100 },
        children: [new Paragraph({ children: runs(c.trim(), { size: 18, bold: r === 0 }), spacing: { after: 0, line: 280 } })],
      })),
    })),
  });
}

const children = [];
// 표지
children.push(new Paragraph({ children: [new TextRun({ text: "앱 프로젝트 소개 / 심사 자료", font: FONT, size: 22, color: SOFT })], spacing: { before: 2400, after: 200 }, alignment: AlignmentType.CENTER }));
children.push(new Paragraph({ children: [new TextRun({ text: "에토스", font: FONT, size: 72, bold: true, color: INK })], spacing: { after: 200 }, alignment: AlignmentType.CENTER }));
children.push(new Paragraph({ children: [new TextRun({ text: "내 운전 습관이 내 차의 캐릭터가 된다", font: FONT, size: 30, color: INK })], spacing: { after: 400 }, alignment: AlignmentType.CENTER }));
children.push(new Paragraph({ children: [new TextRun({ text: "현대자동차그룹 차량용 앱 개발 공모전 · Pleos Connect · 일반인 부문", font: FONT, size: 20, color: SOFT })], alignment: AlignmentType.CENTER, spacing: { after: 300 } }));
// 표지 그림: PNG 머리에서 실제 가로·세로를 읽어 비율대로 넣는다 (고정 크기로 넣으면 찌그러진다)
const cover = fs.readFileSync(path.join(__dirname, "..", "character", "preview_3d.png"));
const coverW = 600, coverH = Math.round(coverW * cover.readUInt32BE(20) / cover.readUInt32BE(16));
children.push(new Paragraph({ children: [new ImageRun({ type: "png", data: cover, transformation: { width: coverW, height: coverH } })], alignment: AlignmentType.CENTER, spacing: { after: 200 } }));
children.push(new Paragraph({ children: [new TextRun({ text: "캐릭터: 운전자의 차가 마스코트(3D). 왼쪽부터 기본형(낮·밤), 꾸민 예(승용·SUV). 차종 7종·전조등 13·후미등 12·튜닝·부품별 색", font: FONT, size: 18, color: SOFT })], alignment: AlignmentType.CENTER, pageBreakAfter: true }));

// 본문: 머리말(> 인용문)과 구분선 건너뛰기
let i = 0;
while (i < md.length && !md[i].startsWith("## ")) i++;
let tableBuf = null;
// 번호 목록 묶음 번호. 번호 줄이 아닌 줄이 끼면 다음 번호 목록은 새 묶음(1부터)
let numInstance = 0, inNumList = false;
const flushTable = () => { if (tableBuf) { children.push(table(tableBuf)); children.push(new Paragraph({ spacing: { after: 120 } })); tableBuf = null; } };
for (; i < md.length; i++) {
  const line = md[i];
  if (line.startsWith("|")) {
    if (/^\|\s*-+/.test(line)) continue; // 구분 줄
    const cells = line.split("|").slice(1, -1);
    (tableBuf ||= []).push(cells);
    continue;
  }
  flushTable();
  const isNum = /^\d+\. /.test(line);
  if (isNum && !inNumList) numInstance++;
  if (line.trim() !== "") inNumList = isNum;
  if (line.startsWith("## ")) {
    children.push(new Paragraph({ children: [new TextRun({ text: line.slice(3), font: FONT, bold: true, size: 32, color: INK })], heading: HeadingLevel.HEADING_1, keepNext: true, spacing: { before: 360, after: 160 }, border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: "4DB38A", space: 4 } } }));
  } else if (line.startsWith("### ")) {
    children.push(new Paragraph({ children: [new TextRun({ text: line.slice(4), font: FONT, bold: true, size: 24, color: INK })], heading: HeadingLevel.HEADING_2, keepNext: true, spacing: { before: 240, after: 100 } }));
  } else if (/^\d+\. /.test(line)) {
    children.push(new Paragraph({ children: runs(line.replace(/^\d+\. /, "")), numbering: { reference: "num", level: 0, instance: numInstance }, spacing: { after: 80, line: 300 } }));
  } else if (line.startsWith("- ")) {
    children.push(new Paragraph({ children: runs(line.slice(2)), numbering: { reference: "bul", level: 0 }, spacing: { after: 80, line: 300 } }));
  } else if (line.startsWith("```")) {
    // 코드 블록: 다음 ``` 까지 고정폭
    let j = i + 1; const buf = [];
    while (j < md.length && !md[j].startsWith("```")) buf.push(md[j++]);
    buf.forEach(b => children.push(new Paragraph({ children: [new TextRun({ text: b, font: { ascii: "Consolas", eastAsia: "Malgun Gothic", hAnsi: "Consolas" }, size: 18 })], spacing: { after: 0 }, indent: { left: 400 } })));
    children.push(new Paragraph({ spacing: { after: 120 } }));
    i = j;
  } else if (line.trim() === "" || line.trim() === "---") {
    continue;
  } else if (line.startsWith("> ")) {
    children.push(para(line.slice(2), { run: { color: SOFT, italics: true } }));
  } else {
    children.push(para(line));
  }
}
flushTable();

const doc = new Document({
  creator: "ethos", title: "에토스 — 앱 프로젝트 소개/심사 자료",
  styles: { default: { document: { run: { font: FONT, size: 20, color: INK } } } },
  numbering: {
    config: [
      { reference: "bul", levels: [{ level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 480, hanging: 240 } } } }] },
      { reference: "num", levels: [{ level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 480, hanging: 300 } } } }] },
    ],
  },
  sections: [{
    properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 1300, bottom: 1300, left: 1400, right: 1400 } } },
    children,
  }],
});
Packer.toBuffer(doc).then(buf => { fs.writeFileSync(path.join(__dirname, "ethos_proposal.docx"), buf); console.log("ok", buf.length); });
