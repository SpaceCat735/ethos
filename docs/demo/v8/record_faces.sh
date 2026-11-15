#!/bin/bash
# 추가 녹화: 주행 중 표정 변화 (급정거 → 시무룩, 정체 → 답답)
# 실행: bash record_faces.sh <저장 폴더>
export MSYS_NO_PATHCONV=1
ADB=${ADB:-adb}
OUT="$1"
tap() { "$ADB" shell input tap "$1" "$2" </dev/null; }

"$ADB" wait-for-device shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done' </dev/null
sleep 25
"$ADB" shell cmd uimode night no </dev/null
# 기본(흰색) 차로 되돌리고 첫 실행 패널을 닫아 둔다
"$ADB" shell pm clear --user 10 com.naechamon </dev/null
"$ADB" shell am start --user 10 -n com.naechamon/.MainActivity </dev/null >/dev/null
sleep 6
tap 2207 785
sleep 2
"$ADB" shell rm -f /sdcard/v8_c.mp4 /sdcard/v8_d.mp4 </dev/null

# C: commute 2배속. 급정거는 시나리오 240초 = 실제 120초
"$ADB" shell am force-stop --user 10 com.naechamon </dev/null
sleep 1
"$ADB" shell am start --user 10 -n com.naechamon/.MainActivity --es scenario commute --ei speed 2 </dev/null >/dev/null
sleep 110
"$ADB" shell screenrecord --bit-rate 16000000 --time-limit 26 /sdcard/v8_c.mp4 </dev/null
echo "C done"

# D: traffic_jam 4배속. 정체 3분이면 답답 (시나리오 약 220초 = 실제 55초)
"$ADB" shell am force-stop --user 10 com.naechamon </dev/null
sleep 1
"$ADB" shell am start --user 10 -n com.naechamon/.MainActivity --es scenario traffic_jam --ei speed 4 </dev/null >/dev/null
sleep 44
"$ADB" shell screenrecord --bit-rate 16000000 --time-limit 30 /sdcard/v8_d.mp4 </dev/null
echo "D done"
sleep 1
"$ADB" pull /sdcard/v8_c.mp4 "$OUT/raw_c.mp4" </dev/null | tail -1
"$ADB" pull /sdcard/v8_d.mp4 "$OUT/raw_d.mp4" </dev/null | tail -1
