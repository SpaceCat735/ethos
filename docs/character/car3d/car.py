# 에토스 캐릭터 차 v2: Blender 5.2 헤드리스용. 옆 실루엣·평면 폭 곡선으로 단면을 촘촘히 만들어 매끈한 소형 크로스오버 몸을 만들고
# 앞유리에 표정 그림을 입혀 투명 배경 PNG로 렌더한다.
# 사용: blender -b --python car.py -- --face face_normal --color pearl --out out.png [--samples 128] [--size 1200] [--glb out.glb] [--view hero|side|front|rear]
import bpy, bmesh, math, sys, os
from mathutils import Vector

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
def arg(name, default):
    return argv[argv.index(name) + 1] if name in argv else default
FACE = arg("--face", "face_normal")
COLOR = arg("--color", "pearl")
OUT = arg("--out", os.path.join(os.path.dirname(__file__), "out.png"))
SAMPLES = int(arg("--samples", "128"))
SIZE = int(arg("--size", "1200"))
GLB = arg("--glb", "")
VIEW = arg("--view", "hero")
HERE = os.path.dirname(os.path.abspath(__file__))
FACE_PNG = os.path.join(arg("--faces", os.path.join(HERE, "faces")), FACE + ".png")

sys.path.insert(0, HERE)
from palette import PALETTE, FINISH, srgb_to_linear
_pname, _phex, _pfin = PALETTE.get(COLOR, PALETTE["white"])
PAINT = srgb_to_linear(_phex)
# 시안(스타일) 변형: 비율·램프·앞유리 처리만 바꾼다. cur = 지금 모양
STYLE = arg("--style", "crisp")   # 기본 = 채택한 시안 C(크리스프). cur 등 옛 시안은 비교용으로 남긴다
ST = {
    "cur": {},
    # 시안 1 라운지 모노리스: 짧은 오버행·긴 대시, 큰 휠, 뒤로 오르는 벨트, 한 줄 캐릭터 라인, 검은 패널 속 라이트바, 어두운 유리 속에서 빛나는 표정
    "mono": dict(cowl=.36, wsend=.03, wheel_t=.72, wheel_mul=1.10, arch=0.05, trim_lv=1, charline=True, tumble=.78, lamp="panel", face="glow",
                 belt=[(1.0, .52), (.6, .53), (0, .55), (-.66, .58), (-1.0, .60)]),
    # 시안 2 페블: 한 호로 이어진 지붕, 네 모서리 바퀴, 차체색 무광 휠커버, 카울 선 라이트, 흡기구 없음, 차체색 틴트 유리 + 잉크 표정
    "pebble": dict(wheel_t=.76, roof="arc", wheel="cover", lamp="cowl", grille=False, face="tint", arch=0.06),
    # 시안 3 현재형 다듬기: 실루엣 유지, 두꺼운 라이트바(슬릿 없음), 표정 영역 확대, 위쪽 선팅 그라데이션
    "plus": dict(lamp="bar", face="big", span=1.9),
    # ---- 2차 시안: 각진 디자인 언어. gen="blade" = 12점 단면(문턱·사이드 블레이드·어깨 선반·지붕 모서리) + 직선 보간 + 접힘선 1.0 ----
    # 시안 A 블레이드 슬래브: subsurf 1단계, 평평한 옆판에 칼날 선 하나, 팔각 휠 아치, 검은 패널 속 라이트바, 어두운 유리 속 발광 표정
    "blade": dict(gen="blade", linear=True, subd=1, crease={2: .7, 3: 1, 4: 1, 6: 1, 7: 1, 9: 1}, end=(0.06, 0.08), end_crease=1.0,
                  arch_v=8, arch_rot=math.pi / 8, arch=0.07, bevel=(0.008, 2), tire=(0.015, 2), rim=.80, lamp="panel", face="glow", trim_lv=2, glass_lv=7, ws_lv=8),
    # 시안 B 패싯 셸: subsurf 없이 큰 평면을 접은 다면체, 육각 휠 아치, 밝은 유리 + 잉크 표정(선팅)
    "facet": dict(gen="blade", linear=True, subd=0, crease={}, end=(0.05, 0.06), end_crease=0,
                  arch_v=6, arch_rot=math.pi / 6, arch=0.07, bevel=(0.012, 2), tire=(0.015, 2), rim=.80, lamp="panel", face="big", span=2.0, trim_lv=2, glass_lv=7, ws_lv=8),
    # 시안 C 크리스프: 같은 12점 단면을 subsurf 2단계 + 접힘선 1.0으로 매끈하되 날이 서게, 둥근 아치 유지
    "crisp": dict(span=1.65, detail=True, gen="blade", linear=False, subd=2, crease={2: .8, 3: 1, 4: 1, 6: 1, 7: 1, 9: 1}, end=(0.08, 0.10), end_crease=0.7,
                  arch_v=40, arch_rot=0, arch=0.07, bevel=(0.01, 3), tire=(0.02, 3), rim=.78, lamp="panel", face="glow", trim_lv=2, glass_lv=7, ws_lv=9),
}[STYLE]
P_MET, P_ROUGH, P_COAT, P_COATR = FINISH[arg("--finish", _pfin)]

# ---------- 장면 초기화 ----------
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.render.engine = "CYCLES"
scene.cycles.device = "GPU"
prefs = bpy.context.preferences.addons["cycles"].preferences
prefs.compute_device_type = "OPTIX" if any(d.type == "OPTIX" for d in prefs.get_devices_for_type("OPTIX")) else "CUDA"
for d in prefs.devices: d.use = (d.type != "CPU")
scene.cycles.samples = SAMPLES
scene.cycles.use_denoising = True
scene.render.resolution_x = scene.render.resolution_y = SIZE
scene.render.film_transparent = True
scene.render.image_settings.file_format = "PNG"
scene.render.image_settings.color_mode = "RGBA"
scene.view_settings.view_transform = "AgX"
scene.view_settings.look = "AgX - Medium High Contrast"

def mat(name, color=(0.8, 0.8, 0.8), metallic=0.0, rough=0.4, coat=0.0, coat_rough=0.05, emission=None, strength=0.0):
    m = bpy.data.materials.new(name); m.use_nodes = True
    bsdf = m.node_tree.nodes["Principled BSDF"]
    bsdf.inputs["Base Color"].default_value = (*color, 1)
    bsdf.inputs["Metallic"].default_value = metallic
    bsdf.inputs["Roughness"].default_value = rough
    bsdf.inputs["Coat Weight"].default_value = coat
    bsdf.inputs["Coat Roughness"].default_value = coat_rough
    if emission:
        bsdf.inputs["Emission Color"].default_value = (*emission, 1)
        bsdf.inputs["Emission Strength"].default_value = strength
    return m

M_PAINT = mat("paint", PAINT, metallic=P_MET, rough=P_ROUGH, coat=P_COAT, coat_rough=P_COATR)
# 옆면 데칼: 차 길이×높이를 한 장으로 보는 옆 투영 UV("decal")로 그림을 도장 위에 얹는다. 옆을 보는 면에만 나온다
DECAL = arg("--decal", "")
if DECAL and os.path.exists(DECAL):
    nt = M_PAINT.node_tree; n = nt.nodes; l = nt.links; bsdf = n["Principled BSDF"]
    tex = n.new("ShaderNodeTexImage"); tex.image = bpy.data.images.load(DECAL); tex.extension = "CLIP"
    uv = n.new("ShaderNodeUVMap"); uv.uv_map = "decal"; l.new(uv.outputs[0], tex.inputs["Vector"])
    geo = n.new("ShaderNodeNewGeometry"); sepn = n.new("ShaderNodeSeparateXYZ"); l.new(geo.outputs["Normal"], sepn.inputs[0])
    ab = n.new("ShaderNodeMath"); ab.operation = "ABSOLUTE"; l.new(sepn.outputs["X"], ab.inputs[0])
    gt = n.new("ShaderNodeMath"); gt.operation = "GREATER_THAN"; gt.inputs[1].default_value = 0.55; l.new(ab.outputs[0], gt.inputs[0])
    fac = n.new("ShaderNodeMath"); fac.operation = "MULTIPLY"; l.new(tex.outputs["Alpha"], fac.inputs[0]); l.new(gt.outputs[0], fac.inputs[1])
    mixd = n.new("ShaderNodeMix"); mixd.data_type = "RGBA"; mixd.inputs[6].default_value = (*PAINT, 1)
    l.new(fac.outputs[0], mixd.inputs["Factor"]); l.new(tex.outputs["Color"], mixd.inputs[7]); l.new(mixd.outputs[2], bsdf.inputs["Base Color"])
M_TRIM = mat("trim", (0.035, 0.038, 0.042), rough=0.5, coat=0.3)
M_GLASS = mat("glass", (0.03, 0.04, 0.05), rough=0.04, coat=1.0, coat_rough=0.02)
M_TIRE = mat("tire", (0.025, 0.026, 0.028), rough=0.75)
M_RIM = mat("rim", (0.78, 0.80, 0.82), metallic=1.0, rough=0.22)
M_PLATE = mat("plate", (0.85, 0.86, 0.88), rough=0.4)
M_CHROME = mat("chrome", (0.62, 0.64, 0.66), metallic=1.0, rough=0.30)   # 새틴 크롬(창틀 몰딩·루프레일)
M_RIM_DARK = mat("rim_dark", (0.06, 0.065, 0.07), metallic=0.6, rough=0.35)
M_LAMP = mat("lamp", (0.95, 0.97, 1.0), rough=0.2, emission=(0.93, 0.96, 1.0), strength=9.0)
M_TAIL = mat("tail", (0.6, 0.08, 0.06), rough=0.2, emission=(1.0, 0.12, 0.08), strength=5.0)
NIGHT = "--night" in argv   # 밤 렌더: 주변을 어둡게, 전조등·후미등을 밝게
# ---- 1단계 디테일 재질 ----
def glassy(name, color, trans=1.0, rough=0.02, ior=1.5):
    m = mat(name, color, rough=rough); b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Transmission Weight"].default_value = trans; b.inputs["IOR"].default_value = ior
    return m
M_PIANO = mat("piano", (0.012, 0.013, 0.015), rough=0.08, coat=1.0, coat_rough=0.02)      # 유광 검정(램프 하우징·손잡이)
M_LENS = glassy("lens", (0.98, 0.99, 1.0))                                                # 헤드램프 투명 커버
M_TAILLENS = glassy("tail_lens", (0.75, 0.03, 0.03), rough=0.04)                          # 후미등 붉은 커버
M_AMBER = mat("amber", (0.95, 0.45, 0.04), rough=0.15)                                       # 방향지시등(국내 규정 호박색, 평소 꺼짐)
M_REFL = mat("refl", (0.82, 0.83, 0.85), metallic=1.0, rough=0.10)                        # 램프 안 반사판
M_DISC = mat("disc", (0.22, 0.22, 0.23), metallic=1.0, rough=0.55)                        # 브레이크 디스크
M_MIRROR = mat("mirror", PAINT, metallic=P_MET, rough=P_ROUGH, coat=P_COAT, coat_rough=P_COATR)   # 미러 머리 (장식 색 칸이 바꾼다)
M_MOLD = mat("molding", (0.62, 0.64, 0.66), metallic=1.0, rough=0.30)   # 창틀 몰딩·손잡이 줄 (장식 색 칸이 바꾼다)
M_SKID = mat("skid", (0.55, 0.57, 0.60), rough=0.55)   # 수지 하부 보호판(은회색)
M_CARBON = mat("carbon", (0.05, 0.055, 0.06), metallic=0.3, rough=0.3, coat=1.0, coat_rough=0.05)   # 카본 무늬 판
M_CALIPER = mat("caliper", (0.10, 0.105, 0.11), rough=0.35, coat=0.8)                     # 캘리퍼 (무채색)
M_SEAT = mat("seat", (0.045, 0.045, 0.05), rough=0.85)                                    # 좌석 직물
M_DASH = mat("dash", (0.025, 0.026, 0.028), rough=0.55)                                   # 대시보드
if NIGHT:
    M_LAMP.node_tree.nodes["Principled BSDF"].inputs["Emission Strength"].default_value = 40.0
    M_TAIL.node_tree.nodes["Principled BSDF"].inputs["Emission Strength"].default_value = 22.0
if ST.get("detail"):
    # 옆·지붕 유리: 짙은 틴트지만 빛이 통해 실내가 비친다
    gb = M_GLASS.node_tree.nodes["Principled BSDF"]
    gb.inputs["Transmission Weight"].default_value = 0.6; gb.inputs["Base Color"].default_value = (0.06, 0.07, 0.08, 1); gb.inputs["IOR"].default_value = 1.5
    # 차체 안쪽 면(유리 너머로 보이는 껍데기 뒷면)은 어둡게: 실내가 어둡게 보여야 진짜 차 같다
    for m_ in (M_PAINT, M_TRIM):
        nt_ = m_.node_tree; out = nt_.nodes["Material Output"]; pr = nt_.nodes["Principled BSDF"]
        geo_ = nt_.nodes.new("ShaderNodeNewGeometry"); dk_ = nt_.nodes.new("ShaderNodeBsdfDiffuse"); dk_.inputs["Color"].default_value = (0.008, 0.008, 0.009, 1)
        mixs = nt_.nodes.new("ShaderNodeMixShader")
        nt_.links.new(geo_.outputs["Backfacing"], mixs.inputs[0]); nt_.links.new(pr.outputs[0], mixs.inputs[1]); nt_.links.new(dk_.outputs[0], mixs.inputs[2])
        nt_.links.new(mixs.outputs[0], out.inputs["Surface"])
    # 타이어: 원주 방향 홈 4줄 + 잔결 (그림자만 바꾸는 범프. 모양은 그대로)
    nt = M_TIRE.node_tree; tb_ = nt.nodes["Principled BSDF"]
    tc = nt.nodes.new("ShaderNodeTexCoord"); wv = nt.nodes.new("ShaderNodeTexWave"); wv.wave_type = "BANDS"; wv.bands_direction = "Z"
    wv.inputs["Scale"].default_value = 3.2; wv.inputs["Distortion"].default_value = 0.0
    nz = nt.nodes.new("ShaderNodeTexNoise"); nz.inputs["Scale"].default_value = 900.0
    mx = nt.nodes.new("ShaderNodeMath"); mx.operation = "MAXIMUM"
    bp = nt.nodes.new("ShaderNodeBump"); bp.inputs["Strength"].default_value = 0.55; bp.inputs["Distance"].default_value = 0.006
    nt.links.new(tc.outputs["Object"], wv.inputs["Vector"]); nt.links.new(wv.outputs["Fac"], mx.inputs[0]); nt.links.new(nz.outputs["Fac"], mx.inputs[1])
    nt.links.new(mx.outputs[0], bp.inputs["Height"]); nt.links.new(bp.outputs["Normal"], tb_.inputs["Normal"])
    # 디스크: 가는 방사형 연마 자국
    dn = M_DISC.node_tree; db = dn.nodes["Principled BSDF"]; dn_ = dn.nodes.new("ShaderNodeTexNoise"); dn_.inputs["Scale"].default_value = 400.0
    dbp = dn.nodes.new("ShaderNodeBump"); dbp.inputs["Strength"].default_value = 0.2; dn.links.new(dn_.outputs["Fac"], dbp.inputs["Height"]); dn.links.new(dbp.outputs["Normal"], db.inputs["Normal"])
    # 도장: 클리어코트에 아주 옅은 결(오렌지 필), 메탈릭·펄은 바탕에 금속 입자(플레이크), 펄은 박막 무지개빛
    pn_ = M_PAINT.node_tree; pb = pn_.nodes["Principled BSDF"]
    op = pn_.nodes.new("ShaderNodeTexNoise"); op.inputs["Scale"].default_value = 45.0
    opb = pn_.nodes.new("ShaderNodeBump"); opb.inputs["Strength"].default_value = 0.025
    pn_.links.new(op.outputs["Fac"], opb.inputs["Height"]); pn_.links.new(opb.outputs["Normal"], pb.inputs["Coat Normal"])
    if P_MET >= 0.2:
        fl = pn_.nodes.new("ShaderNodeTexVoronoi"); fl.inputs["Scale"].default_value = 2200.0
        flb = pn_.nodes.new("ShaderNodeBump"); flb.inputs["Strength"].default_value = 0.35; flb.inputs["Distance"].default_value = 0.0004
        pn_.links.new(fl.outputs["Distance"], flb.inputs["Height"]); pn_.links.new(flb.outputs["Normal"], pb.inputs["Normal"])
    if arg("--finish", _pfin) == "pearl":
        pb.inputs["Thin Film Thickness"].default_value = 380.0; pb.inputs["Thin Film IOR"].default_value = 1.55
M_WELL = mat("well", (0.01, 0.01, 0.012), rough=0.9)
M_COVER = mat("cover", tuple(c * 0.9 for c in PAINT), metallic=0.1, rough=0.6)

# 앞유리: 옅은 유리 바탕 위에 표정 그림(채도 낮춤)을 알파로 얹는다
FACEMODE = ST.get("face", "cur")
M_FACE = mat("windshield", (0.03, 0.04, 0.05) if FACEMODE == "glow" else (0.80, 0.86, 0.91), rough=0.06, coat=1.0, coat_rough=0.02)
if ST.get("detail") and "--wsoft" in argv:
    # 표정이 읽히도록 앞유리 반사를 약하게 (천장 조명이 비쳐 표정을 덮지 않게)
    _fb = M_FACE.node_tree.nodes["Principled BSDF"]
    _fb.inputs["Coat Weight"].default_value = 0.15; _fb.inputs["Specular IOR Level"].default_value = 0.12; _fb.inputs["Roughness"].default_value = 0.18
if os.path.exists(FACE_PNG) and FACEMODE == "glow":
    # 어두운 유리 속에서 표정 선이 은은하게 빛난다 (그림을 반전해 밝은 선으로, 알파만큼 발광)
    nt = M_FACE.node_tree; n = nt.nodes; l = nt.links
    bsdf = n["Principled BSDF"]
    tex = n.new("ShaderNodeTexImage"); tex.image = bpy.data.images.load(FACE_PNG); tex.extension = "CLIP"
    uv = n.new("ShaderNodeUVMap"); uv.uv_map = "face"; l.new(uv.outputs[0], tex.inputs["Vector"])
    inv = n.new("ShaderNodeInvert"); l.new(tex.outputs["Color"], inv.inputs["Color"])
    mul = n.new("ShaderNodeMath"); mul.operation = "MULTIPLY"; mul.inputs[1].default_value = 6.0 if ST.get("detail") else 3.0
    l.new(tex.outputs["Alpha"], mul.inputs[0])
    l.new(inv.outputs[0], bsdf.inputs["Emission Color"]); l.new(mul.outputs[0], bsdf.inputs["Emission Strength"])
elif os.path.exists(FACE_PNG):
    nt = M_FACE.node_tree; n = nt.nodes; l = nt.links
    bsdf = n["Principled BSDF"]
    tex = n.new("ShaderNodeTexImage"); tex.image = bpy.data.images.load(FACE_PNG); tex.extension = "CLIP"
    uv = n.new("ShaderNodeUVMap"); uv.uv_map = "face"
    l.new(uv.outputs[0], tex.inputs["Vector"])
    hsv = n.new("ShaderNodeHueSaturation"); hsv.inputs["Saturation"].default_value = 1.0 if FACEMODE == "tint" else 0.25
    l.new(tex.outputs["Color"], hsv.inputs["Color"])
    grad = n.new("ShaderNodeValToRGB")
    if FACEMODE == "tint":
        # 차체색을 밝힌 틴트 유리: 아래가 조금 더 진하다
        lo = tuple(p + (1 - p) * 0.70 for p in PAINT); hi = tuple(p + (1 - p) * 0.88 for p in PAINT)
        grad.color_ramp.elements[0].color = (*lo, 1); grad.color_ramp.elements[1].color = (*hi, 1)
        grad.color_ramp.elements[0].position = 0.40; grad.color_ramp.elements[1].position = 0.62
    elif FACEMODE == "big":
        # 위쪽 선팅: 아래 밝고 위로 갈수록 어둡게
        grad.color_ramp.elements[0].color = (0.88, 0.92, 0.95, 1); grad.color_ramp.elements[1].color = (0.30, 0.36, 0.42, 1)
        grad.color_ramp.elements[0].position = 0.60; grad.color_ramp.elements[1].position = 0.95
    else:
        grad.color_ramp.elements[0].color = (0.62, 0.72, 0.80, 1); grad.color_ramp.elements[1].color = (0.90, 0.94, 0.97, 1)
        grad.color_ramp.elements[0].position = 0.45; grad.color_ramp.elements[1].position = 0.65
    sep = n.new("ShaderNodeSeparateXYZ"); l.new(uv.outputs[0], sep.inputs[0]); l.new(sep.outputs["Y"], grad.inputs["Fac"])
    mix = n.new("ShaderNodeMix"); mix.data_type = "RGBA"; mix.blend_type = "MIX"
    l.new(tex.outputs["Alpha"], mix.inputs["Factor"]); l.new(grad.outputs["Color"], mix.inputs[6]); l.new(hsv.outputs[0], mix.inputs[7])
    l.new(mix.outputs[2], bsdf.inputs["Base Color"])

# ---------- 몸: 곡선으로 단면을 촘촘히 ----------
def catmull(ctrl, y):
    # ctrl: [(y, v)] y 내림차순(앞→뒤). 사이를 Catmull-Rom으로 보간
    ys = [c[0] for c in ctrl]
    if y >= ys[0]: return ctrl[0][1]
    if y <= ys[-1]: return ctrl[-1][1]
    for i in range(len(ctrl) - 1):
        if ys[i] >= y >= ys[i + 1]:
            p0 = ctrl[max(i - 1, 0)][1]; p1 = ctrl[i][1]; p2 = ctrl[i + 1][1]; p3 = ctrl[min(i + 2, len(ctrl) - 1)][1]
            t = (ys[i] - y) / (ys[i] - ys[i + 1])
            return 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t * t * t)
    return ctrl[-1][1]

BODY = arg("--body", "suv_m")
sys.path.insert(0, HERE); from bodies import BODIES, PROFILES
_L, _W, _H, WHEEL_R, _kind = BODIES[BODY]; P = dict(PROFILES[_kind])
from bodies import BLADE_PROFILES, shape_profile
# 각진 언어는 차종마다 따로 잡은 실루엣(bodies.SHAPE_SPECS)을 쓴다. 없으면 차급 공용
if ST.get("gen") == "blade": P = dict(shape_profile(BODY) or BLADE_PROFILES[_kind])
if ST.get("gen") == "blade": ST = dict(ST, arch=dict(sedan=0.035, suv=0.05, mini=0.04).get(_kind, 0.05))   # 스탠스: 바퀴가 아치를 꽉 채우게 (세단 3.5 cm, SUV 5 cm)
for k in ("cowl", "wsend", "wheel_t", "belt"):
    if k in ST: P[k] = ST[k]
if ST.get("roof") == "arc":
    P["top"] = [(1.0, .50), (.85, .57), (.6, .70), (.3, .83), (0, .89), (-.3, .89), (-.6, .84), (-.85, .72), (-1.0, .62)]
WHEEL_R *= ST.get("wheel_mul", 1.0)
HL = _L / 2
def sc(curve, mul): return [(t * HL, v * mul) for t, v in curve]
Y_FRONT, Y_REAR = HL, -HL
# 앞·뒤 바퀴 중심 (앞 +, 뒤는 크기만). 앞뒤 오버행이 차종마다 달라 대칭이 아니다
WHEEL_YF = HL * P.get("wheel_tf", P["wheel_t"]); WHEEL_YR = HL * P.get("wheel_tr", P["wheel_t"])
if "wheel_t" in ST: WHEEL_YF = WHEEL_YR = HL * ST["wheel_t"]
WHEEL_Y = WHEEL_YF
WHEELS_Y = (WHEEL_YF, -WHEEL_YR)
TOP = sc(P["top"], _H); BELT = sc(P["belt"], _H); FLOOR = sc(P["floor"], _H)
WBELT = sc(P["wbelt"], _W / 2); WTOP = sc(P["wtop"], _W / 2)
# 타이어 폭: 차종별 실차 비례 (경차 175, 중형 세단 245 …)
TIRE_W = dict(mini=0.175, sedan_s=0.225, sedan_m=0.245, sedan_l=0.255, suv_s=0.235, suv_m=0.255, suv_l=0.265).get(BODY, 0.25) if ST.get("detail") else 0.25
# 바퀴 위치: 타이어 바깥면(사이드월 가장 볼록한 곳)이 그 자리 펜더선보다 안쪽에 오게 (차체 밖으로 나오면 국내 튜닝 기준 위반)
WHEEL_X = min(catmull(WBELT, WHEEL_YF), catmull(WBELT, -WHEEL_YR)) - TIRE_W / 2 - (0.006 if ST.get("detail") else 0.035)
COWL = HL * P["cowl"]; WSHIELD_END = HL * P["wsend"]
SP_Y, SP_Z = -HL + 0.09, catmull(TOP, -HL + 0.12) + 0.01

def linear(ctrl, y):
    # ctrl: [(y, v)] y 내림차순. 꺾이는 점 사이를 직선으로 (면이 접히는 각진 언어)
    ys = [c[0] for c in ctrl]
    if y >= ys[0]: return ctrl[0][1]
    if y <= ys[-1]: return ctrl[-1][1]
    for i in range(len(ctrl) - 1):
        if ys[i] >= y >= ys[i + 1]:
            t = (ys[i] - y) / (ys[i] - ys[i + 1]); return ctrl[i][1] + (ctrl[i + 1][1] - ctrl[i][1]) * t
    return ctrl[-1][1]
interp = linear if ST.get("linear") else catmull

# 어깨 턱(벨트에서 유리까지 안쪽으로 들어가는 폭)과 지붕 평면이 시작하는 비율 (차종 실루엣에서 덮어쓸 수 있다)
SHOULDER = P.get("shoulder", 0.035); ROOF_FLAT = P.get("roof_flat", 0.80)
# 앞뒤 끝 입체감 (bodies.SHAPE_SPECS의 nose·tail, m). 끝에서 END_ZONE 안쪽까지 서서히 줄어든다 (단면이 서로 뒤집히지 않게 넉넉히)
# 턱언더: 옆면 아래(문턱)가 안으로 말려 들어가는 깊이 (m). 낮은 세단일수록 깊게 → 차가 바퀴 위에 올라선 것처럼 보인다
TUCK = P.get("tuck", dict(sedan=0.065, suv=0.045, mini=0.035).get(_kind, 0.04))
NOSE = tuple(P.get("nose", (0, 0, 0))); TAIL = tuple(P.get("tail", (0, 0))); END_ZONE = 0.60
def section_blade(y):
    # 각진 언어 단면(반쪽 12점): 바닥, 문턱 모서리, 사이드 블레이드 아래/날/위, 벨트, 어깨 선반, 유리 중간, 지붕 모서리, 지붕 평면
    top = interp(TOP, y); belt = interp(BELT, y); fl = interp(FLOOR, y)
    wb = interp(WBELT, y); wt = min(interp(WTOP, y), wb - 0.05)
    half = [
        (0.0, fl), (wb * 0.80 - TUCK, fl), (wb - TUCK, fl + (0.10 if _kind == "sedan" else 0.14)),   # 0,1,2 바닥·문턱 모서리 (턱언더만큼 안쪽)
        (wb - 0.005 - TUCK * 0.15, belt - 0.20), (wb + 0.02, belt - 0.14), (wb - 0.005, belt - 0.02),   # 3,4,5 사이드 블레이드(아래·날·위)
        (wb, belt), (wb - SHOULDER, belt + 0.05),                            # 6 벨트, 7 어깨 선반 (넓으면 유리가 차체 위에 얹힌 모자처럼 보인다)
        (wt + 0.01, belt + 0.05 + (top - belt - 0.05) * 0.5),                # 8 유리 중간
        (wt, top - 0.03), (wt * ROOF_FLAT, top), (0.0, top),                 # 9 지붕 모서리, 10 지붕 평면, 11 가운데
    ]
    ex, ez = ST.get("end", (0.22, 0.30)); RAMP = 0.12
    t = max(0.0, 1.0 - (Y_FRONT - abs(y)) / RAMP); end = t * t   # 옆면과 접선이 이어지고 끝에서 둥글게 감긴다
    if end > 0:
        cz = (belt + fl) / 2 + 0.08
        half = [(x * (1 - ex * end), cz + (z - cz) * (1 - ez * end)) for x, z in half]
    # 앞뒤 끝 입체감: 위에서 보면 가운데가 나오고 모서리가 뒤로 감기며(평면 곡률), 옆에서 보면 범퍼가 앞·보닛 끝이 뒤(기울기), 범퍼 아래는 안으로 말린다
    sgn = 1 if y > 0 else -1; d = Y_FRONT - abs(y)
    if d < END_ZONE:
        s_ = (1 - d / END_ZONE) ** 2
        bow, lean, chin = (NOSE if sgn > 0 else TAIL + (0.0,))
        z0 = fl + 0.42 * (top - fl)
        def back(x, z):
            wz = min(1.0, max(0.0, (z - z0) / max(top - z0, 0.05))); wz = wz * wz * (3 - 2 * wz)
            wc = max(0.0, 1.0 - (z - fl) / 0.15)
            return s_ * (bow * (x / max(wb, 0.3)) ** 2 + lean * wz + chin * wc)
        return [(x, y - sgn * back(x, z), z) for x, z in half] + [(-x, y - sgn * back(x, z), z) for x, z in reversed(half[1:-1])]
    return [(x, y, z) for x, z in half] + [(-x, y, z) for x, z in reversed(half[1:-1])]

def _side_inset(y, z):
    # 턱언더 때문에 높이 z에서 옆면이 벨트 폭(wb)보다 얼마나 안쪽인지 (스티커·줄무늬를 몸에 붙일 때 쓴다)
    pts = section_blade(y)[:6]; wb = interp(WBELT, y)
    for (x0, _, z0), (x1, _, z1) in zip(pts, pts[1:]):
        if z0 <= z <= z1 and z1 > z0: return max(0.0, wb - (x0 + (x1 - x0) * (z - z0) / (z1 - z0)))
    return 0.0

def section(y):
    if ST.get("gen") == "blade": return section_blade(y)
    top = catmull(TOP, y); belt = catmull(BELT, y); fl = catmull(FLOOR, y)
    wb = catmull(WBELT, y); wt = min(catmull(WTOP, y), wb - 0.05, wb * ST.get("tumble", 1.0))
    mid = (fl + belt) / 2 + 0.04
    p3 = (wb + 0.03, belt - 0.08) if ST.get("charline") else (wb + 0.015, mid)   # 캐릭터 라인: 시안 1은 벨트 바로 아래 한 줄을 세게
    half = [
        (0.0, fl), (wb * 0.72, fl), (wb - 0.03, fl + 0.10),             # 0,1,2 바닥, 문턱
        p3, (wb, belt), (wb - 0.035, belt + 0.07),                       # 3 옆 캐릭터 라인(날카롭게), 4 벨트, 5 어깨
        (wt + (wb - wt) * 0.45, belt + 0.07 + (top - belt) * 0.45),      # 6 유리 중간
        (wt, top - 0.045), (wt * 0.5, top - 0.004), (0.0, top),          # 7,8,9
    ]
    # 맨 앞·뒤 단면은 코/꼬리 마감: 안쪽으로 오므려 수직에 가까운 평평한 면을 만든다
    end = max(0.0, 1.0 - (Y_FRONT - abs(y)) / 0.05) if abs(y) > Y_FRONT - 0.05 else 0.0
    if end > 0:
        cz = (belt + fl) / 2 + 0.08
        half = [(x * (1 - 0.22 * end), cz + (z - cz) * (1 - 0.30 * end)) for x, z in half]
    pts = [(x, y, z) for x, z in half] + [(-x, y, z) for x, z in reversed(half[1:-1])]
    return pts

bm = bmesh.new()
NS = 96 if ST.get("detail") else 44   # 디테일 모드: 단면을 촘촘히 해 긴 면의 반사가 울퉁불퉁하지 않게
rings = []; ring_y = []
_ys = {round(Y_FRONT - (Y_FRONT - Y_REAR) * i / NS, 5) for i in range(NS + 1)}
if ST.get("detail"):
    for d in (0.004, 0.012, 0.025, 0.045, 0.07, 0.095):   # 끝에서 이 거리마다 단면 추가
        _ys.add(round(Y_FRONT - d, 5)); _ys.add(round(Y_REAR + d, 5))
_ys = sorted(_ys, reverse=True); NS = len(_ys) - 1
for y in _ys:
    rings.append([bm.verts.new(p) for p in section(y)]); ring_y.append(y)
n = len(rings[0]); NH = n // 2 + 1   # 반쪽 점 수(10)
def level(idx): return idx if idx < NH else n - idx
# 재질은 케이지 단계에서 띠(edge loop) 단위로 정한다: 0 페인트, 1 트림(바닥·문턱), 2 유리(어깨 위), 3 앞유리(표정)
face_mats = []
for a, b, ya, yb in zip(rings, rings[1:], ring_y, ring_y[1:]):
    ym = (ya + yb) / 2
    for i in range(n):
        bm.faces.new((a[i], a[(i + 1) % n], b[(i + 1) % n], b[i]))
        lo, hi = sorted((level(i), level((i + 1) % n)))
        if hi <= ST.get("trim_lv", 2): face_mats.append(1)
        elif ST.get("detail") and lo == 9 and hi == 10: face_mats.append(0)   # A필러·지붕 옆 레일(차체색): 앞유리·옆유리 테두리가 보이게
        elif lo >= ST.get("ws_lv", 6) and WSHIELD_END < ym < COWL: face_mats.append(3)   # 앞유리(표정): 가운데 윗부분만
        elif lo >= ST.get("glass_lv", 5) and ym < COWL and ym > HL * P.get("gend", -1.0): face_mats.append(2)   # 옆유리띠·지붕(파노라마). 뒷유리 끝(gend) 뒤 트렁크는 차체 색
        else: face_mats.append(0)
# 앞뒤 끝 마개: 가운데 세로줄 점을 둬서 끝면이 위에서 볼 때 휜 모양(가운데가 나온)을 따라가게 한다
for r in (rings[0], rings[-1]):
    yc = (r[0].co.y + r[NH - 1].co.y) / 2
    cc = [r[0]] + [bm.verts.new((0.0, yc, r[i].co.z)) for i in range(1, NH - 1)] + [r[NH - 1]]
    for i in range(NH - 1):
        for quad in ((r[i], r[i + 1], cc[i + 1], cc[i]), (cc[i], cc[i + 1], r[(n - i - 1) % n], r[(n - i) % n])):
            vs_ = [v for k, v in enumerate(quad) if v is not quad[k - 1]]
            if len(vs_) >= 3: bm.faces.new(vs_); face_mats.append(0)
bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
# 끝면이 휘면 바깥 판정이 뒤집힐 때가 있다. 부피 부호로 확인해 안쪽을 보면 모두 뒤집는다
_vol = sum(f_.calc_center_median().dot(f_.normal) * f_.calc_area() for f_ in bm.faces)
if _vol < 0: bmesh.ops.reverse_faces(bm, faces=bm.faces)
# 접힘선(크리스): 벨트·캐릭터 라인·어깨를 날카롭게 (파라메트릭한 면 느낌)
CREASE = ST.get("crease", {3: 0.85, 4: 0.9, 5: 0.5, 2: 0.6})
cl = bm.edges.layers.float.new("crease_edge")
for e in bm.edges:
    ia = e.verts[0].index if False else None
bm.verts.ensure_lookup_table(); bm.edges.ensure_lookup_table()
vidx = {}
for ri, r in enumerate(rings):
    for pi, v in enumerate(r): vidx[v.index] = (ri, pi)
for e in bm.edges:
    if e.verts[0].index not in vidx or e.verts[1].index not in vidx: continue   # 끝 마개 가운데 점
    (ra, pa), (rb, pb) = vidx[e.verts[0].index], vidx[e.verts[1].index]
    if ra != rb and pa == pb and level(pa) in CREASE: e[cl] = CREASE[level(pa)]
    elif ra == rb and ra in (0, NS) and ST.get("end_crease"): e[cl] = ST["end_crease"]   # 앞뒤 끝면 둘레를 날카롭게
mesh = bpy.data.meshes.new("body"); bm.to_mesh(mesh); bm.free(); mesh.update()
print("STEP tomesh", len(mesh.polygons), len(face_mats), flush=True)
body = bpy.data.objects.new("body", mesh); scene.collection.objects.link(body)
body.data.materials.append(M_PAINT); body.data.materials.append(M_TRIM); body.data.materials.append(M_GLASS); body.data.materials.append(M_FACE)
print("STEP mats", flush=True)
mesh.polygons.foreach_set("material_index", face_mats); mesh.update()
print("STEP set", flush=True)
def matcount(tag): print("MATCOUNT", tag, [sum(1 for p in body.data.polygons if p.material_index == i) for i in range(4)], flush=True)
matcount("cage")
bpy.context.view_layer.objects.active = body; body.select_set(True)
_subd = ST.get("subd", 2)
if _subd > 0:
    mod = body.modifiers.new("subd", "SUBSURF"); mod.levels = mod.render_levels = _subd
    bpy.ops.object.modifier_apply(modifier="subd")
matcount("subd")

# 휠 아치 파기
for y in WHEELS_Y:
    # 팔각 휠 아치 (각진 미래형)
    bpy.ops.mesh.primitive_cylinder_add(radius=WHEEL_R + ST.get("arch", 0.09) - (0.015 if ST.get("detail") else 0), depth=3.0, location=(0, y, WHEEL_R + (0.015 if ST.get("detail") else 0)), rotation=(ST.get("arch_rot", 0), math.pi / 2, 0), vertices=ST.get("arch_v", 40))
    cut = bpy.context.object; cut.data.materials.append(M_WELL)
    bpy.ops.object.transform_apply(rotation=True)
    bo = body.modifiers.new("arch", "BOOLEAN"); bo.object = cut; bo.operation = "DIFFERENCE"; bo.solver = "EXACT"; bo.material_mode = "TRANSFER"
    bpy.context.view_layer.objects.active = body
    bpy.ops.object.modifier_apply(modifier="arch")
    bpy.data.objects.remove(cut)

matcount("arch")
# ---------- 디테일(홈): 앞문·뒷문 분할선, 후드 분할선. 얇은 판으로 겉면을 3 cm 깊이만 파낸다 ----------
DETAIL = ST.get("detail", False)
_archr = WHEEL_R + ST.get("arch", 0.09)
DOOR_A = WHEEL_YF - _archr - 0.07            # 앞문 앞 끝 (앞 휠 아치 바로 뒤)
DOOR_B = -WHEEL_YR + _archr + 0.07           # 뒷문 뒤 끝 (뒤 휠 아치 바로 앞)
DOOR_M = (DOOR_A + DOOR_B) / 2 - 0.07       # 앞문·뒷문 경계 (B필러). 앞문:뒷문 ≈ 1.15:1
if DETAIL:
    cutters = []
    def slab(loc, scale, rot=(0, 0, 0)):
        bpy.ops.mesh.primitive_cube_add(location=loc); o = bpy.context.object; o.scale = scale; o.rotation_euler = rot; o.data.materials.append(M_WELL); cutters.append(o)
    # 문 틈: 실제(3~4 mm)보다 넓게 1.2 cm로 파야 화면 크기에서 앞문·뒷문이 갈려 보인다
    GAP = 0.004
    zdoor = {}
    for yy in (DOOR_A, DOOR_M, DOOR_B):
        wb = interp(WBELT, yy); z0 = interp(FLOOR, yy) + 0.15; z1 = interp(BELT, yy) - 0.004
        zdoor[yy] = z0
        for s in (-1, 1): slab((s * (wb - TUCK / 2), yy, (z0 + z1) / 2), (0.045 + TUCK / 2, GAP, (z1 - z0) / 2))
    # 앞문 윗선: 벨트 바로 아래에서 앞으로 꺾여 A필러 밑동(카울)까지 (실차처럼 앞문이 펜더 위로 뻗는다)
    if COWL > DOOR_A:
        zt = interp(BELT, (DOOR_A + COWL) / 2) - 0.008
        for s in (-1, 1): slab((s * interp(WBELT, (DOOR_A + COWL) / 2), (DOOR_A + COWL + 0.015) / 2, zt), (0.045, (COWL + 0.015 - DOOR_A) / 2, GAP))
        for s in (-1, 1): slab((s * interp(WBELT, COWL), COWL + 0.015, interp(BELT, COWL) + 0.03), (0.045, GAP, 0.04))
    # 문 아래 가로 틈: 앞문 앞 끝에서 뒷문 뒤 끝까지 (문과 문턱이 갈린다)
    ym = (DOOR_A + DOOR_B) / 2; zb = max(zdoor.values())
    for s in (-1, 1): slab((s * (interp(WBELT, ym) - TUCK / 2), ym, zb), (0.045 + TUCK / 2, (DOOR_A - DOOR_B) / 2 + GAP, GAP))
    # 범퍼 분할선: 앞·뒤 끝면과 그 옆(휠 아치까지) 겉면만 가로로 판다
    for ye, sgn in ((Y_FRONT, 1), (Y_REAR, -1)):
        zb = min(interp(FLOOR, ye) + 0.34, interp(TOP, ye) - 0.07)   # 번호판 위 끝(+0.27)과 겹치지 않게, 앞·뒤 끝 윗모서리에 걸리지 않게
        _bp = []
        for k in range(25):
            xk = interp(WBELT, ye) * 0.98 * (k / 12 - 1)
            ok, loc, nrm, idx = body.ray_cast(Vector((xk, ye + sgn * 1.0, zb)), Vector((0, -sgn, 0)))
            if ok: _bp.append(loc.copy())
        for p0, p1 in zip(_bp, _bp[1:]):
            mid = (p0 + p1) / 2; dv = p1 - p0
            slab((mid.x, mid.y, zb), (dv.length / 2 + 0.004, 0.03, 0.004), (0, 0, math.atan2(dv.y, dv.x)))
        ya = sgn * ((WHEEL_YF if sgn > 0 else WHEEL_YR) + _archr + 0.02); yl = abs(ye - ya)
        for s in (-1, 1): slab((s * (interp(WBELT, (ye + ya) / 2) - TUCK / 2), (ye + ya) / 2, zb), (0.05 + TUCK / 2, yl / 2, 0.004))
    # 후드 분할선: 후드 기울기를 따라 앞에서 카울까지
    # 곧은 판 하나로 파면 휜 후드에서 점선처럼 끊긴다. 짧은 조각으로 나눠 위에서 잰 표면 높이에 하나씩 놓는다
    yf = Y_FRONT - 0.14; NSEG = 14
    for s in (-1, 1):
        xs = s * interp(WBELT, (yf + COWL) / 2) * 0.62
        pts = []
        for k in range(NSEG + 1):
            yk = yf + (COWL - yf) * k / NSEG
            ok, loc, nrm, idx = body.ray_cast(Vector((xs, yk, 5.0)), Vector((0, 0, -1)))
            if ok: pts.append(loc.copy())
        for p0, p1 in zip(pts, pts[1:]):
            mid = (p0 + p1) / 2; d = p1 - p0
            slab((mid.x, mid.y, mid.z), (0.0045, d.length / 2 + 0.004, 0.03), (math.atan2(d.z, d.y), 0, 0))
    # 판끼리 겹치면 한꺼번에 깎을 때 차체가 깨진다. 하나씩 따로 깎는다
    for cut in cutters:
        bpy.ops.object.select_all(action="DESELECT"); cut.select_set(True); bpy.context.view_layer.objects.active = cut
        bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
        bo = body.modifiers.new("seam", "BOOLEAN"); bo.object = cut; bo.operation = "DIFFERENCE"; bo.solver = "EXACT"; bo.material_mode = "TRANSFER"
        bpy.ops.object.select_all(action="DESELECT"); body.select_set(True); bpy.context.view_layer.objects.active = body
        # 불리언이 깨져 차체가 거의 사라지면(면이 절반 아래로) 그 홈은 건너뛰고 차체를 되돌린다
        _keep = body.data.copy(); _n = len(body.data.polygons); _loc = sum((v.co for v in cut.data.vertices), Vector()) / max(len(cut.data.vertices), 1)
        bpy.ops.object.modifier_apply(modifier="seam"); bpy.data.objects.remove(cut)
        if len(body.data.polygons) < _n * 0.5:
            print("SEAM SKIP", _n, "->", len(body.data.polygons), "at", tuple(round(v, 3) for v in _loc), flush=True)
            _bad = body.data; body.data = _keep; bpy.data.meshes.remove(_bad)
        else:
            bpy.data.meshes.remove(_keep)
    print("BODYFACES", len(body.data.polygons), flush=True)
WS_Z = (catmull(TOP, COWL) + catmull(TOP, WSHIELD_END)) / 2 + 0.02
duv = body.data.uv_layers.new(name="decal")
for poly in body.data.polygons:
    for li in poly.loop_indices:
        v = body.data.vertices[body.data.loops[li].vertex_index].co
        duv.data[li].uv = ((Y_FRONT - v.y) / _L, v.z / _H)   # 앞→뒤가 왼→오른, 바닥→지붕이 아래→위
uv_layer = body.data.uv_layers.new(name="face")
for poly in body.data.polygons:
    for li in poly.loop_indices:
        v = body.data.vertices[body.data.loops[li].vertex_index].co
        # 앞유리를 정면에서 본 평면 투영 (x → u, 유리 아래→위 → v)
        _sp = ST.get("span", 2.4)
        uv_layer.data[li].uv = (0.5 - v.x / _sp, (v.z - WS_Z) / _sp + 0.56)
_bw, _bs = ST.get("bevel", (0.01, 3))
bev = body.modifiers.new("bevel", "BEVEL"); bev.width = _bw; bev.segments = _bs; bev.limit_method = "ANGLE"; bev.angle_limit = math.radians(30)
bpy.ops.object.modifier_apply(modifier="bevel")
# 정리: 촘촘한 단면 + 베벨이 만든 넓이 거의 0인 찌그러진 면을 없앤다. 이런 면은 방향이 제멋대로라
# 확대하면 모서리를 따라 반사가 톱니·세로 줄처럼 튄다
_bm = bmesh.new(); _bm.from_mesh(body.data)
_n0 = len(_bm.faces)
bmesh.ops.remove_doubles(_bm, verts=_bm.verts, dist=0.0004)
bmesh.ops.dissolve_degenerate(_bm, edges=_bm.edges, dist=0.0004)
_thin = [f for f in _bm.faces if f.calc_area() < 2e-7]
if _thin: bmesh.ops.delete(_bm, geom=_thin, context="FACES_ONLY")
bmesh.ops.recalc_face_normals(_bm, faces=_bm.faces)
_bm.to_mesh(body.data); _bm.free(); body.data.update()
print("CLEANUP faces", _n0, "->", len(body.data.polygons), "thin", len(_thin), flush=True)
for poly in body.data.polygons: poly.use_smooth = True
bpy.ops.object.shade_auto_smooth(angle=math.radians(30))
# (가중 법선은 시험해 보니 후드 반사가 구겨져 보여서 쓰지 않는다)
print("MATCOUNT", [sum(1 for p in body.data.polygons if p.material_index == i) for i in range(4)])
uv_layer = body.data.uv_layers["face"]  # 모디파이어 적용 뒤에는 다시 가져와야 한다
_uvs = [uv_layer.data[li].uv for p in body.data.polygons if p.material_index == 3 for li in p.loop_indices]
if _uvs: print("FACEUV u %.2f..%.2f v %.2f..%.2f" % (min(u.x for u in _uvs), max(u.x for u in _uvs), min(u.y for u in _uvs), max(u.y for u in _uvs)))
if os.path.exists(FACE_PNG): print("FACEIMG", bpy.data.images[os.path.basename(FACE_PNG)].size[:], bpy.data.images[os.path.basename(FACE_PNG)].has_data)

# 휠 하우스(바퀴마다 따로, 폭 0.42)와 바닥판. 좌우를 가로지르는 통으로 만들면 아래에서 바퀴가 이어진 것처럼 보인다
for y in WHEELS_Y:
    for x in (WHEEL_X, -WHEEL_X):
        # 바퀴 안쪽(차체 쪽)으로 밀어 넣어 타이어를 가리지 않게 한다
        sx = 1 if x > 0 else -1
        _rw = (_archr - 0.012) if DETAIL else WHEEL_R + 0.06
        bpy.ops.mesh.primitive_cylinder_add(radius=_rw, depth=0.30, location=(x - sx * (0.17 if DETAIL else 0.15), y, WHEEL_R), rotation=(0, math.pi / 2, 0), vertices=48)
        w = bpy.context.object; w.data.materials.append(M_WELL)
# 바닥판: 낮은 각도에서 모서리가 보이지 않게 몸 안쪽으로 작게
bpy.ops.mesh.primitive_cube_add(location=(0, ((WHEEL_YF - WHEEL_YR) / 2) if DETAIL else 0, catmull(FLOOR, 0) - 0.02)); fl = bpy.context.object; fl.scale = ((WHEEL_X - TIRE_W / 2 - 0.04) if DETAIL else _W / 2 - 0.24, ((WHEEL_YF + WHEEL_YR) / 2 - WHEEL_R - 0.05) if DETAIL else HL - 0.4, 0.02); fl.data.materials.append(M_WELL)

# ---------- 바퀴 ----------
WHEEL_OBJS = {}
def wheel_detail(x, y, r):
    # 실차처럼: 속이 빈 타이어(사이드월·어깨·림 보호 테) + 안쪽으로 오목한 휠(스포크가 림에서 허브로 기울며 굵어짐)
    # + 허브·휠너트 5개·센터캡 + 스포크 사이로 보이는 디스크·캘리퍼·림 통
    sx = 1 if x > 0 else -1; w = TIRE_W / 2; rr = r * ST.get("rim", 0.78)
    def at(a, rho, phi): return (x + sx * a, y + rho * math.sin(phi), r + rho * math.cos(phi))
    seat = rr * 0.86; mid = (rr + r) / 2
    prof = [(w * 0.80, seat - 0.005), (w * 0.90, seat + 0.010), (w * 0.97, rr + 0.010), (w * 1.00, rr + 0.030), (w * 1.00, mid),
            (w * 0.97, r - 0.035), (w * 0.88, r - 0.012), (w * 0.72, r - 0.002), (w * 0.30, r)]
    prof = prof + [(-a, p) for a, p in reversed(prof)]
    N = 96; bm_ = bmesh.new(); vs = []
    for k in range(N):
        phi = 2 * math.pi * k / N; vs.append([bm_.verts.new(at(a, p, phi)) for a, p in prof])
    M_ = len(prof)
    for k in range(N):
        for j in range(M_):
            bm_.faces.new((vs[k][j], vs[(k + 1) % N][j], vs[(k + 1) % N][(j + 1) % M_], vs[k][(j + 1) % M_]))
    bmesh.ops.recalc_face_normals(bm_, faces=bm_.faces)
    me = bpy.data.meshes.new("tire"); bm_.to_mesh(me); bm_.free()
    t = bpy.data.objects.new("tire", me); scene.collection.objects.link(t); t.data.materials.append(M_TIRE)
    for p in me.polygons: p.use_smooth = True
    def cyl(rad, depth, a, m, verts=64, fill="NGON", bevel=0.0):
        bpy.ops.mesh.primitive_cylinder_add(radius=rad, depth=depth, location=at(a, 0, 0), rotation=(0, math.pi / 2, 0), vertices=verts, end_fill_type=fill)
        o = bpy.context.object; o.data.materials.append(m)
        if bevel: b_ = o.modifiers.new("b", "BEVEL"); b_.width = bevel; b_.segments = 2; b_.harden_normals = True
        bpy.ops.object.shade_smooth(); return o
    def tor(R_, r_, a, m):
        bpy.ops.mesh.primitive_torus_add(major_radius=R_, minor_radius=r_, location=at(a, 0, 0), rotation=(0, math.pi / 2, 0), major_segments=96, minor_segments=12)
        o = bpy.context.object; o.data.materials.append(m); bpy.ops.object.shade_smooth(); return o
    cyl(rr - 0.012, w * 1.6, 0, M_RIM_DARK, fill="NOTHING")              # 림 통 (스포크 사이 안쪽 벽)
    cyl(rr - 0.012, 0.004, -w * 0.6, M_RIM_DARK)                           # 통 뒤 막음
    tor(rr - 0.002, 0.011, w * 0.90, M_RIM)                                # 림 바깥 테
    tor(rr - 0.022, 0.005, w * 0.76, M_RIM)                                # 림 안쪽 턱
    cyl(rr * 0.80, 0.026, w * 0.05, M_DISC)                                # 브레이크 디스크
    cyl(rr * 0.36, 0.03, w * 0.12, M_RIM_DARK)                             # 디스크 모자
    # 캘리퍼: 앞바퀴는 뒤쪽 위, 뒷바퀴는 앞쪽 위. 앞이 더 크다
    front = y > 0; phi_c = math.radians(35 if front else 145) - math.pi / 2
    bpy.ops.mesh.primitive_cube_add(location=at(w * 0.22, rr * 0.70, phi_c))
    cp = bpy.context.object; cp.rotation_euler = (-phi_c, 0, 0); cp.scale = (0.034, rr * (0.34 if front else 0.26), 0.034); cp.data.materials.append(M_CALIPER)
    b3 = cp.modifiers.new("b", "BEVEL"); b3.width = 0.012; b3.segments = 3
    ah = w * 0.40                                                          # 허브 면 (림 테보다 안쪽 = 오목)
    cyl(rr * 0.30, 0.03, ah, M_RIM, bevel=0.006)
    cyl(0.042, 0.012, ah + 0.018, M_RIM, bevel=0.004)                      # 센터캡 (로고 없음, 휠색. 검정이면 구멍처럼 보인다)
    for i in range(5):                                                     # 휠너트 5개 (PCD 114.3)
        ph = i * 2 * math.pi / 5 + math.pi / 5
        bpy.ops.mesh.primitive_cylinder_add(radius=0.0095, depth=0.02, location=at(ah + 0.02, 0.057, ph), rotation=(0, math.pi / 2, 0), vertices=6)
        bpy.context.object.data.materials.append(M_CHROME)
    # 트윈 스포크 5쌍: 바깥 끝(림 테 안쪽, 앞)에서 허브(안쪽, 뒤)로 기울어 들어가고 허브 쪽이 굵다
    for i in range(5):
        for side in (-1, 1):
            po = i * 2 * math.pi / 5 + side * 0.125; pi_ = i * 2 * math.pi / 5 + side * 0.085
            ends = [(w * 0.86, rr - 0.012, po, 0.015, 0.016), (ah + 0.006, rr * 0.30, pi_, 0.023, 0.018)]
            vv = []
            for a, rho, ph, hw, ht in ends:
                c = Vector(at(a, rho, ph)); tvec = Vector((0, math.cos(ph), -math.sin(ph))); xv = Vector((sx, 0, 0))
                vv += [c + tvec * hw + xv * ht, c - tvec * hw + xv * ht, c - tvec * hw - xv * ht, c + tvec * hw - xv * ht]
            bm_ = bmesh.new(); bv_ = [bm_.verts.new(tuple(v)) for v in vv]
            for f_ in [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)]: bm_.faces.new([bv_[k] for k in f_])
            bmesh.ops.recalc_face_normals(bm_, faces=bm_.faces)
            me = bpy.data.meshes.new("spoke"); bm_.to_mesh(me); bm_.free()
            o = bpy.data.objects.new("spoke", me); scene.collection.objects.link(o); o.data.materials.append(M_RIM)
            b_ = o.modifiers.new("b", "BEVEL"); b_.width = 0.004; b_.segments = 2; b_.harden_normals = True
            for p in me.polygons: p.use_smooth = True
    bpy.ops.mesh.primitive_cylinder_add(radius=0.0035, depth=0.014, location=at(w * 0.80, rr - 0.03, 0.62), rotation=(0, math.pi / 2, 0), vertices=12)
    bpy.context.object.data.materials.append(M_CHROME)                     # 공기 주입구(밸브 캡)

def wheel(x, y, wi):
    r = WHEEL_R
    _before = set(o.name for o in scene.objects)
    if ST.get("detail"):
        wheel_detail(x, y, r)
        for o in scene.objects:
            if o.name not in _before: WHEEL_OBJS[o.name] = wi
        return
    bpy.ops.mesh.primitive_cylinder_add(radius=r, depth=0.25, location=(x, y, r), rotation=(0, math.pi / 2, 0), vertices=96)
    t = bpy.context.object; t.data.materials.append(M_TIRE)
    b = t.modifiers.new("b", "BEVEL"); b.width, b.segments = ST.get("tire", (0.05, 6))
    bpy.ops.object.modifier_apply(modifier="b"); bpy.ops.object.shade_smooth()
    sx = 1 if x > 0 else -1
    rr = r * ST.get("rim", 0.72)
    if ST.get("wheel") == "cover":
        # 페블: 차체색 무광 평면 휠커버 + 작은 캡
        bpy.ops.mesh.primitive_cylinder_add(radius=r * 0.80, depth=0.04, location=(x + sx * 0.11, y, r), rotation=(0, math.pi / 2, 0), vertices=64)
        cv = bpy.context.object; cv.data.materials.append(M_COVER)
        b2 = cv.modifiers.new("b", "BEVEL"); b2.width = 0.015; b2.segments = 4; bpy.ops.object.modifier_apply(modifier="b"); bpy.ops.object.shade_smooth()
        bpy.ops.mesh.primitive_cylinder_add(radius=0.05, depth=0.015, location=(x + sx * 0.135, y, r), rotation=(0, math.pi / 2, 0), vertices=32)
        cap = bpy.context.object; cap.data.materials.append(M_RIM)
        return
    if ST.get("detail"):
        # 휠 안쪽: 어두운 림 통 → 브레이크 디스크 → 캘리퍼 (스포크 사이로 보인다)
        bpy.ops.mesh.primitive_cylinder_add(radius=rr, depth=0.03, location=(x + sx * 0.035, y, r), rotation=(0, math.pi / 2, 0), vertices=64)
        d = bpy.context.object; d.data.materials.append(M_RIM_DARK)
        bpy.ops.mesh.primitive_cylinder_add(radius=rr * 0.80, depth=0.022, location=(x + sx * 0.075, y, r), rotation=(0, math.pi / 2, 0), vertices=64)
        dk = bpy.context.object; dk.data.materials.append(M_DISC); bpy.ops.object.shade_smooth()
        bpy.ops.mesh.primitive_cylinder_add(radius=rr * 0.30, depth=0.03, location=(x + sx * 0.085, y, r), rotation=(0, math.pi / 2, 0), vertices=32)
        hb = bpy.context.object; hb.data.materials.append(M_RIM_DARK)
        ca = math.radians(35 if y > 0 else 145)   # 캘리퍼: 앞바퀴는 뒤쪽 위, 뒷바퀴는 앞쪽 위
        bpy.ops.mesh.primitive_cube_add(location=(x + sx * 0.088, y - math.cos(ca) * rr * 0.68, r + math.sin(ca) * rr * 0.68), rotation=(-(math.pi / 2 - ca), 0, 0))
        cp = bpy.context.object; cp.scale = (0.024, rr * 0.30, 0.040); cp.data.materials.append(M_CALIPER)
        b3 = cp.modifiers.new("b", "BEVEL"); b3.width = 0.01; b3.segments = 3
    else:
        # 에어로 휠: 평평한 어두운 원판 + 밝은 터빈 날개 5장 + 가는 림
        bpy.ops.mesh.primitive_cylinder_add(radius=rr, depth=0.05, location=(x + sx * 0.10, y, r), rotation=(0, math.pi / 2, 0), vertices=64)
        d = bpy.context.object; d.data.materials.append(M_RIM_DARK)
    bpy.ops.mesh.primitive_torus_add(major_radius=rr, minor_radius=0.016, location=(x + sx * 0.125, y, r), rotation=(0, math.pi / 2, 0), major_segments=64, minor_segments=12)
    lip = bpy.context.object; lip.data.materials.append(M_RIM); bpy.ops.object.shade_smooth()
    if ST.get("detail"):
        # 5쌍 트윈 스포크: 허브에서 림까지 곧게 뻗는 가는 살
        for i in range(5):
            for off in (-0.16, 0.16):
                a = i * 2 * math.pi / 5 + off
                bpy.ops.mesh.primitive_cube_add(location=(x + sx * 0.126, y + math.sin(a) * rr * 0.53, r + math.cos(a) * rr * 0.53), rotation=(-a, 0, 0))
                s = bpy.context.object; s.scale = (0.007, 0.016, rr * 0.45); s.data.materials.append(M_RIM)
    else:
      for i in range(5):
        a = i * 2 * math.pi / 5
        bpy.ops.mesh.primitive_cube_add(location=(x + sx * 0.126, y + math.sin(a + 0.35) * rr * 0.52, r + math.cos(a + 0.35) * rr * 0.52), rotation=(a + 0.9, 0, 0))
        s = bpy.context.object; s.scale = (0.006, 0.075, rr * 0.42); s.data.materials.append(M_RIM)
    bpy.ops.mesh.primitive_cylinder_add(radius=0.06, depth=0.02, location=(x + sx * 0.132, y, r), rotation=(0, math.pi / 2, 0), vertices=32)
    cap = bpy.context.object; cap.data.materials.append(M_RIM)
    for o in scene.objects:
        if o.name not in _before: WHEEL_OBJS[o.name] = wi
_wi = 0
for x in (WHEEL_X, -WHEEL_X):
    for y in WHEELS_Y: wheel(x, y, _wi); _wi += 1

# ---------- 램프: 앞 흰 라이트바, 뒤 빨간 라이트바, 아래 검정 흡기구 ----------
# 앞: 전폭 라이트바 + 양 끝 세로 슬릿, 뒤: 전폭 라이트바, 아래 검정 흡기구, 지붕 끝 스포일러 립
LAMP = ST.get("lamp", "slit")
_zf = catmull(TOP, Y_FRONT)
if LAMP == "slit":
    bpy.ops.mesh.primitive_cube_add(location=(0, Y_FRONT - 0.03, _zf - 0.10)); lb = bpy.context.object; lb.scale = (_W * 0.33, 0.04, 0.012); lb.data.materials.append(M_LAMP)
    for s in (-1, 1):
        bpy.ops.mesh.primitive_cube_add(location=(s * _W * 0.30, Y_FRONT - 0.07, _zf - 0.17)); sl = bpy.context.object; sl.scale = (0.012, 0.06, 0.06); sl.data.materials.append(M_LAMP)
elif LAMP == "bar":
    bpy.ops.mesh.primitive_cube_add(location=(0, Y_FRONT - 0.03, _zf - 0.11)); lb = bpy.context.object; lb.scale = (_W * 0.40, 0.045, 0.022); lb.data.materials.append(M_LAMP)
elif LAMP == "panel":
    # 검은 유광 패널 안에 가는 라이트바 한 줄
    bpy.ops.mesh.primitive_cube_add(location=(0, Y_FRONT - 0.035, _zf - 0.12)); pn = bpy.context.object; pn.scale = (_W * 0.44, 0.045, 0.05); pn.data.materials.append(M_GLASS)
    bpy.ops.mesh.primitive_cube_add(location=(0, Y_FRONT - 0.02, _zf - 0.12)); lb = bpy.context.object; lb.scale = (_W * 0.40, 0.04, 0.011); lb.data.materials.append(M_LAMP)
elif LAMP == "cowl":
    # 후드와 앞유리 경계(카울)를 따라 가는 선 하나. 표정 아래를 받친다
    bpy.ops.mesh.primitive_cube_add(location=(0, COWL + 0.03, catmull(TOP, COWL + 0.03) + 0.006)); lb = bpy.context.object; lb.scale = (_W * 0.34, 0.012, 0.006); lb.data.materials.append(M_LAMP)
# 뒤 라이트바: 세단처럼 트렁크가 낮은 차는 지붕선 아래로 (몸 밖으로 튀어나오지 않게)
bpy.ops.mesh.primitive_cube_add(location=(0, Y_REAR + 0.03, min(catmull(BELT, Y_REAR) + 0.10, catmull(TOP, Y_REAR) - 0.12))); tb = bpy.context.object; tb.scale = (_W * 0.34, 0.04, 0.012); tb.data.materials.append(M_TAIL)
if ST.get("detail"):
    # 막대 하나 대신 램프 유닛: 유광 검정 하우징 → 반사판 → LED 줄 → 프로젝터 렌즈 → 투명 커버 (앞에서 뒤로 겹친다)
    for o in [lb, tb] + ([pn] if LAMP == "panel" else []): bpy.data.objects.remove(o)
    def cube(loc, scale, m, rot=(0, 0, 0)):
        bpy.ops.mesh.primitive_cube_add(location=loc); o = bpy.context.object; o.scale = scale; o.rotation_euler = rot; o.data.materials.append(m); return o
    # 차체 앞·뒤 표면 위치를 광선으로 찾는다 (끝면이 둥글게 깎여 있어서 Y_FRONT보다 안쪽에 있다)
    def surf(y0, z, sgn):
        ok, loc, nrm, idx = body.ray_cast(Vector((0, y0 + sgn * 1.0, z)), Vector((0, -sgn, 0)))
        return loc.y if ok else y0
    def flat_half(y0, z, sgn, tol=0.006):
        # 가운데 표면 깊이에서 tol 이상 물러나기 직전까지의 반폭 (그 바깥은 둥글게 돌아간다)
        y_c = surf(y0, z, sgn); hw = 0.2
        for i in range(1, 80):
            x = _W / 2 * i / 80
            ok, loc, nrm, idx = body.ray_cast(Vector((x, y0 + sgn * 1.0, z)), Vector((0, -sgn, 0)))
            if not ok or abs(loc.y - y_c) > tol: break
            hw = x
        return hw
    def hit(x, y0, z, sgn):
        ok, loc, nrm, idx = body.ray_cast(Vector((x, y0 + sgn * 1.0, z)), Vector((0, -sgn, 0)))
        return (loc, nrm) if ok else (None, None)
    def face_ext(y0, z, sgn, ny=0.30):
        # 앞·뒷면이 옆으로 70°쯤 돌아가기 직전까지의 반폭 (램프가 모서리를 살짝 감싸되 옆으로 삐져나오지 않게)
        hw = 0.1
        for i in range(1, 200):
            x = _W / 2 * i / 200; loc, nrm = hit(x, y0, z, sgn)
            if loc is None or nrm.y * sgn < ny: break
            hw = x
        return hw
    LAMPSET = [None]   # 지금 만드는 램프 스타일 꼬리표 (예: "f:split"). 페이지가 고른 스타일만 보여준다
    def tag(o):
        if LAMPSET[0] and o is not None: o["lampset"] = LAMPSET[0]
        return o
    _LIM = {}
    def _row_lim(y0, z, sgn):
        k = (round(y0, 4), round(z, 4), sgn)
        if k not in _LIM: _LIM[k] = face_ext(y0, z, sgn, 0.08) - 0.004
        return _LIM[k]
    def conform(sgn, y0, xs, zs, off, m, thick=0.003, name="lampsheet"):
        # 표면을 광선으로 따라가는 얇은 판: 각 점 = 표면 + 법선 × off (차체 곡면에 꼭 붙는다). xs는 줄마다 달라도 된다(함수)
        rows = []
        nx_ = None
        for z in zs:
            row = []
            lim = _row_lim(y0, z, sgn) if name in ("f", "r") else None   # 전조등: 이 높이의 정면 폭을 넘지 않게 (위 모서리가 둥근 차체를 따라간다)
            for x in (xs(z) if callable(xs) else xs):
                if lim is not None: x = max(-lim, min(lim, x))
                loc, nrm = hit(x, y0, z, sgn)
                if loc is None: print("CONFORM MISS", name, LAMPSET[0], flush=True); return None
                row.append(tuple(loc + nrm * off))
            rows.append(row)
        nx = len(rows[0]); vs = [p for r in rows for p in r]
        fs_ = [(j * nx + i, j * nx + i + 1, (j + 1) * nx + i + 1, (j + 1) * nx + i) for j in range(len(zs) - 1) for i in range(nx - 1)]
        me = bpy.data.meshes.new(name); me.from_pydata(vs, [], fs_); me.update()
        o = bpy.data.objects.new(name, me); scene.collection.objects.link(o); o.data.materials.append(m)
        for p in me.polygons: p.use_smooth = True
        if thick: so = o.modifiers.new("s", "SOLIDIFY"); so.thickness = thick; so.offset = 0
        return tag(o)
    def span(x0, x1, n=48): return [x0 + (x1 - x0) * k / n for k in range(n + 1)]
    def rows(z0, z1, n=3): return [z0 + (z1 - z0) * k / n for k in range(n + 1)]
    def lamp_z(y0, z, sgn, h, ny=0.55):
        # 램프 위 끝이 후드·트렁크 윗면으로 넘어가지 않게: 위 끝 법선이 앞(뒤)을 충분히 볼 때까지 내린다
        for _ in range(30):
            loc, nrm = hit(0, y0, z + h, sgn)
            if loc is not None and nrm.y * sgn > ny: break
            z -= 0.01
        return z
    def pod(x, y0, z, sgn, r, depth, m, off):
        # 표면 법선을 따라 세운 원통 (프로젝터 컵·렌즈)
        loc, nrm = hit(x, y0, z, sgn)
        if loc is None: return
        bpy.ops.mesh.primitive_cylinder_add(radius=r, depth=depth, location=tuple(loc + nrm * off), vertices=32)
        o = bpy.context.object; o.rotation_euler = nrm.to_track_quat("Z", "Y").to_euler(); o.data.materials.append(m); bpy.ops.object.shade_smooth(); tag(o)
    # 램프 높이·폭: 스타일과 상관없이 같은 자리 (밤 불빛·내보내는 치수가 이 값을 쓴다)
    # 전조등은 후드 앞끝 바로 아래에 크게 (멀리서도 눈에 띄게). LS = 세로 크기 배율
    LS = 1.7
    zf = lamp_z(Y_FRONT, _zf - 0.04, 1, LS * 0.04, 0.80)   # 램프 위 끝까지 정면을 보는 면(휘기 시작하는 곳 아래)
    EF = min(face_ext(Y_FRONT, z, 1, 0.08) for z in (zf - LS * 0.038, zf)) - 0.004   # 모서리를 더 감싸 폭을 넓게
    HF = flat_half(Y_FRONT, zf, 1, 0.006 + 0.5 * NOSE[0]) - 0.01; print("LAMPFIT front", round(zf, 3), round(HF, 3), round(EF, 3), flush=True)
    zr = lamp_z(Y_REAR, min(catmull(BELT, Y_REAR) + 0.10, catmull(TOP, Y_REAR) - 0.12), -1, 0.035)
    ER = min(face_ext(Y_REAR, z, -1, 0.65) for z in (zr - 0.032, zr, zr + 0.032)) - 0.004
    HR = flat_half(Y_REAR, zr, -1, 0.006 + 0.5 * TAIL[0]) - 0.01; print("LAMPFIT rear", round(zr, 3), round(HR, 3), round(ER, 3), flush=True)
    F_, R_ = Y_FRONT, Y_REAR
    def fsheet(xs, z0, z1, off, m, n=1, name="f"): return conform(1, F_, xs, rows(z0, z1, n), off, m, thick=0.002 if m not in (M_LENS,) else 0.0025, name=name)
    def rsheet(xs, z0, z1, off, m, n=1, name="r"): return conform(-1, R_, xs, rows(z0, z1, n), off, m, thick=0.002 if m not in (M_TAILLENS,) else 0.0025, name=name)
    def proj(x, z, r=0.018):
        r *= LS   # 프로젝터: 크롬 반사 컵 + 렌즈
        pod(x, F_, z, 1, r, 0.006, M_REFL, 0.004); pod(x, F_, z, 1, r * 0.64, 0.004, M_LAMP, 0.0065)
    XF = EF * 0.34                                                     # 분리형 안쪽 끝
    # ── 전조등 1. 분리형(기본): 좌우 유닛, 일자 주간주행등 + 프로젝터 2 + 호박색 (ㄱ자는 특정 브랜드 시그니처와 닮아 뺐다) ──
    LAMPSET[0] = "f:split"
    for s_ in (-1, 1):
        def sp_(a0, a1, n=32): return span(s_ * a0, s_ * a1, n)
        fsheet(sp_(XF, EF), zf - LS * 0.034, zf + LS * 0.034, 0.002, M_PIANO, 4)
        fsheet(sp_(XF + 0.012, EF * 0.985), zf + LS * 0.016, zf + LS * 0.024, 0.0045, M_LAMP)
        for t_ in (0.30, 0.58): proj(s_ * (XF + (EF - XF) * t_), zf - LS * 0.006)
        fsheet(sp_(EF * 0.70, EF * 0.88, 16), zf - LS * 0.028, zf - LS * 0.020, 0.0045, M_AMBER)
        fsheet(sp_(XF - 0.003, EF + 0.003), zf - LS * 0.037, zf + LS * 0.037, 0.008, M_LENS, 4)
    # ── 전조등 2. 라이트바: 전폭 검정 띠 + 가는 주간주행등 + 프로젝터 2+2 ──
    LAMPSET[0] = "f:bar"
    fsheet(span(-EF, EF, 96), zf - LS * 0.036, zf + LS * 0.036, 0.002, M_PIANO, 4)
    fsheet(span(-EF * 0.985, EF * 0.985, 96), zf + LS * 0.020, zf + LS * 0.028, 0.0045, M_LAMP)
    for s_ in (-1, 1):
        for xx in (0.62, 0.80): proj(s_ * HF * xx, zf - LS * 0.008, 0.022)
        fsheet(span(s_ * EF * 0.84, s_ * EF * 0.985, 24), zf - LS * 0.030, zf - LS * 0.022, 0.0045, M_AMBER)
    fsheet(span(-EF - 0.003, EF + 0.003, 96), zf - LS * 0.039, zf + LS * 0.039, 0.008, M_LENS, 4)
    # ── 전조등 3. 슬림 2단: 위는 가는 주간주행등 한 줄, 아래 범퍼 쪽에 따로 떨어진 메인 램프 ──
    LAMPSET[0] = "f:slim"
    fsheet(span(-EF, EF, 96), zf + LS * 0.018, zf + LS * 0.036, 0.002, M_PIANO, 2)
    fsheet(span(-EF * 0.985, EF * 0.985, 96), zf + LS * 0.024, zf + LS * 0.030, 0.0045, M_LAMP)
    fsheet(span(-EF - 0.003, EF + 0.003, 96), zf + LS * 0.016, zf + LS * 0.038, 0.008, M_LENS, 2)
    zl = zf - LS * 0.11; EL = min(EF, face_ext(F_, zl, 1, 0.55) - 0.004)
    for s_ in (-1, 1):
        def sp_(a0, a1, n=24): return span(s_ * a0, s_ * a1, n)
        fsheet(sp_(EL * 0.55, EL * 0.92), zl - LS * 0.028, zl + LS * 0.028, 0.002, M_PIANO, 3)
        for xx in (0.64, 0.79): proj(s_ * EL * xx, zl, 0.016)
        fsheet(sp_(EL * 0.85, EL * 0.90, 4), zl - LS * 0.020, zl + LS * 0.020, 0.0045, M_AMBER, 2)
        fsheet(sp_(EL * 0.55 - 0.003, EL * 0.92 + 0.003), zl - LS * 0.031, zl + LS * 0.031, 0.008, M_LENS, 3)
    # ── 전조등 4. 원형 트윈: 좌우 둥근 램프 2개씩 (크롬 테) ──
    LAMPSET[0] = "f:round"
    for s_ in (-1, 1):
        for xx in (0.58, 0.82):
            x_ = s_ * EF * xx
            pod(x_, F_, zf, 1, LS * 0.040, 0.006, M_PIANO, 0.002); pod(x_, F_, zf, 1, LS * 0.034, 0.004, M_REFL, 0.004)
            pod(x_, F_, zf, 1, LS * 0.024, 0.004, M_LAMP, 0.006); pod(x_, F_, zf, 1, LS * 0.041, 0.003, M_LENS, 0.009)
        fsheet(span(s_ * EF * 0.66, s_ * EF * 0.92, 16), zf - LS * 0.056, zf - LS * 0.048, 0.0045, M_AMBER)
    # ── 전조등 5. 사선형: 안쪽 아래로 비스듬히 잘린 유닛 + 윗변 주간주행등 + 큰 프로젝터 1 ──
    LAMPSET[0] = "f:slant"
    for s_ in (-1, 1):
        def sx_(z, inset=0.0, outer=1.0): return span(s_ * (XF + inset + (zf + LS * 0.034 - z) * 1.4), s_ * EF * outer, 32)
        fsheet(lambda z: sx_(z), zf - LS * 0.034, zf + LS * 0.034, 0.002, M_PIANO, 4)
        fsheet(lambda z: sx_(z, 0.012, 0.985), zf + LS * 0.018, zf + LS * 0.026, 0.0045, M_LAMP)
        proj(s_ * (XF + EF) / 2 + s_ * 0.05, zf - LS * 0.006, 0.022)
        fsheet(span(s_ * EF * 0.80, s_ * EF * 0.985, 12), zf - LS * 0.028, zf - LS * 0.020, 0.0045, M_AMBER)
        fsheet(lambda z: sx_(z, -0.003, 1.0) if False else span(s_ * (XF - 0.003 + (zf + LS * 0.037 - z) * 1.4), s_ * (EF + 0.003), 32), zf - LS * 0.037, zf + LS * 0.037, 0.008, M_LENS, 4)
    # ── 실제 전조등 유형(조사: 연대별 대표 형태). 특정 브랜드 시그니처(링·T자·픽셀·발톱·C/L자 DRL)는 피한다 ──
    def lens_pod(x, z, r):   # 둥근 램프 한 개: 검정 테 + 반사갓 + 전구 + 볼록 커버
        pod(x, F_, z, 1, r * 1.12, 0.006, M_PIANO, 0.002); pod(x, F_, z, 1, r * 0.78, 0.004, M_REFL, 0.004)
        pod(x, F_, z, 1, r * 0.42, 0.003, M_LAMP, 0.006); pod(x, F_, z, 1, r * 1.02, 0.0015, M_LENS, 0.0085)
    def ribbed(xs, z0, z1):  # 사각 램프 속: 반사판 + 가로 줄무늬(실드빔 렌즈 결)
        fsheet(xs, z0, z1, 0.003, M_REFL, 2)
        for k_ in range(1, 4): zk = z0 + (z1 - z0) * k_ / 4; fsheet(xs, zk - 0.0012, zk + 0.0012, 0.0045, M_PIANO)
    EC = EF * 0.72; MID = (XF + EC) / 2; HV = LS * 0.034   # 클래식 램프는 모서리를 감싸지 않게 EC 안쪽에. HV = 램프 반높이
    # 1. 원형 단등: 좌우 큰 둥근 램프 하나씩 (1940~70년대 표준)
    LAMPSET[0] = "f:single"
    for s_ in (-1, 1):
        lens_pod(s_ * (MID + 0.04), zf, min(HV * 1.25, (EC - XF) * 0.32))
        fsheet(span(s_ * EC * 1.04, s_ * EF * 0.97, 8), zf - HV * 0.3, zf + HV * 0.3, 0.0045, M_AMBER)
    # 2. 사각 단등: 납작한 직사각형 하나씩 (70~80년대)
    LAMPSET[0] = "f:rect"
    for s_ in (-1, 1):
        def sp_(a0, a1, n=24): return span(s_ * a0, s_ * a1, n)
        fsheet(sp_(XF + 0.06, EC - 0.04), zf - HV, zf + HV, 0.002, M_PIANO, 4)
        ribbed(sp_(XF + 0.07, EC - 0.05), zf - HV * 0.8, zf + HV * 0.8)
        fsheet(sp_(EC - 0.09, EC - 0.055, 4), zf - HV * 0.8, zf + HV * 0.8, 0.0055, M_AMBER, 2)
        fsheet(sp_(XF + 0.057, EC - 0.037), zf - HV - 0.003, zf + HV + 0.003, 0.008, M_LENS, 4)
    # 3. 쿼드 사각: 작은 사각 램프 둘을 나란히 (4등식)
    LAMPSET[0] = "f:quad"
    for s_ in (-1, 1):
        def sp_(a0, a1, n=16): return span(s_ * a0, s_ * a1, n)
        fsheet(sp_(XF + 0.04, EC + 0.01), zf - HV * 0.85, zf + HV * 0.85, 0.002, M_PIANO, 4)
        for a0, a1 in ((XF + 0.05, MID - 0.008), (MID + 0.008, EC)):
            ribbed(sp_(a0, a1), zf - HV * 0.68, zf + HV * 0.68)
            fsheet(sp_(a0 + (a1 - a0) * 0.42, a0 + (a1 - a0) * 0.58, 4), zf - HV * 0.2, zf + HV * 0.2, 0.0055, M_LAMP)
            fsheet(sp_(a0 - 0.003, a1 + 0.003), zf - HV * 0.68 - 0.003, zf + HV * 0.68 + 0.003, 0.008, M_LENS, 3)
    # 4. 세로 스택: 둥근 램프 둘을 위아래로 (50~60년대)
    LAMPSET[0] = "f:stack"
    for s_ in (-1, 1):
        rs = HV * 0.62; xs_ = s_ * (EC - HV * 0.9)
        fsheet(span(xs_ - s_ * rs * 1.3, xs_ + s_ * rs * 1.3, 8), zf - rs * 2.3, zf + rs * 2.3, 0.002, M_PIANO, 6)
        for dz in (-1, 1): lens_pod(xs_, zf + dz * rs * 1.08, rs)
        fsheet(span(s_ * (EC + 0.02), s_ * (EF * 0.97), 8), zf - 0.006, zf + 0.006, 0.0045, M_AMBER)
    # 5. 팝업(켜진 모습으로 고정): 후드 앞끝에서 올라온 차체색 상자 + 앞면 둥근 램프 (움직임 없음)
    LAMPSET[0] = "f:popup"
    for s_ in (-1, 1):
        yb = F_ - 0.15; zt = interp(TOP, yb); bw = 0.11; bx = s_ * (XF + 0.17)
        bpy.ops.mesh.primitive_cube_add(location=(bx, yb, zt + 0.045)); o = bpy.context.object; o.scale = (bw, 0.07, 0.045); o.data.materials.append(M_PIANO); tag(o)
        for r_, m_, dy in ((0.046, M_PIANO, 0.002), (0.040, M_REFL, 0.004), (0.014, M_LAMP, 0.006), (0.042, M_LENS, 0.009)):
            bpy.ops.mesh.primitive_cylinder_add(radius=r_, depth=0.004, location=(bx, yb + 0.07 + dy, zt + 0.045), vertices=32)
            c = bpy.context.object; c.rotation_euler = (math.pi / 2, 0, 0); c.data.materials.append(m_); bpy.ops.object.shade_smooth(); tag(c)
        fsheet(span(s_ * EF * 0.70, s_ * EF * 0.95, 12), zf - 0.006, zf + 0.006, 0.0045, M_AMBER)   # 닫혀도 보이는 방향지시등
    # 6. 물방울 커버: 바깥이 높고 안쪽으로 가늘어지는 큰 투명 커버 + 반사갓 + 프로젝터 1 (90년대)
    LAMPSET[0] = "f:drop"
    for s_ in (-1, 1):
        def dx_(z, grow=0.0):
            t = min(1.0, abs(z - zf) / HV); return span(s_ * (XF + (EF - XF) * 0.55 * t ** 1.6 - grow), s_ * (EF + grow), 32)
        fsheet(lambda z: dx_(z), zf - HV, zf + HV, 0.002, M_PIANO, 8)
        fsheet(lambda z: dx_(z), zf - HV * 0.7, zf + HV * 0.7, 0.003, M_REFL, 6)
        proj(s_ * (XF + (EF - XF) * 0.62), zf, 0.024)
        fsheet(span(s_ * EF * 0.86, s_ * EF * 0.98, 6), zf - HV * 0.75, zf - HV * 0.45, 0.0045, M_AMBER)
        fsheet(lambda z: dx_(z, 0.003), zf - HV - 0.003, zf + HV + 0.003, 0.008, M_LENS, 8)
    # 7. 멀티 렌즈: 가로 하우징 안에 작은 원형 렌즈 4개를 한 줄로 (LED 멀티 렌즈. 3구는 특정 브랜드 시그니처라 피한다)
    LAMPSET[0] = "f:multi"
    for s_ in (-1, 1):
        def sp_(a0, a1, n=24): return span(s_ * a0, s_ * a1, n)
        fsheet(sp_(XF + 0.03, EC), zf - HV * 0.75, zf + HV * 0.75, 0.002, M_PIANO, 4)
        fsheet(sp_(XF + 0.04, EC - 0.01), zf + HV * 0.55, zf + HV * 0.66, 0.0045, M_LAMP)   # 위 가장자리 가는 일자 주간주행등
        for k_ in range(4): proj(s_ * (XF + 0.06 + (EC - XF - 0.11) * k_ / 3), zf - HV * 0.08, HV * 0.30 / LS)
        fsheet(sp_(EC + 0.015, EF * 0.97, 6), zf - HV * 0.35, zf + HV * 0.1, 0.0045, M_AMBER)
        fsheet(sp_(XF + 0.027, EC + 0.003), zf - HV * 0.75 - 0.003, zf + HV * 0.75 + 0.003, 0.008, M_LENS, 4)
    # 8. 리플렉터: 투명 커버 너머로 크롬 반사판이 다 보이는 90년대식 (바깥 칸은 호박색)
    LAMPSET[0] = "f:reflector"
    for s_ in (-1, 1):
        def sp_(a0, a1, n=24): return span(s_ * a0, s_ * a1, n)
        a0, a1 = XF + 0.04, EC
        fsheet(sp_(a0, a1), zf - HV, zf + HV, 0.002, M_PIANO, 4)
        fsheet(sp_(a0 + 0.008, a0 + (a1 - a0) * 0.78), zf - HV * 0.86, zf + HV * 0.86, 0.003, M_REFL, 4)
        pod(s_ * (a0 + (a1 - a0) * 0.40), F_, zf, 1, HV * 0.26, 0.004, M_LAMP, 0.0055)
        fsheet(sp_(a0 + (a1 - a0) * 0.80, a1 - 0.008), zf - HV * 0.86, zf + HV * 0.86, 0.0035, M_AMBER, 4)
        fsheet(sp_(a0 - 0.003, a1 + 0.003), zf - HV - 0.003, zf + HV + 0.003, 0.008, M_LENS, 4)
    # 9. 프로젝터 하우징: 둥근 끝 검정 하우징 안에 큰 어안 렌즈 하나 (빛나는 링 없음)
    LAMPSET[0] = "f:projector"
    for s_ in (-1, 1):
        x0_, x1_, rr_ = XF + 0.04, EC, HV * 0.92
        def hs_(z, grow=0.0, n=32):
            dz = min(abs(z - zf), rr_ + grow - 1e-4); ins = (rr_ + grow) - math.sqrt((rr_ + grow) ** 2 - dz * dz)
            return span(s_ * (x0_ - grow + ins), s_ * (x1_ + grow - ins), n)
        fsheet(lambda z: hs_(z), zf - rr_ + 0.0005, zf + rr_ - 0.0005, 0.002, M_PIANO, 8)
        xp = s_ * (x0_ + rr_ * 1.05)
        pod(xp, F_, zf, 1, rr_ * 0.80, 0.006, M_REFL, 0.004); pod(xp, F_, zf, 1, rr_ * 0.58, 0.008, M_LENS, 0.008); pod(xp, F_, zf, 1, rr_ * 0.30, 0.003, M_LAMP, 0.0065)
        fsheet(span(s_ * (x0_ + rr_ * 2.3), s_ * (x1_ - rr_ * 0.6), 12), zf - 0.004, zf + 0.004, 0.0045, M_LAMP)   # 하우징 안 가는 일자 줄
        fsheet(span(s_ * (x1_ - rr_ * 0.55), s_ * (x1_ - 0.012), 4), zf - rr_ * 0.4, zf + rr_ * 0.1, 0.0045, M_AMBER)
        fsheet(lambda z: hs_(z, 0.003), zf - rr_ - 0.0025, zf + rr_ + 0.0025, 0.008, M_LENS, 8)
    XI = ER * 0.46                                                     # 분리형 안쪽 끝 (가운데 차체색)
    # ── 후미등 1. 분리형(기본): 좌우 유닛, 일자 붉은 빛 + 안쪽 짧은 세로 + 아래 호박색·후진등 (L자는 특정 브랜드 시그니처라 뺐다) ──
    LAMPSET[0] = "r:split"
    for s_ in (-1, 1):
        def sp_(a0, a1, n=32): return span(s_ * a0, s_ * a1, n)
        rsheet(sp_(XI, ER), zr - 0.034, zr + 0.034, 0.002, M_PIANO, 4)
        rsheet(sp_(XI + 0.012, ER * 0.985), zr + 0.010, zr + 0.020, 0.0045, M_TAIL)
        rsheet(sp_(XI + 0.012, XI + 0.024, 2), zr - 0.008, zr + 0.020, 0.0045, M_TAIL, 2)
        rsheet(sp_(ER * 0.64, ER * 0.86, 16), zr - 0.026, zr - 0.016, 0.0045, M_AMBER)
        rsheet(sp_(XI + 0.04, ER * 0.60, 16), zr - 0.026, zr - 0.016, 0.0045, M_REFL)
        rsheet(sp_(XI - 0.003, ER + 0.003), zr - 0.037, zr + 0.037, 0.008, M_TAILLENS, 4)
    # ── 후미등 2. 라이트바: 전폭 붉은 줄 + 양 끝 세로 블록 ──
    LAMPSET[0] = "r:bar"
    rsheet(span(-ER, ER, 96), zr - 0.030, zr + 0.030, 0.002, M_PIANO, 4)
    rsheet(span(-ER * 0.985, ER * 0.985, 96), zr + 0.008, zr + 0.018, 0.0045, M_TAIL)
    for s_ in (-1, 1):
        rsheet(span(s_ * ER * 0.90, s_ * ER * 0.985, 16), zr - 0.022, zr + 0.018, 0.0045, M_TAIL, 2)
        rsheet(span(s_ * ER * 0.62, s_ * ER * 0.86, 24), zr - 0.022, zr - 0.012, 0.0045, M_AMBER)
        rsheet(span(s_ * HR * 0.18, s_ * HR * 0.40, 16), zr - 0.022, zr - 0.012, 0.0045, M_REFL)
    rsheet(span(-ER - 0.003, ER + 0.003, 96), zr - 0.033, zr + 0.033, 0.008, M_TAILLENS, 4)
    # ── 후미등 3. 세로형: 양 모서리에 세로로 긴 유닛 (붉은 테두리 빛 + 안쪽 호박색·후진등) ──
    LAMPSET[0] = "r:vertical"
    zb_ = zr - 0.085
    for s_ in (-1, 1):
        def sp_(a0, a1, n=12): return span(s_ * a0, s_ * a1, n)
        rsheet(sp_(ER * 0.80, ER), zb_, zr + 0.034, 0.002, M_PIANO, 8)
        rsheet(sp_(ER * 0.95, ER * 0.985, 4), zb_ + 0.006, zr + 0.028, 0.0045, M_TAIL, 8)
        rsheet(sp_(ER * 0.80 + 0.008, ER * 0.80 + 0.018, 2), zb_ + 0.006, zr + 0.028, 0.0045, M_TAIL, 8)
        rsheet(sp_(ER * 0.80 + 0.008, ER * 0.985), zr + 0.020, zr + 0.028, 0.0045, M_TAIL)
        rsheet(sp_(ER * 0.84, ER * 0.92), zr - 0.030, zr - 0.012, 0.0045, M_AMBER, 2)
        rsheet(sp_(ER * 0.84, ER * 0.92), zb_ + 0.016, zb_ + 0.034, 0.0045, M_REFL, 2)
        rsheet(sp_(ER * 0.80 - 0.003, ER + 0.003), zb_ - 0.003, zr + 0.037, 0.008, M_TAILLENS, 8)
    # ── 후미등 4. 원형 트윈: 좌우 붉은 고리 2개씩 + 안쪽 작은 후진등 ──
    LAMPSET[0] = "r:round"
    for s_ in (-1, 1):
        for xx in (0.74,):   # 좌우 2개씩(쌍원)은 특정 차 시그니처라 큰 원 하나씩
            x_ = s_ * ER * xx
            pod(x_, R_, zr, -1, 0.036 * 1.35, 0.006, M_PIANO, 0.002); pod(x_, R_, zr, -1, 0.030 * 1.35, 0.004, M_TAIL, 0.004)
            pod(x_, R_, zr, -1, 0.017 * 1.35, 0.004, M_PIANO, 0.0055); pod(x_, R_, zr, -1, 0.037 * 1.35, 0.003, M_TAILLENS, 0.008)
        pod(s_ * ER * 0.44, R_, zr, -1, 0.016, 0.004, M_REFL, 0.004)
    # ── 후미등 5. 2단 줄: 좌우 유닛 안에 붉은 가로줄 두 줄 ──
    LAMPSET[0] = "r:dual"
    for s_ in (-1, 1):
        def sp_(a0, a1, n=32): return span(s_ * a0, s_ * a1, n)
        rsheet(sp_(XI, ER), zr - 0.034, zr + 0.034, 0.002, M_PIANO, 4)
        rsheet(sp_(XI + 0.012, ER * 0.985), zr + 0.012, zr + 0.020, 0.0045, M_TAIL)
        rsheet(sp_(XI + 0.012, ER * 0.985), zr - 0.006, zr + 0.002, 0.0045, M_TAIL)
        rsheet(sp_(ER * 0.64, ER * 0.86, 16), zr - 0.028, zr - 0.020, 0.0045, M_AMBER)
        rsheet(sp_(XI + 0.04, ER * 0.60, 16), zr - 0.028, zr - 0.020, 0.0045, M_REFL)
        rsheet(sp_(XI - 0.003, ER + 0.003), zr - 0.037, zr + 0.037, 0.008, M_TAILLENS, 4)
    # ── 실제 후미등 유형(조사). 세로 3줄·쌍원·L자·D필러 세로·픽셀 격자·화살표·Y자·부메랑은 특정 브랜드 시그니처라 피한다 ──
    HT = 0.032   # 후미등 반높이
    # 6. 칸나눔: 가로 사각 렌즈를 빨강·호박·흰색 세 칸으로 (70~90년대 대중 세단)
    LAMPSET[0] = "r:band"
    for s_ in (-1, 1):
        def sp_(a0, a1, n=16): return span(s_ * a0, s_ * a1, n)
        rsheet(sp_(XI, ER), zr - HT, zr + HT, 0.002, M_PIANO, 4)
        rsheet(sp_(ER * 0.68, ER * 0.985), zr - HT * 0.82, zr + HT * 0.82, 0.0045, M_TAIL, 3)
        rsheet(sp_(XI + (ER - XI) * 0.42, ER * 0.665), zr - HT * 0.82, zr + HT * 0.82, 0.0045, M_AMBER, 3)
        rsheet(sp_(XI + 0.01, XI + (ER - XI) * 0.40), zr - HT * 0.82, zr + HT * 0.82, 0.0045, M_REFL, 3)
        rsheet(sp_(ER * 0.675, ER + 0.003), zr - HT - 0.003, zr + HT + 0.003, 0.008, M_TAILLENS, 4)
        rsheet(sp_(XI - 0.003, ER * 0.672), zr - HT - 0.003, zr + HT + 0.003, 0.008, M_LENS, 4)
    # 7. 통패널: 트렁크 폭 전체를 덮는 높은 붉은 판 + 가는 가로줄 (70~80년대. 얇은 라이트바와 높이로 구별)
    LAMPSET[0] = "r:panel"
    rsheet(span(-ER, ER, 96), zr - HT * 1.6, zr + HT * 1.2, 0.002, M_PIANO, 6)
    rsheet(span(-ER * 0.985, ER * 0.985, 96), zr - HT * 1.45, zr + HT * 1.05, 0.004, M_TAIL, 6)
    for k_ in range(1, 6): zk = zr - HT * 1.45 + HT * 2.5 * k_ / 6; rsheet(span(-ER * 0.985, ER * 0.985, 96), zk - 0.0015, zk + 0.0015, 0.0055, M_PIANO)
    rsheet(span(-ER - 0.003, ER + 0.003, 96), zr - HT * 1.6 - 0.003, zr + HT * 1.2 + 0.003, 0.008, M_TAILLENS, 6)
    # 8. 감싸기: 렌즈가 뒷면 모서리를 돌아 옆면 쪽까지 이어진다 (50~60년대 이후 일반화)
    LAMPSET[0] = "r:wrap"
    EW = face_ext(Y_REAR, zr, -1, 0.10) - 0.004
    for s_ in (-1, 1):
        def sp_(a0, a1, n=32): return span(s_ * a0, s_ * a1, n)
        rsheet(sp_(ER * 0.60, EW), zr - HT, zr + HT, 0.002, M_PIANO, 4)
        rsheet(sp_(ER * 0.62, EW - 0.004), zr - HT * 0.15, zr + HT * 0.8, 0.0045, M_TAIL, 3)
        rsheet(sp_(ER * 0.62, ER * 0.80), zr - HT * 0.8, zr - HT * 0.3, 0.0045, M_REFL, 2)
        rsheet(sp_(ER * 0.82, EW - 0.004), zr - HT * 0.8, zr - HT * 0.3, 0.0045, M_AMBER, 2)
        rsheet(sp_(ER * 0.60 - 0.003, EW + 0.002), zr - HT - 0.003, zr + HT + 0.003, 0.008, M_TAILLENS, 4)
    # 9. 범퍼형: 차체에는 램프가 없고 범퍼 아래쪽에 작은 사각 램프 한 쌍 (오프로더·트럭식)
    LAMPSET[0] = "r:bumper"
    zbm = interp(FLOOR, R_) + 0.12
    for s_ in (-1, 1):
        def sp_(a0, a1, n=12): return span(s_ * a0, s_ * a1, n)
        rsheet(sp_(ER * 0.74, ER * 1.02), zbm - 0.026, zbm + 0.026, 0.002, M_PIANO, 3)
        rsheet(sp_(ER * 0.84, ER * 1.005), zbm - 0.020, zbm + 0.020, 0.0045, M_TAIL, 2)
        rsheet(sp_(ER * 0.76, ER * 0.82), zbm - 0.020, zbm + 0.020, 0.0045, M_REFL, 2)
        rsheet(sp_(ER * 0.74 - 0.003, ER * 1.02 + 0.003), zbm - 0.029, zbm + 0.029, 0.008, M_LENS, 3)
    # 10. 투명 렌즈: 맑은 커버 안에 크롬 하우징과 둥근 붉은 램프 둘이 비친다 (90년대 말 유행)
    LAMPSET[0] = "r:clear"
    for s_ in (-1, 1):
        def sp_(a0, a1, n=24): return span(s_ * a0, s_ * a1, n)
        rsheet(sp_(XI, ER), zr - HT, zr + HT, 0.002, M_PIANO, 4)
        rsheet(sp_(XI + 0.008, ER * 0.985), zr - HT * 0.86, zr + HT * 0.86, 0.003, M_REFL, 4)
        for t_, r_, m_ in ((0.72, HT * 0.78, M_TAIL), (0.40, HT * 0.62, M_TAIL), (0.12, HT * 0.36, M_PIANO)):
            x_ = s_ * (XI + (ER - XI) * t_)
            pod(x_, R_, zr, -1, r_, 0.004, m_, 0.005)
            if m_ is M_TAIL: pod(x_, R_, zr, -1, r_ * 1.02, 0.002, M_TAILLENS, 0.0075)   # 밤에 붉은빛이 하얗게 날아가지 않게
        rsheet(sp_(XI - 0.003, ER + 0.003), zr - HT - 0.003, zr + HT + 0.003, 0.008, M_LENS, 4)
    # 11. 스모크: 짙은 커버 안에 가는 붉은 줄 두 개만 (꺼져 있으면 검게 보인다)
    LAMPSET[0] = "r:smoke"
    for s_ in (-1, 1):
        def sp_(a0, a1, n=24): return span(s_ * a0, s_ * a1, n)
        rsheet(sp_(XI, ER), zr - HT, zr + HT, 0.002, M_PIANO, 4)
        rsheet(sp_(XI + 0.02, ER * 0.98), zr + HT * 0.45, zr + HT * 0.62, 0.0045, M_TAIL)
        rsheet(sp_(XI + 0.06, ER * 0.98), zr - HT * 0.25, zr - HT * 0.10, 0.0045, M_TAIL)
        rsheet(sp_(XI - 0.003, ER + 0.003), zr - HT - 0.003, zr + HT + 0.003, 0.008, M_LENS, 4)
    # 12. 블레이드: 검은 바탕 위에 길이가 다른 얇은 붉은 날 3장이 층층이 떠 있다 (입체)
    LAMPSET[0] = "r:blade"
    for s_ in (-1, 1):
        def sp_(a0, a1, n=24): return span(s_ * a0, s_ * a1, n)
        rsheet(sp_(XI, ER), zr - HT, zr + HT, 0.002, M_PIANO, 4)
        for k_, (zc, a0, off) in enumerate(((zr + HT * 0.55, XI + 0.02, 0.006), (zr, XI + (ER - XI) * 0.25, 0.012), (zr - HT * 0.55, XI + (ER - XI) * 0.5, 0.018))):
            rsheet(sp_(a0, ER * 0.985), zc - 0.004, zc + 0.004, off, M_TAIL)
    LAMPSET[0] = None
    # Blender 정지 렌더에서는 기본(분리형)만 보이게
    for o in scene.objects:
        if o.get("lampset") and o["lampset"] not in ("f:split", "r:split"): o.hide_render = True
    LAMP_Z = (zf, zr)
if ST.get("grille", True):
    bpy.ops.mesh.primitive_cube_add(location=(0, Y_FRONT - 0.07, catmull(FLOOR, Y_FRONT) + 0.12)); gr = bpy.context.object; gr.scale = (_W * 0.21, 0.06, 0.04); gr.data.materials.append(M_WELL)
def roof_half(y, tol=0.03):
    # 가운데 지붕 높이에서 tol 이상 내려가기 직전까지의 반폭 = 지붕 윗면 반폭
    ok, loc, n, i = body.ray_cast(Vector((0, y, 5.0)), Vector((0, 0, -1)))
    if not ok: return _W * 0.3
    z0 = loc.z; hw = 0.1
    for k in range(1, 120):
        x = _W / 2 * k / 120
        ok, loc, n, i = body.ray_cast(Vector((x, y, 5.0)), Vector((0, 0, -1)))
        if not ok or z0 - loc.z > tol: break
        hw = x
    return hw
bpy.ops.mesh.primitive_cube_add(location=(0, SP_Y, SP_Z)); sp = bpy.context.object; sp.scale = (_W * 0.31, 0.09, 0.010); sp.rotation_euler.x = math.radians(-14); sp.data.materials.append(M_TRIM)
if not P["spoiler"]: bpy.data.objects.remove(sp)
else:
    def rz0(y):
        ok, loc, n, i = body.ray_cast(Vector((0, y, 5.0)), Vector((0, 0, -1)))
        return loc.z if ok else -9.0
    yend = SP_Y
    y = -HL * 0.3
    while y > -HL + 0.05:
        if (rz0(y) - rz0(y - 0.05)) / 0.05 > 0.30: yend = y; break   # 완만한 지붕 경사(약 0.2)는 건너뛰고 해치로 꺾이는 곳
        y -= 0.01
    th = math.atan2(rz0(yend + 0.15) - rz0(yend + 0.05), 0.10) - math.radians(4)   # 지붕 각도보다 4° 들어 끝이 살짝 들린다
    hl, ht = 0.09, 0.009; _wr = min(roof_half(yend + 0.06), roof_half(yend - 0.06)) * 0.92
    yF = yend + 0.11; zF = rz0(yF) + 0.003                                         # 앞 끝 아랫면이 지붕에 닿는 점
    cy = yF - (hl * math.cos(th) + ht * math.sin(th)); cz = zF - (hl * math.sin(th) - ht * math.cos(th))
    sp.scale = (_wr, hl, ht); sp.rotation_euler = (th, 0, 0); sp.location = (0, cy, cz)
    sp.data.materials.clear(); sp.data.materials.append(M_PAINT)
    b_ = sp.modifiers.new("b", "BEVEL"); b_.width = 0.006; b_.segments = 3
    for s_ in (-1, 1):   # 양 끝 날개판(유광 검정)
        bpy.ops.mesh.primitive_cube_add(location=(s_ * (_wr - 0.006), cy - 0.01 * math.cos(th), cz - 0.012))
        ep = bpy.context.object; ep.scale = (0.008, hl * 0.92, max(0.022, (rz0(yF) - rz0(yF - 2 * hl) + 0.02) / 2)); ep.rotation_euler = (th, 0, 0); ep.data.materials.append(M_PIANO)
        ep.location.z = cz - ep.scale.z + 0.012
    SPOILER_END = yend
    print("SPOILER", round(yend, 3), round(_wr, 3), round(math.degrees(th), 1), flush=True)

# ---------- 디테일(부품): 창틀 몰딩, B필러, 손잡이, 사이드미러, 루프레일, 흡기구 살 ----------
if DETAIL:
    def box(loc, scale, m, rot=(0, 0, 0), bevel=0.0):
        bpy.ops.mesh.primitive_cube_add(location=loc); o = bpy.context.object; o.scale = scale; o.rotation_euler = rot; o.data.materials.append(m)
        if bevel:
            bpy.ops.object.transform_apply(scale=True); bm_ = o.modifiers.new("b", "BEVEL"); bm_.width = bevel; bm_.segments = 3
            bpy.ops.object.modifier_apply(modifier="b"); bpy.ops.object.shade_smooth()
        return o
    def strip(pts, m, name):
        # pts: [(아래점, 위점)] 순서대로 이은 얇은 띠 (곡면을 따라가는 몰딩)
        me = bpy.data.meshes.new(name); vs = [p for pair in pts for p in pair]
        fs_ = [(i * 2, i * 2 + 1, i * 2 + 3, i * 2 + 2) for i in range(len(pts) - 1)]
        me.from_pydata(vs, [], fs_); me.update(); o = bpy.data.objects.new(name, me); scene.collection.objects.link(o); o.data.materials.append(m)
        so = o.modifiers.new("s", "SOLIDIFY"); so.thickness = 0.006; so.offset = 0
        return o
    def ray_side(s, y, z):
        ok, loc, n, i = body.ray_cast(Vector((s * 3.0, y, z)), Vector((-s, 0, 0)))
        return (loc, n) if ok else (None, None)
    def roof_z(x, y):
        ok, loc, nrm, idx = body.ray_cast(Vector((x, y, 5.0)), Vector((0, 0, -1)))
        return loc.z if ok else interp(TOP, y)
    def side_sheet(s, cols, rows, off, m, thick=0.003, name="sidesheet"):
        # 옆면을 광선으로 따라가는 얇은 판. cols: y 목록, rows: [z(y) 함수] → 각 점 = 표면 + 법선 × off
        grid = []
        for zf_ in rows:
            row = []
            for y in cols:
                loc, n = ray_side(s, y, zf_(y))
                if loc is None: return None
                row.append(tuple(loc + n * off))
            grid.append(row)
        nx = len(cols); vs = [p for r in grid for p in r]
        fs_ = [(j * nx + i, j * nx + i + 1, (j + 1) * nx + i + 1, (j + 1) * nx + i) for j in range(len(rows) - 1) for i in range(nx - 1)]
        me = bpy.data.meshes.new(name); me.from_pydata(vs, [], fs_); me.update()
        o = bpy.data.objects.new(name, me); scene.collection.objects.link(o); o.data.materials.append(m)
        for p in me.polygons: p.use_smooth = True
        if thick: so = o.modifiers.new("s", "SOLIDIFY"); so.thickness = thick; so.offset = 0
        return o
    def lin(a0, a1, n): return [a0 + (a1 - a0) * k / n for k in range(n + 1)]
    # 창틀 아래 몰딩(새틴 크롬): A필러 밑동에서 뒷유리 끝(C필러)까지만
    ys_belt = lin(WSHIELD_END - 0.02, HL * P.get("gend", -0.8) + 0.03, 60)
    for s in (-1, 1):
        side_sheet(s, ys_belt, [lambda y: interp(BELT, y) - 0.004, lambda y: interp(BELT, y) + 0.012], 0.002, M_MOLD, thick=0.004, name="beltline")
    # B필러: 유광 검정 가니시. 벨트 위에서 지붕 레일 바로 아래까지 표면을 따라 (지붕 위로 솟지 않게)
    for s in (-1, 1):
        side_sheet(s, lin(DOOR_M - 0.045, DOOR_M + 0.045, 6),
                   [(lambda k: (lambda y: interp(BELT, y) - 0.006 + (interp(TOP, y) - 0.06 - interp(BELT, y) + 0.006) * k / 10))(k) for k in range(11)],
                   0.002, M_PIANO, thick=0.002, name="bpillar")
        # 뒷문 뒤 분할 기둥(유광 검정): 그 뒤 유리는 고정 쿼터글라스로 읽힌다
        if HL * P.get("gend", -0.8) < DOOR_B - 0.08:
            side_sheet(s, lin(DOOR_B - 0.014, DOOR_B + 0.014, 2),
                       [(lambda k: (lambda y: interp(BELT, y) - 0.006 + (interp(TOP, y) - 0.06 - interp(BELT, y) + 0.006) * k / 10))(k) for k in range(11)],
                       0.002, M_PIANO, thick=0.002, name="qpillar")
    # 손잡이(플러시형): 각 문 뒤쪽, 블레이드 날 위. 유광 검정 + 위 끝 크롬 줄
    for yh in (DOOR_M + 0.21, DOOR_B + 0.21):
        for s in (-1, 1):
            zh = lambda y: interp(BELT, yh) - 0.075
            side_sheet(s, lin(yh - 0.12, yh + 0.12, 12), [lambda y: zh(y) - 0.015, lambda y: zh(y), lambda y: zh(y) + 0.015], 0.0025, M_PIANO, thick=0.004, name="handle")
            side_sheet(s, lin(yh - 0.115, yh + 0.115, 12), [lambda y: zh(y) + 0.014, lambda y: zh(y) + 0.018], 0.0045, M_MOLD, thick=0.002, name="handle_trim")
    # 사이드미러: 앞문 위 앞모서리에 꽂힌 팔 + 실차 비례 머리(바깥으로 20 cm, 높이 12 cm). 머리 앞 아래에 호박색 방향지시등
    ym = max(COWL - 0.07, DOOR_A - 0.09)
    for s in (-1, 1):
        zb_ = interp(BELT, ym); loc, n = ray_side(s, ym, zb_ + 0.03)
        xs_ = abs(loc.x) if loc is not None else interp(WBELT, ym)
        box((s * (xs_ + 0.035), ym, zb_ + 0.035), (0.04, 0.03, 0.012), M_TRIM, bevel=0.006)                       # 팔
        box((s * (xs_ + 0.115), ym - 0.01, zb_ + 0.085), (0.095, 0.042, 0.058), M_MIRROR, rot=(0, 0, s * math.radians(-6)), bevel=0.022)   # 머리(차체색, 장식 색 칸)
        box((s * (xs_ + 0.115), ym - 0.015, zb_ + 0.040), (0.09, 0.038, 0.012), M_PIANO, rot=(0, 0, s * math.radians(-6)), bevel=0.008)  # 아래 덮개
        box((s * (xs_ + 0.15), ym + 0.031, zb_ + 0.055), (0.05, 0.004, 0.004), M_AMBER, rot=(0, 0, s * math.radians(-6)))              # 방향지시등
        box((s * (xs_ + 0.115), ym - 0.053, zb_ + 0.088), (0.08, 0.002, 0.046), M_REFL, rot=(0, 0, s * math.radians(-6)))              # 거울면
    # 루프레일(SUV): 지붕 곡면을 12점에서 재어 따라가는 레일 + 받침 3개, 양 끝은 지붕 쪽으로 꺾인다
    if _kind == "suv":
        y0, y1 = WSHIELD_END - 0.12, -HL * 0.72
        for s in (-1, 1):
            xr = min(roof_half(y0), roof_half(y1)) * 0.86
            pts = [(y, roof_z(s * xr, y)) for y in lin(y0, y1, 12)]
            rail = [(y, z + 0.046) for y, z in pts]
            rail[0] = (rail[0][0] + 0.02, pts[0][1] + 0.008); rail[-1] = (rail[-1][0] - 0.02, pts[-1][1] + 0.008)
            for (ya, za), (yb, zb2) in zip(rail, rail[1:]):
                ln = math.hypot(ya - yb, za - zb2)
                box((s * xr, (ya + yb) / 2, (za + zb2) / 2), (0.016, ln / 2 + 0.004, 0.013), M_CHROME, rot=(math.atan2(za - zb2, ya - yb), 0, 0), bevel=0.004)
            for k in (1, 6, 11):
                box((s * xr, pts[k][0], pts[k][1] + 0.022), (0.02, 0.045, 0.022), M_TRIM, bevel=0.006)
    # SUV 휠 아치 클래딩: 둥근 파이프 대신 아치를 따라 차체에 붙은 납작한 무광 띠 (폭 5.5 cm, 8 mm 돌출)
    if _kind == "suv":
        for yw in WHEELS_Y:
            for s in (-1, 1):
                zmin = interp(FLOOR, yw) + 0.10
                angs = [a_ for a_ in lin(-math.radians(100), math.radians(100), 48)]
                angs = [a_ for a_ in angs if WHEEL_R + (_archr + 0.03) * math.cos(a_) > zmin]
                def ok_ang(a_):
                    y_ = yw + (_archr + 0.058) * math.sin(a_); z_ = WHEEL_R + (_archr + 0.058) * math.cos(a_)
                    loc, n = ray_side(s, y_, z_)
                    return loc is not None and n.x * s > 0.6 and z_ < interp(BELT, y_) - 0.02
                angs = [a_ for a_ in angs if ok_ang(a_)]
                rows_ = [(lambda rr_: (lambda y: None))(rr_) for rr_ in ()]
                grid = []
                for rr_ in (_archr + 0.004, _archr + 0.03, _archr + 0.058):
                    row = []
                    for a_ in angs:
                        y_ = yw + rr_ * math.sin(a_); z_ = WHEEL_R + rr_ * math.cos(a_)
                        loc, n = ray_side(s, y_, z_)
                        row.append(tuple(loc + n * 0.006) if loc is not None else None)
                    grid.append(row)
                if any(p is None for r_ in grid for p in r_): continue
                nx = len(angs); vs = [p for r_ in grid for p in r_]
                fs_ = [(j * nx + i, j * nx + i + 1, (j + 1) * nx + i + 1, (j + 1) * nx + i) for j in range(2) for i in range(nx - 1)]
                me = bpy.data.meshes.new("cladding"); me.from_pydata(vs, [], fs_); me.update()
                o = bpy.data.objects.new("cladding", me); scene.collection.objects.link(o); o.data.materials.append(M_TRIM)
                for p in me.polygons: p.use_smooth = True
                so = o.modifiers.new("s", "SOLIDIFY"); so.thickness = 0.012; so.offset = 0
                bv_ = o.modifiers.new("b", "BEVEL"); bv_.width = 0.003; bv_.segments = 2
    # 번호판(렌더용. 앱에서는 페이지가 글자와 함께 그리므로 내보내지 않는다)
    # 높이를 한 값으로 맞추고(내보내는 값과 같게), 표면에 검정 받침을 깐다
    ZP = {1: interp(FLOOR, Y_FRONT) + 0.21, -1: interp(FLOOR, Y_REAR) + 0.22}
    YP = {}
    for ye, sgn in ((Y_FRONT, 1), (Y_REAR, -1)):
        conform(sgn, ye, span(-0.275, 0.275, 24), rows(ZP[sgn] - 0.065, ZP[sgn] + 0.065, 2), 0.0015, M_TRIM, thick=0.003, name="plate_base")
        loc_, n_ = hit(0, ye, ZP[sgn], sgn); YP[sgn] = loc_.y if loc_ is not None else ye
        o = box((0, YP[sgn] + sgn * 0.006, ZP[sgn]), (0.26, 0.002, 0.055), M_PLATE); o.name = "plate"
    # 뒷면 맨 아래(범퍼 아래 세로면) 높이: 뒤에서 쏜 광선이 뒤를 보는 면에 맞는 가장 낮은 z
    def low_face(y0, sgn):
        z = interp(FLOOR, y0)
        for _ in range(60):
            loc, nrm = hit(0, y0, z, sgn)
            if loc is not None and nrm.y * sgn > 0.6: return z
            z += 0.005
        return interp(FLOOR, y0) + 0.08
    zlo = low_face(Y_REAR, -1)
    # 디퓨저: 범퍼 아래 세로면에 붙은 검정 판 + 뒤로 2 cm 나온 핀 4개 (차 밑으로 매달리지 않게)
    EDf = face_ext(Y_REAR, zlo + 0.05, -1, 0.6) * 0.80
    for tg_ in ("rb:base", "rb:towhook"):   # 기본 디퓨저 (튜닝에서 다른 뒷범퍼를 고르면 숨긴다)
        LAMPSET[0] = tg_
        conform(-1, Y_REAR, span(-EDf, EDf, 48), rows(zlo + 0.01, zlo + 0.09, 2), 0.002, M_TRIM, thick=0.003, name="diffuser")
        for fx in (-0.36, -0.12, 0.12, 0.36):
            conform(-1, Y_REAR, span(EDf * fx - 0.004, EDf * fx + 0.004, 2), rows(zlo + 0.012, zlo + 0.07, 2), 0.012, M_TRIM, thick=0.02, name="fin")
    LAMPSET[0] = None
    # 배기구 자리(페이지가 연료별로 그린다): 디퓨저 양끝 안쪽
    loc_, n_ = hit(_W * 0.30, Y_REAR, zlo + 0.05, -1)
    EXH = (zlo + 0.05, (loc_.y if loc_ is not None else Y_REAR))
    # 번호등 2개 (뒤 번호판 위)
    for px in (-0.12, 0.12):
        conform(-1, Y_REAR, span(px - 0.015, px + 0.015, 2), rows(ZP[-1] + 0.068, ZP[-1] + 0.074, 1), 0.004, M_LAMP, thick=0.003, name="plate_lamp")
    # SUV·경차 뒷유리: 후미등 위에서 지붕 끝까지 뒷면을 따라. 검정 테두리(프릿) 위에 유리, 위 가운데 보조제동등, 아래 와이퍼
    if _kind in ("suv", "mini"):
        # 해치는 비스듬해서 뒤에서 수평으로 쏘면 스치듯 맞는다. 후미등 위(A)와 지붕 끝(B)을 잇는 현에 수직으로 쏴서 해치 면을 잡는다
        locA, _ = hit(0, Y_REAR, LAMP_Z[1] + 0.05, -1)
        yB = globals().get("SPOILER_END", -HL * 0.85)
        okB, locB, _n, _i = body.ray_cast(Vector((0, yB, 5.0)), Vector((0, 0, -1)))
        if locA is not None and okB:
            A = Vector((0, locA.y, locA.z)); Bv = Vector((0, locB.y, locB.z))
            ch = (Bv - A).normalized(); nout = Vector((0, -ch.z, ch.y))          # 현에 수직, 뒤·위 바깥
            def hhit(x, u):
                o_ = A + (Bv - A) * u + Vector((x, 0, 0)) + nout * 1.0
                ok, loc, nrm, idx = body.ray_cast(o_, -nout)
                return (loc, nrm) if ok and nrm.dot(nout) > 0.70 else (None, None)
            def hext(u):
                hw = 0.05
                for i in range(1, 160):
                    x = _W / 2 * i / 160; loc, nrm = hhit(x, u)
                    if loc is None: break
                    hw = x
                return hw
            us = [0.06 + 0.86 * k / 24 for k in range(25)]                       # 아래는 후미등 위 여유, 위는 스포일러 밑
            ws = [hext(u) * (0.70 if _kind == "mini" else 0.80) for u in us]
            ws = [min(ws[max(i - 1, 0):i + 2]) for i in range(len(ws))]          # 이웃 줄과 비교해 튀는 폭을 누른다
            keep = [i for i, w_ in enumerate(ws) if w_ > 0.18]                   # 폭이 너무 좁은 줄(지붕으로 넘어가는 곳)은 뺀다
            us = [us[i] for i in keep]; ws = [ws[i] for i in keep]
            if len(ws) >= 3:
                # 계단처럼 들쭉날쭉하지 않게: 아래·위 폭을 곧게 잇는 사다리꼴로 바꾸고, 어느 줄에서도 잰 폭을 넘지 않게 전체를 줄인다
                n3 = max(1, len(ws) // 3)
                wb_, wt_ = min(ws[:n3]), min(ws[-n3:])
                lin_ = [wb_ + (wt_ - wb_) * i / (len(ws) - 1) for i in range(len(ws))]
                k_ = min(1.0, min(w_ / l_ for w_, l_ in zip(ws, lin_)))
                ws = [l_ * k_ for l_ in lin_]
            L_ = (Bv - A).length; EG = min(ws) if ws else 0
            def hsheet(fr, uu, wu, inset, off, m, thick, name):
                # fr: -1..1 비율 열, uu: 줄 위치, wu: 줄마다 반폭 → 위가 좁은 사다리꼴 유리
                grid = []
                for u, w_ in zip(uu, wu):
                    row = []
                    for f in fr:
                        loc, nrm = hhit(f * (w_ - inset), u)
                        if loc is None: return None
                        row.append(tuple(loc + nrm * off))
                    grid.append(row)
                nx = len(fr); vs = [p for r_ in grid for p in r_]
                fs_ = [(j * nx + i, j * nx + i + 1, (j + 1) * nx + i + 1, (j + 1) * nx + i) for j in range(len(uu) - 1) for i in range(nx - 1)]
                me = bpy.data.meshes.new(name); me.from_pydata(vs, [], fs_); me.update()
                o = bpy.data.objects.new(name, me); scene.collection.objects.link(o); o.data.materials.append(m)
                for p in me.polygons: p.use_smooth = True
                if thick: so = o.modifiers.new("s", "SOLIDIFY"); so.thickness = thick; so.offset = 0
                return o
            if len(us) >= 6:
                fr = [-1 + 2 * k / 64 for k in range(65)]; du = 0.018 / L_
                hsheet(fr, us, ws, 0.0, 0.004, M_PIANO, 0.002, "rear_frit")
                gu = [u for u in us if us[0] + du <= u <= us[-1] - du]; gw = [w_ for u, w_ in zip(us, ws) if us[0] + du <= u <= us[-1] - du]
                hsheet(fr, gu, gw, 0.018, 0.007, M_GLASS, 0.002, "rear_glass")
                hsheet([-1 + 2 * k / 12 for k in range(13)], [us[-1] - 0.012 / L_, us[-1] - 0.004 / L_], [0.12, 0.12], 0.0, 0.009, M_TAILLENS, 0.003, "hmsl")   # 보조제동등(평소 꺼짐)
                hsheet([k / 16 for k in range(17)], [us[0] + 0.026 / L_, us[0] + 0.034 / L_], [ws[0] * 0.62] * 2, 0.0, 0.013, M_PIANO, 0.007, "rear_wiper")
            loc_, nrm_ = hhit(0, us[0] + 0.03 / L_)
            if loc_ is not None:
                bpy.ops.mesh.primitive_cylinder_add(radius=0.014, depth=0.02, location=tuple(loc_ + nrm_ * 0.01), vertices=24)
                pv = bpy.context.object; pv.rotation_euler = nrm_.to_track_quat("Z", "Y").to_euler(); pv.data.materials.append(M_PIANO)
            print("REARGLASS", round(A.z, 3), round(Bv.z, 3), round(EG, 3), round(L_, 3), flush=True)
    # 실내: 앞좌석 2개, 뒷좌석 벤치, 대시보드, 핸들(국내 좌핸들: 운전석은 -x)
    y_f = WSHIELD_END - 0.30; y_r = y_f - 0.85
    for yy, xs in ((y_f, (-1, 1)), (y_r, (0,))):
        if yy < -HL * 0.6: continue
        zb = interp(BELT, yy); zfl = interp(FLOOR, yy) + 0.16
        for s in xs:
            wseat = _W * (0.17 if s else 0.36)
            box((s * _W * 0.21, yy, zfl + 0.06), (wseat, 0.24, 0.06), M_SEAT, bevel=0.03)                      # 방석
            box((s * _W * 0.21, yy - 0.24, (zfl + zb) / 2 + 0.10), (wseat, 0.06, (zb - zfl) / 2 + 0.10), M_SEAT, rot=(math.radians(-12), 0, 0), bevel=0.03)   # 등받이
            if s: box((s * _W * 0.21, yy - 0.27, zb + 0.24), (wseat * 0.55, 0.05, 0.07), M_SEAT, bevel=0.025)   # 머리받침
    yd = COWL - 0.18
    box((0, yd, interp(BELT, yd) - 0.02), (_W * 0.43, 0.20, 0.07), M_DASH, rot=(math.radians(-8), 0, 0), bevel=0.02)
    bpy.ops.mesh.primitive_torus_add(major_radius=0.17, minor_radius=0.017, location=(-_W * 0.21, yd - 0.30, interp(BELT, yd) + 0.04), rotation=(math.radians(62), 0, 0), major_segments=48, minor_segments=10)
    sw = bpy.context.object; sw.data.materials.append(M_DASH); bpy.ops.object.shade_smooth()
    # 아래 흡기구: 범퍼 아래 검정 무광 판 + 가로 살 4줄(유광 검정, 판보다 4 mm 앞). 번호판이 위 끝에 걸친다
    if ST.get("grille", True):
        zfl = low_face(Y_FRONT, 1)
        EI = face_ext(Y_FRONT, zfl + 0.08, 1, 0.6) * 0.78
        conform(1, Y_FRONT, span(-EI, EI, 48), rows(zfl + 0.015, zfl + 0.16, 3), 0.002, M_TRIM, thick=0.003, name="intake")
        LAMPSET[0] = "gr:base"   # 기본 그릴 살 (튜닝 그릴을 고르면 숨긴다)
        for k in range(4):
            zk = zfl + 0.04 + k * 0.03
            conform(1, Y_FRONT, span(-EI + 0.02, EI - 0.02, 48), rows(zk - 0.004, zk + 0.004, 1), 0.006, M_PIANO, thick=0.004, name="intake_slat")
        LAMPSET[0] = "fb:base"   # 기본 아래 립
        conform(1, Y_FRONT, span(-EI * 1.04, EI * 1.04, 48), rows(zfl - 0.004, zfl + 0.012, 1), 0.006, M_TRIM, thick=0.01, name="lip")
        LAMPSET[0] = None   # 아래 립(에어댐)
    conform(1, Y_FRONT, span(-0.065, 0.065, 12), rows(ZP[1] + 0.095, ZP[1] + 0.140, 2), 0.003, M_PIANO, thick=0.003, name="radar")
    # 주차 센서: 앞·뒤 범퍼 분할선 아래 4개씩, 차체색 작은 원판
    for ye, sgn in ((Y_FRONT, 1), (Y_REAR, -1)):
        zs_ = interp(FLOOR, ye) + 0.30
        for fx in (-0.34, -0.17, 0.17, 0.34):
            pod(fx * _W, ye, zs_, sgn, 0.011, 0.004, M_PAINT, 0.002)
    # 카울(후드와 앞유리 사이 검정 판)과 와이퍼 2개 (국내 좌핸들: 운전석 쪽이 길다). 표정 아래 가장자리 밖이라 가리지 않는다
    def top_hit(x, y):
        ok, loc, nrm, idx = body.ray_cast(Vector((x, y, 5.0)), Vector((0, 0, -1)))
        return (loc, nrm) if ok else (None, None)
    def top_sheet(xs, ys, off, m, thick, name):
        grid = []
        for y in ys:
            row = []
            for x in xs:
                loc, nrm = top_hit(x, y)
                if loc is None: return None
                row.append(tuple(loc + nrm * off))
            grid.append(row)
        nx = len(xs); vs = [p for r_ in grid for p in r_]
        fs_ = [(j * nx + i, j * nx + i + 1, (j + 1) * nx + i + 1, (j + 1) * nx + i) for j in range(len(ys) - 1) for i in range(nx - 1)]
        me = bpy.data.meshes.new(name); me.from_pydata(vs, [], fs_); me.update()
        o = bpy.data.objects.new(name, me); scene.collection.objects.link(o); o.data.materials.append(m)
        for p in me.polygons: p.use_smooth = True
        if thick: so = o.modifiers.new("s", "SOLIDIFY"); so.thickness = thick; so.offset = 0
        return o
    wc_ = roof_half(COWL - 0.05) * 0.98
    top_sheet(span(-wc_, wc_, 48), [COWL + 0.075, COWL + 0.04, COWL + 0.008], 0.002, M_TRIM, 0.003, "cowl")
    for x0, x1 in ((-wc_ * 0.92, -0.05), (0.06, wc_ * 0.80)):
        top_sheet(span(x0, x1, 24), [COWL + 0.034, COWL + 0.024], 0.014, M_PIANO, 0.008, "wiper")
        loc_, n_ = top_hit(x1 - 0.01, COWL + 0.05)
        if loc_ is not None:
            bpy.ops.mesh.primitive_cylinder_add(radius=0.016, depth=0.016, location=tuple(loc_ + n_ * 0.008), vertices=20)
            bpy.context.object.data.materials.append(M_PIANO)   # 와이퍼 축 캡
    # ── 외관 튜닝 부품 (페이지가 고른 것만 보여준다. 꼬리표 "칸:항목", base는 지금까지의 기본 모양) ──
    # 모든 크기는 이 차의 치수(전폭·바닥·휠·아치·오버행)에서 정한다 → 7종 모두 같은 규칙으로 맞는다
    def side_hit(sgn, y, z):
        ok, loc, nrm, idx = body.ray_cast(Vector((sgn * 3.0, y, z)), Vector((-sgn, 0, 0)))
        return (loc, nrm) if ok else (None, None)
    def grid_obj(grid, flip, m, thick, name):
        nz = len(grid[0]); vs = [p for r_ in grid for p in r_]
        fs_ = [(j * nz + i, (j + 1) * nz + i, (j + 1) * nz + i + 1, j * nz + i + 1) for j in range(len(grid) - 1) for i in range(nz - 1)]
        if flip: fs_ = [tuple(reversed(f)) for f in fs_]
        me = bpy.data.meshes.new(name); me.from_pydata(vs, [], fs_); me.update()
        o = bpy.data.objects.new(name, me); scene.collection.objects.link(o); o.data.materials.append(m)
        for p in me.polygons: p.use_smooth = True
        if thick: so = o.modifiers.new("s", "SOLIDIFY"); so.thickness = thick; so.offset = 0
        return tag(o)
    def t_side_sheet(sgn, ys, zs_fn, off, m, thick, name):
        # 옆면을 따라가는 띠: ys 앞→뒤, zs_fn(y) = 그 자리의 높이 목록(아래→위)
        grid = []
        for y in ys:
            row = []
            for z in zs_fn(y):
                loc, nrm = side_hit(sgn, y, z)
                if loc is None: print("SIDE MISS", name, LAMPSET[0], flush=True); return None
                row.append(tuple(loc + nrm * off))
            grid.append(row)
        return grid_obj(grid, sgn < 0, m, thick, name)
    def ttop(xs, ys, off, m, thick, name):
        o = top_sheet(xs, ys, off, m, thick, name)
        if o is None: print("TOP MISS", name, LAMPSET[0], flush=True); return None
        return tag(o)
    def tbox(loc, scale, m, rot=(0, 0, 0)):
        bpy.ops.mesh.primitive_cube_add(location=loc); o = bpy.context.object; o.scale = scale; o.rotation_euler = rot
        o.data.materials.append(m); return tag(o)
    YFW, YRW = WHEEL_YF, -WHEEL_YR                       # 앞·뒤 바퀴 중심 (길이 방향)
    ARR = WHEEL_R + ST.get("arch", 0.09) - 0.015         # 휠 아치 반지름
    ZAC = WHEEL_R + 0.015                                # 휠 아치 중심 높이
    # ── 그릴 (아래 흡기구 판 위에 살만 바꾼다) ──
    if ST.get("grille", True):
        z0g, z1g = zfl + 0.03, zfl + 0.145
        LAMPSET[0] = "gr:mesh"          # 그물망: 가는 가로살 5 + 세로살
        for k in range(5):
            zk = z0g + (z1g - z0g) * k / 4
            conform(1, Y_FRONT, span(-EI + 0.02, EI - 0.02, 48), rows(zk - 0.0025, zk + 0.0025, 1), 0.006, M_PIANO, thick=0.004, name="g_mesh")
        nv = max(8, int(EI / 0.045))
        for k in range(nv + 1):
            xk = -EI + 0.02 + (2 * EI - 0.04) * k / nv
            conform(1, Y_FRONT, span(xk - 0.0025, xk + 0.0025, 2), rows(z0g, z1g, 2), 0.006, M_PIANO, thick=0.004, name="g_mesh")
        LAMPSET[0] = "gr:horiz"         # 가로줄: 굵은 크롬 살 3줄
        for k in range(3):
            zk = z0g + 0.012 + (z1g - z0g - 0.024) * k / 2
            conform(1, Y_FRONT, span(-EI + 0.02, EI - 0.02, 48), rows(zk - 0.006, zk + 0.006, 1), 0.007, M_CHROME, thick=0.006, name="g_horiz")
        LAMPSET[0] = "gr:vert"          # 가는 세로줄
        nv = max(14, int(EI / 0.028))
        for k in range(nv + 1):
            xk = -EI + 0.025 + (2 * EI - 0.05) * k / nv
            conform(1, Y_FRONT, span(xk - 0.004, xk + 0.004, 2), rows(z0g, z1g, 2), 0.006, M_PIANO, thick=0.005, name="g_vert")
        LAMPSET[0] = "gr:panel"         # 매끈 패널: 흡기구를 차체색 판으로 덮고 아래 가는 틈만
        conform(1, Y_FRONT, span(-EI - 0.004, EI + 0.004, 48), rows(zfl + 0.012, zfl + 0.163, 3), 0.007, M_PAINT, thick=0.006, name="g_panel")
        conform(1, Y_FRONT, span(-EI * 0.7, EI * 0.7, 32), rows(zfl + 0.03, zfl + 0.04, 1), 0.012, M_PIANO, thick=0.003, name="g_panel_slot")
        # ── 앞범퍼 ──
        LAMPSET[0] = "fb:lip"           # 앞 립: 기본보다 넓고 앞으로 2 cm 나온 검정 립
        conform(1, Y_FRONT, span(-EI * 1.18, EI * 1.18, 48), rows(zfl - 0.008, zfl + 0.016, 1), 0.02, M_PIANO, thick=0.014, name="fb_lip")
        LAMPSET[0] = "fb:splitter"      # 짧은 스플리터: 바닥과 평행한 판 (앞 오버행의 1/10 이하로만 나온다)
        loc_, n_ = hit(0, Y_FRONT, zfl, 1); yfront = loc_.y if loc_ is not None else Y_FRONT
        sp_out = min(0.06, (Y_FRONT - YFW - WHEEL_R) * 0.1)
        tbox((0, yfront - 0.05 + sp_out, zfl - 0.012), (EI * 1.12, 0.05 + sp_out, 0.005), M_PIANO)
        conform(1, Y_FRONT, span(-EI * 1.1, EI * 1.1, 48), rows(zfl - 0.006, zfl + 0.01, 1), 0.006, M_TRIM, thick=0.01, name="fb_splitter_lip")
        LAMPSET[0] = "fb:outdoor"       # 아웃도어 범퍼: 수지 하부 보호판(은회색) + 검정 아래 범퍼
        conform(1, Y_FRONT, span(-EI * 1.08, EI * 1.08, 48), rows(zfl - 0.01, zfl + 0.03, 1), 0.012, M_TRIM, thick=0.012, name="fb_out_low")
        tbox((0, yfront - 0.12, zfl - 0.03), (EI * 0.62, 0.14, 0.006), M_SKID, (math.radians(-12), 0, 0))
        for s_ in (-1, 1):
            conform(1, Y_FRONT, span(s_ * EI * 0.86, s_ * EI * 1.0, 6), rows(zfl + 0.02, zfl + 0.06, 1), 0.014, M_SKID, thick=0.01, name="fb_out_corner")
    # ── 뒷범퍼 (기본 디퓨저는 위에서 rb:base·rb:towhook으로 그렸다) ──
    LAMPSET[0] = "rb:diffuser"          # 디퓨저: 더 높은 판 + 뒤로 4 cm 나온 세로 핀 3장
    conform(-1, Y_REAR, span(-EDf * 1.05, EDf * 1.05, 48), rows(zlo + 0.005, zlo + 0.11, 3), 0.002, M_PIANO, thick=0.004, name="rb_diff")
    for fx in (-0.4, 0.0, 0.4):
        conform(-1, Y_REAR, span(EDf * fx - 0.005, EDf * fx + 0.005, 2), rows(zlo + 0.008, zlo + 0.1, 2), 0.022, M_PIANO, thick=0.04, name="rb_fin")
    LAMPSET[0] = "rb:skid"              # 하부 보호판: 범퍼 아래 은회색 수지 판
    conform(-1, Y_REAR, span(-EDf * 0.95, EDf * 0.95, 48), rows(zlo - 0.005, zlo + 0.085, 2), 0.008, M_SKID, thick=0.01, name="rb_skid")
    LAMPSET[0] = "rb:towhook"           # 견인 고리: 기본 디퓨저 + 한쪽 작은 고리
    loc_, n_ = hit(EDf * 0.6, Y_REAR, zlo + 0.05, -1)
    if loc_ is not None:
        bpy.ops.mesh.primitive_torus_add(major_radius=0.03, minor_radius=0.008, location=(EDf * 0.6, loc_.y - 0.03, zlo + 0.04), rotation=(0, math.pi / 2, 0), major_segments=24, minor_segments=8)
        o = bpy.context.object; o.data.materials.append(M_CHROME); bpy.ops.object.shade_smooth(); tag(o)
    # ── 옆 하단 (앞바퀴 뒤 ~ 뒷바퀴 앞, 양쪽) ──
    ys_side = [YFW - ARR - 0.03 + (YRW + ARR + 0.03 - (YFW - ARR - 0.03)) * k / 24 for k in range(25)]
    for s_ in (-1, 1):
        LAMPSET[0] = "sd:skirt"         # 사이드 스커트: 문 아래 검정 띠, 바닥보다 바깥
        t_side_sheet(s_, ys_side, lambda y: [interp(FLOOR, y) + 0.01 + 0.02 * i for i in range(4)], 0.014, M_PIANO, 0.012, "sd_skirt")
        LAMPSET[0] = "sd:molding"       # 검은 몰딩 띠: 문 아래쪽 가는 띠
        t_side_sheet(s_, ys_side, lambda y: [interp(FLOOR, y) + 0.13, interp(FLOOR, y) + 0.16], 0.004, M_TRIM, 0.004, "sd_mold")
        LAMPSET[0] = "sd:step"          # 사이드 스텝(SUV): 문 아래 바깥으로 나온 발판 + 받침 2개
        loc_, n_ = side_hit(s_, (YFW + YRW) / 2, interp(FLOOR, 0) + 0.05)
        if loc_ is not None:
            xs_ = loc_.x + s_ * 0.075; zs_ = interp(FLOOR, 0) - 0.015; yl = (ys_side[0] - ys_side[-1]) / 2; yc_ = (ys_side[0] + ys_side[-1]) / 2
            tbox((xs_, yc_, zs_), (0.07, yl, 0.014), M_PIANO)
            tbox((xs_, yc_, zs_ + 0.016), (0.065, yl * 0.98, 0.003), M_SKID)
            for yb in (yc_ + yl * 0.6, yc_ - yl * 0.6):
                tbox((loc_.x + s_ * 0.03, yb, zs_ + 0.01), (0.035, 0.02, 0.02), M_TRIM)
    # ── 보닛 (위에서 내려 쏴 후드 곡면에 붙인다) ──
    yh0, yh1 = COWL + 0.15, Y_FRONT - 0.22            # 후드 앞뒤 범위 (앞유리 아래 ~ 코끝 뒤)
    ym = (yh0 + yh1) / 2
    hw = interp(WBELT, ym) - 0.16                     # 후드 반폭 (어깨선 안쪽)
    LAMPSET[0] = "hd:vent"              # 바람구멍 두 개: 좌우 검정 판 + 가는 살 3줄
    for s_ in (-1, 1):
        xa, xb = sorted((s_ * hw * 0.38, s_ * hw * 0.72))
        ttop(span(xa, xb, 8), [ym + 0.10, ym - 0.10], 0.003, M_PIANO, 0.003, "hd_vent")
        for k in range(3):
            yk = ym + 0.07 - 0.07 * k
            ttop(span(xa + 0.01, xb - 0.01, 8), [yk + 0.006, yk - 0.006], 0.007, M_TRIM, 0.004, "hd_vent_slat")
    LAMPSET[0] = "hd:scoop"             # 공기 흡입구: 가운데 낮은 차체색 혹 + 앞쪽 검정 입
    loc_, n_ = top_hit(0, ym)
    if loc_ is not None:
        sl = math.atan2(interp(TOP, ym - 0.2) - interp(TOP, ym + 0.2), 0.4)
        tbox((0, ym, loc_.z + 0.022), (hw * 0.24, 0.22, 0.024), M_PAINT, (sl, 0, 0))
        tbox((0, ym + 0.215, loc_.z + 0.026 - 0.22 * math.sin(sl)), (hw * 0.2, 0.006, 0.016), M_PIANO, (sl, 0, 0))
    LAMPSET[0] = "hd:carbon"            # 카본 무늬: 후드 위를 덮는 짙은 카본 판
    ttop(span(-hw, hw, 32), [yh0 + (yh1 - yh0) * k / 8 for k in range(9)], 0.004, M_CARBON, 0.002, "hd_carbon")
    # ── 펜더 (휠 아치) ──
    def arch_sheet(s_, yc, r0, r1, off, m, thick, name, n=28):
        grid = []
        for k in range(n + 1):
            a = math.radians(8 + 164 * k / n); row = []
            for r in (r0, r1):
                loc, nrm = side_hit(s_, yc + math.cos(a) * r, ZAC + math.sin(a) * r)
                if loc is None: print("ARCH MISS", name, flush=True); return None
                row.append(tuple(loc + nrm * off))
            grid.append(row)
        return grid_obj(grid, s_ > 0, m, thick, name)
    for s_ in (-1, 1):
        for yc in (YFW, YRW):
            LAMPSET[0] = "fd:molding"   # 휠 아치 몰딩: 아치를 따라 검정 띠
            arch_sheet(s_, yc, ARR + 0.006, ARR + 0.05, 0.006, M_TRIM, 0.008, "fd_mold")
            LAMPSET[0] = "fd:over"      # 매끈 오버펜더: 차체색 띠를 띄움 (리벳 없음, 차 폭은 넘지 않게 얇게)
            arch_sheet(s_, yc, ARR + 0.004, ARR + 0.085, 0.016, M_PAINT, 0.02, "fd_over")
            LAMPSET[0] = "fd:mudguard"  # 머드가드: 바퀴 뒤 아래쪽 검정 흙받이
            yg = yc - ARR * 0.92
            loc_, n_ = side_hit(s_, yg, WHEEL_R * 0.55)
            xg = (loc_.x - s_ * TIRE_W * 0.45) if loc_ is not None else s_ * WHEEL_X
            tbox((xg, yg, WHEEL_R * 0.5), (TIRE_W * 0.45, 0.004, WHEEL_R * 0.42), M_PIANO)
    LAMPSET[0] = None

# ---------- 바닥(그림자 받이), 조명, 카메라 ----------
bpy.ops.mesh.primitive_plane_add(size=30, location=(0, 0, 0)); ground = bpy.context.object
ground.is_shadow_catcher = True
ground.data.materials.append(mat("ground", (0.5, 0.5, 0.5), rough=0.6))

world = bpy.data.worlds.new("w"); scene.world = world; world.use_nodes = True
wn = world.node_tree.nodes; wl = world.node_tree.links
env = wn.new("ShaderNodeTexEnvironment")
hdri = os.path.join(os.path.dirname(bpy.app.binary_path), "5.2", "datafiles", "studiolights", "world", arg("--hdri", "studio") + ".exr")
if os.path.exists(hdri): env.image = bpy.data.images.load(hdri)
mapping = wn.new("ShaderNodeMapping"); tc = wn.new("ShaderNodeTexCoord")
mapping.inputs["Rotation"].default_value = (0, 0, math.radians(float(arg("--envrot", "35"))))
wl.new(tc.outputs["Generated"], mapping.inputs["Vector"]); wl.new(mapping.outputs[0], env.inputs["Vector"])
bg = wn["Background"]; bg.inputs["Strength"].default_value = float(arg("--envstr", "1.0"))
wl.new(env.outputs["Color"], bg.inputs["Color"])

def area(name, loc, target, size, energy, color=(1, 1, 1)):
    ld = bpy.data.lights.new(name, "AREA"); ld.energy = energy; ld.size = size; ld.color = color
    lo = bpy.data.objects.new(name, ld); scene.collection.objects.link(lo); lo.location = loc
    d = Vector(target) - Vector(loc); lo.rotation_euler = d.to_track_quat("-Z", "Y").to_euler()
    return lo
STUDIO = ST.get("detail") and "--oldlight" not in argv
if STUDIO:
    # 자동차 촬영 스튜디오: 창문 모양이 비치는 HDRI 대신 어두운 벽 + 큰 소프트박스 + 긴 띠 조명.
    # 도장에 깨끗한 하이라이트 띠가 지나가고 앞유리에 얼룩이 생기지 않는다
    wl.new(tc.outputs["Generated"], mapping.inputs["Vector"])
    for lk in list(wl):
        if lk.to_node == bg: wl.remove(lk)
    grad = wn.new("ShaderNodeValToRGB"); sep = wn.new("ShaderNodeSeparateXYZ")
    wl.new(tc.outputs["Generated"], sep.inputs[0]); wl.new(sep.outputs["Z"], grad.inputs["Fac"])
    grad.color_ramp.elements[0].color = (0.010, 0.010, 0.011, 1); grad.color_ramp.elements[0].position = 0.45
    grad.color_ramp.elements[1].color = (0.05, 0.052, 0.055, 1); grad.color_ramp.elements[1].position = 0.62
    wl.new(grad.outputs["Color"], bg.inputs["Color"]); bg.inputs["Strength"].default_value = 1.0
    def rect(name, loc, target, sx, sy, energy, color=(1, 1, 1)):
        lo = area(name, loc, target, sx, energy, color); lo.data.shape = "RECTANGLE"; lo.data.size_y = sy; return lo
    _top = rect("top", (0, 0.2, 4.2), (0, 0.2, 0.0), 1.6, 4.2, 650, (1.0, 0.985, 0.965))   # 지붕 위 큰 소프트박스
    _top.visible_glossy = False   # 비추기만 하고 반사에는 안 보이게: 앞유리 표정을 흰 사각형이 덮지 않는다
    rect("stripR", (3.6, 0.0, 1.9), (0, 0.0, 0.7), 0.22, 5.0, 260, (0.96, 0.98, 1.0))        # 오른쪽 긴 띠 (옆면 하이라이트 선)
    _sl = rect("stripL", (-3.6, 0.0, 1.9), (0, 0.0, 0.7), 0.22, 5.0, 150, (0.96, 0.98, 1.0))  # 왼쪽 긴 띠
    _sl.visible_glossy = False   # 이 띠는 히어로 각도에서 앞유리에 비쳐 표정을 덮는다. 비추기만 한다
    rect("front", (2.2, 6.0, 1.6), (0, 1.0, 0.6), 2.0, 1.0, 260, (1.0, 0.97, 0.93))          # 앞 사선 보조
    rect("rim", (-2.5, -5.5, 2.2), (0, -1.0, 0.8), 2.0, 0.6, 260, (0.90, 0.95, 1.0))        # 뒤 윤곽광
    for _nm in [n_ for n_ in arg("--noglossy", "").split(",") if n_]:
        if _nm in scene.objects: scene.objects[_nm].visible_glossy = False
else:
    area("key", (3.5, 4.5, 5.5), (0, 0.3, 0.8), 4.0, 900, (1.0, 0.97, 0.93))
    area("rim", (-4.5, -3.0, 3.0), (0, 0, 0.9), 3.0, 350, (0.90, 0.95, 1.0))
    area("top", (0, 0.5, 6.0), (0, 0.5, 1.0), 6.0, 300)
if NIGHT:
    # 밤: 환경·조명을 거의 끄고 전조등 빛이 바닥에 떨어지게 스포트 조명 두 개
    bg.inputs["Strength"].default_value *= 0.05
    for o in scene.objects:
        if o.type == "LIGHT": o.data.energy *= 0.06
    for s in (-1, 1):
        ld = bpy.data.lights.new("beam", "SPOT"); ld.energy = 260; ld.spot_size = math.radians(55); ld.spot_blend = 0.6; ld.color = (0.95, 0.97, 1.0)
        lo = bpy.data.objects.new("beam", ld); scene.collection.objects.link(lo); lo.location = (s * _W * 0.33, Y_FRONT + 0.02, catmull(TOP, Y_FRONT) - 0.13)
        lo.rotation_euler = (Vector((s * _W * 0.4, Y_FRONT + 4.0, 0.0)) - lo.location).to_track_quat("-Z", "Y").to_euler()

cam_d = bpy.data.cameras.new("cam"); cam_d.lens = 70
cam = bpy.data.objects.new("cam", cam_d); scene.collection.objects.link(cam); scene.camera = cam
K = _L / 4.52
if VIEW == "detail": cam.location = (2.9 * K, 4.6 * K, 0.95)   # 앞바퀴·헤드램프 근접
elif VIEW == "side": cam.location = (11.0 * K, 0.3, 1.3)
elif VIEW == "front": cam.location = (3.0 * K, 10.5 * K, 1.6)
elif VIEW == "rear": cam.location = (6.5 * K, -8.5 * K, 2.2 * K)
else: cam.location = (7.6 * K, 8.0 * K, 2.6 * K)
look = Vector((_W * 0.32, HL * 0.62, 0.52)) if VIEW == "detail" else Vector((0, 0.1, 0.72)); cam.rotation_euler = (look - cam.location).to_track_quat("-Z", "Y").to_euler()

if NIGHT:
    # 밤: 램프 둘레 빛 번짐(블룸). Blender 버전에 따라 합성 노드 위치가 달라 실패해도 렌더는 계속한다
    try:
        if hasattr(scene, "compositing_node_group"):
            ng = bpy.data.node_groups.new("comp", "CompositorNodeTree"); scene.compositing_node_group = ng
            rl = ng.nodes.new("CompositorNodeRLayers"); go = ng.nodes.new("NodeGroupOutput"); ng.interface.new_socket("Image", in_out="OUTPUT", socket_type="NodeSocketColor")
        else:
            scene.use_nodes = True; ng = scene.node_tree; ng.nodes.clear()
            rl = ng.nodes.new("CompositorNodeRLayers"); go = ng.nodes.new("CompositorNodeComposite")
        gl = ng.nodes.new("CompositorNodeGlare")
        try: gl.glare_type = "BLOOM"
        except Exception: gl.glare_type = "FOG_GLOW"
        for k, v in (("threshold", 1.2), ("size", 7), ("mix", 0.0)):
            if hasattr(gl, k): setattr(gl, k, v)
        for nm, v in (("Threshold", 1.2), ("Size", 0.6), ("Strength", 0.8)):
            if nm in gl.inputs: gl.inputs[nm].default_value = v
        ng.links.new(rl.outputs["Image"], gl.inputs[0]); ng.links.new(gl.outputs[0], go.inputs[0])
        print("BLOOM ok")
    except Exception as e:
        # 합성이 반쯤 만들어진 채로 남으면 그림이 검게 나온다. 되돌린다
        if hasattr(scene, "compositing_node_group"): scene.compositing_node_group = None
        else: scene.use_nodes = False
        print("BLOOM skipped", e)

if GLB:
    bpy.ops.object.select_all(action="SELECT"); ground.select_set(False); cam.select_set(False)
    bpy.ops.export_scene.gltf(filepath=GLB, use_selection=True, export_apply=True)

MESH = arg("--mesh", "")
if MESH:
    from export_mesh import export_mesh
    _roof_mid = (WSHIELD_END - 0.15 + (-HL * 0.55)) / 2
    export_mesh(MESH, BODY, dict(
        skip={ground.name, cam.name} | {o.name for o in scene.objects if o.name.startswith("plate")},
        wheel_of=WHEEL_OBJS,
        skip_mats={"seat", "dash"},
        wheel_center={0: Vector((WHEEL_X, WHEEL_Y, WHEEL_R)), 2: Vector((-WHEEL_X, WHEEL_Y, WHEEL_R))},
        roof_z=interp(TOP, 0) - 0.12, cowl=COWL,
        dims=dict(L=_L, W=_W, H=_H, wheelR=WHEEL_R, wheelX=WHEEL_X, wheelY=WHEEL_Y, wheelYF=WHEEL_YF, wheelYR=WHEEL_YR, tireW=TIRE_W,
                  floor=interp(FLOOR, 0), belt=interp(BELT, 0), top=interp(TOP, 0),
                  roof=dict(y0=WSHIELD_END - 0.15, y1=-HL * 0.55, z=interp(TOP, _roof_mid)),
                  cowl=COWL, wsEnd=WSHIELD_END, wsZ=WS_Z,
                  sideX=interp(WBELT, 0), sideZ=(interp(FLOOR, 0) + interp(BELT, 0)) / 2 + 0.04, sideIn=_side_inset(0, (interp(FLOOR, 0) + interp(BELT, 0)) / 2 + 0.08),
                  front=dict(y=Y_FRONT, zLamp=(LAMP_Z[0] if DETAIL else interp(TOP, Y_FRONT) - 0.10), zPlate=(ZP[1] if DETAIL else interp(FLOOR, Y_FRONT) + 0.22), yPlate=(YP[1] if DETAIL else Y_FRONT)),
                  rear=dict(y=Y_REAR, zLamp=(LAMP_Z[1] if DETAIL else interp(BELT, Y_REAR) + 0.10), zPlate=(ZP[-1] if DETAIL else interp(FLOOR, Y_REAR) + 0.22), yPlate=(YP[-1] if DETAIL else Y_REAR), zTop=interp(TOP, Y_REAR), zExh=(EXH[0] if DETAIL else None), yExh=(EXH[1] if DETAIL else None)),
                  spoiler=P["spoiler"], doorA=DOOR_A, doorM=DOOR_M, doorB=DOOR_B,
                  # 페이지에서 줄무늬·스티커를 몸 곡면에 붙이려고 쓰는 옆폭·지붕 곡선 [[y, 값]] (앞→뒤)
                  curves=dict(wbelt=[[round(y, 3), round(interp(WBELT, y), 3)] for y in [HL - i * _L / 24 for i in range(25)]],
                              top=[[round(y, 3), round(interp(TOP, y), 3)] for y in [HL - i * _L / 24 for i in range(25)]])),
    ))

scene.render.filepath = OUT
if "--norender" in argv: sys.exit(0)
bpy.ops.render.render(write_still=True)
print("RENDERED", OUT)
