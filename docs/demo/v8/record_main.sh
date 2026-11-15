#!/bin/bash
# 데모 영상 v8 본 녹화: demo 시나리오 2배속, 두 파일(A: 0~174초, B: 176~262초)
# 실행: bash record_main.sh <저장 폴더>. 에뮬레이터를 켜고 release APK를 설치해 둔 상태에서 (탭 좌표는 2560x1440 가로 화면 기준)
export MSYS_NO_PATHCONV=1
ADB=${ADB:-adb}
OUT="$1"
tap() { "$ADB" shell input tap "$1" "$2" </dev/null; }
now() { date +%s%3N; }
at() { # 시나리오 시작 뒤 $1(밀리초)가 될 때까지 기다린다
  local d=$(( T0 + $1 - $(now) ))
  if (( d > 0 )); then sleep "$(printf "%d.%03d" $((d/1000)) $((d%1000)))"; fi
}

# 준비: 새 설치 상태로 만들고 첫 실행 차급 패널을 "나중에"로 닫는다
"$ADB" shell cmd uimode night no </dev/null
"$ADB" shell pm clear --user 10 com.naechamon </dev/null
"$ADB" shell am start --user 10 -n com.naechamon/.MainActivity </dev/null >/dev/null
sleep 5
tap 2207 785
sleep 2
"$ADB" shell rm -f /sdcard/v8_a.mp4 /sdcard/v8_b.mp4 </dev/null

"$ADB" shell am force-stop --user 10 com.naechamon </dev/null
sleep 1
# 녹화 A
"$ADB" shell screenrecord --bit-rate 16000000 --time-limit 174 /sdcard/v8_a.mp4 </dev/null &
sleep 1.5
"$ADB" shell am start --user 10 -n com.naechamon/.MainActivity --es scenario demo --ei speed 2 </dev/null >/dev/null
T0=$(now)
echo "T0 set"

at 100000; tap 1694 777          # 부화 카드 확인
at 106000; tap 1006 348          # 소모품
at 112000; "$ADB" shell input swipe 1900 1100 1900 400 500 </dev/null
at 119000; tap 1006 245          # 차량 상태
wait                          # 녹화 A 끝 (약 175.5초)
echo "A done at $(( $(now) - T0 ))ms"

# 녹화 B
"$ADB" shell screenrecord --bit-rate 16000000 --time-limit 90 /sdcard/v8_b.mp4 </dev/null &
echo "B start at $(( $(now) - T0 ))ms"
at 185000; tap 1006 553          # 월간 점검표
at 193000; tap 917 141           # 뒤로
at 195000; tap 1006 348          # 소모품
at 198000; tap 2383 283          # 엔진오일 정비 완료 기록
at 204500; tap 1006 451        # 정비 수첩
at 210000; tap 1010 655          # 내 차 설정 (3D 차고)
at 223000; tap 2000 137          # 색·재질
at 226000; tap 2112 417          # 틸 그린
at 231000; tap 2119 137          # 바퀴·조명
at 234000; tap 2253 306          # 반짝 별 (엔진오일 기록으로 열림)
at 240000; tap 2344 1217         # 저장하기
wait
echo "B done at $(( $(now) - T0 ))ms"
sleep 2
"$ADB" pull /sdcard/v8_a.mp4 "$OUT/raw_a.mp4" </dev/null | tail -1
"$ADB" pull /sdcard/v8_b.mp4 "$OUT/raw_b.mp4" </dev/null | tail -1
