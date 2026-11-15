# car.py가 끝에서 불러 쓰는 메시 내보내기: 차고 페이지(three.js)용 JS 파일.
# 좌표: Blender (x, y앞, z위) → three.js (-x, z, y) (회전이라 겉면 방향 유지, 차는 좌우 대칭이라 모양 동일)
import bpy, base64, struct, json, math

def octa(x, y, z):
    # 단위 법선 → 팔면체 2성분(int16). 반사가 매끈하도록 정밀하게 (페이지에서 되돌린다)
    s = abs(x) + abs(y) + abs(z) or 1.0
    u, v = x / s, y / s
    if z < 0: u, v = (1 - abs(v)) * (1 if u >= 0 else -1), (1 - abs(u)) * (1 if v >= 0 else -1)
    return int(round(max(-1, min(1, u)) * 32767)), int(round(max(-1, min(1, v)) * 32767))

def export_mesh(path, body_key, ctx):
    scene = bpy.context.scene
    deps = bpy.context.evaluated_depsgraph_get()
    # 부위 이름: 재질 이름 기준. body 오브젝트의 paint는 지붕(roof)을 따로 뗀다
    parts = {}  # (part, wheel_idx) -> dict(pos list, nrm list, uv list, idx list, map)
    def part(name, wheel=-1):
        k = (name, wheel)
        if k not in parts: parts[k] = dict(v=[], i=[], map={}, uv=name == "windshield")
        return parts[k]
    roof_z = ctx["roof_z"]; cowl = ctx["cowl"]
    for ob in scene.objects:
        if ob.type != "MESH" or ob.name in ctx["skip"]: continue
        ev = ob.evaluated_get(deps); me = ev.to_mesh()
        me.calc_loop_triangles()
        uvl = me.uv_layers.get("face")
        mw = ev.matrix_world; nm = mw.to_3x3().inverted().transposed()
        wheel = ctx["wheel_of"].get(ob.name, -1)
        if wheel in (1, 3): continue   # 바퀴는 왼쪽·오른쪽 한 개씩만 내보내고 페이지에서 앞뒤로 복제한다
        wc = ctx["wheel_center"].get(wheel)  # 바퀴 중심 기준 좌표로
        for tri in me.loop_triangles:
            mname = me.materials[tri.material_index].name if me.materials else "trim"
            if ob.name == "body" and mname == "glass":
                # 유리띠 중 위를 보는 면(지붕)은 따로 떼어 투톤 지붕 색을 입힐 수 있게 한다
                if tri.normal.z > 0.6: mname = "roof"
            if mname in ctx.get("skip_mats", ()): continue   # 실내 등 앱에서 안 보이는 부품은 빼서 파일을 가볍게
            if ob.get("lampset"): mname = mname + "@" + ob["lampset"]   # 램프 스타일 부품: 페이지가 고른 스타일만 보여준다
            P = part(mname, wheel)
            ids = []
            for li, vi in zip(tri.loops, tri.vertices):
                co = mw @ me.vertices[vi].co
                if wc: co = co - wc
                nr = (nm @ me.corner_normals[li].vector).normalized()
                uv = (uvl.data[li].uv.x, uvl.data[li].uv.y) if (P["uv"] and uvl) else (0.0, 0.0)
                key = (int(round(-co.x * 1000)), int(round(co.z * 1000)), int(round(co.y * 1000))) + octa(-nr.x, nr.z, nr.y) + (round(1 - uv[0], 3), round(uv[1], 3))
                j = P["map"].get(key)
                if j is None:
                    j = len(P["v"]); P["map"][key] = j; P["v"].append(key)
                ids.append(j)
            P["i"].extend(ids)
        ev.to_mesh_clear()
    out = {"dims": ctx["dims"], "parts": []}
    for (name, wheel), P in parts.items():
        if not P["i"]: continue
        pos = struct.pack("<%dh" % (len(P["v"]) * 3), *[c for k in P["v"] for c in k[0:3]])  # mm 단위 int16
        nrm = struct.pack("<%dh" % (len(P["v"]) * 2), *[c for k in P["v"] for c in k[3:5]])   # 팔면체 int16 ×2
        small = len(P["v"]) < 65536   # 정점이 적으면 인덱스를 16비트로 (파일 크기 약 1/3 감소)
        rec = {"m": name, "w": wheel, "n": len(P["v"]),
               "p": base64.b64encode(pos).decode(), "no": base64.b64encode(nrm).decode(),
               "i": base64.b64encode(struct.pack("<%d%s" % (len(P["i"]), "H" if small else "I"), *P["i"])).decode()}
        if small: rec["i16"] = 1
        if P["uv"]:
            rec["uv"] = base64.b64encode(struct.pack("<%df" % (len(P["v"]) * 2), *[c for k in P["v"] for c in k[5:7]])).decode()
        out["parts"].append(rec)
    with open(path, "w", encoding="utf-8") as f:
        f.write("// 자동 생성 (car3d/car.py --mesh). 차종 %s 의 3D 메시\n" % body_key)
        f.write("window.CAR_MESHES = window.CAR_MESHES || {};\nwindow.CAR_MESHES[%s] = " % json.dumps(body_key))
        json.dump(out, f, separators=(",", ":"))
        f.write(";\n")
    print("MESH", path, [(k[0], k[1], len(P["v"]), len(P["i"]) // 3) for k, P in parts.items()])
