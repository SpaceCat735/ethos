#!/bin/bash
# 차종 7종 메시를 Blender로 내보내 앱 assets/garage/meshes/ 에 넣는다.
# 사용: bash docs/character/car3d/export_all.sh [차종...]   (BLENDER 환경변수로 blender.exe 경로 지정)
B="${BLENDER:-blender}"
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/../../../app/src/main/assets/garage/meshes"
mkdir -p "$OUT"
for b in ${@:-mini sedan_s sedan_m sedan_l suv_s suv_m suv_l}; do
  "$B" -b --python "$HERE/car.py" -- --body $b --norender --mesh "$(cd "$OUT" && pwd -W 2>/dev/null || pwd)/$b.js" 2>&1 | grep -E "^MESH|Traceback|Error|line [0-9]+" | cut -c1-160
done
ls -la "$OUT"
