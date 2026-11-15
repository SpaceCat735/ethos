# 에토스 (Ethos)

## 1. 앱 이름 · 한 줄 소개

**에토스(Ethos)** — 내 운전 습관이 내 차의 캐릭터가 된다.
이름은 그리스어 ἦθος(습관이 쌓여 생긴 성품)에서 왔다. 슬로건: "습관이 나아지면, 차의 성품이 된다."
주행 데이터로 운전 성향(차BTI)과 차량 상태를 읽어 캐릭터의 표정·컨디션으로 보여 주고, 내 운전 조건(가혹 조건)에 맞는 정비 시기를 알려 주는 Pleos Connect용 차량 관리 앱.

시연 영상: https://youtu.be/GxZcNLqElSE

## 2. 실행 방법

1. Android Studio에서 이 저장소를 연다.
2. Pleos Connect Emulator(v2.1.0, API 34, Cold boot, RAM 4GB)를 만들고 켠다. (설치 방법: Pleos Playground 개발 환경 설정 문서)
3. `app` 모듈을 Run 하거나, `apk/` 폴더의 APK를 설치한다: `adb install apk/SpaceCat_ethos_release.apk`
4. 데모 시나리오 재생 (실차 데이터 없이 주행 상황을 재생한다):
   ```
   adb shell am start -n com.naechamon/.MainActivity --ei speed 5
   ```
   - 주차 → 출근길(급정거 3회) → 주차 후 리포트 → 공기압 경고(긴급 화면) → 정차 후 리포트, 약 96초. 끝나면 정차 화면에 머문다.
   - 캐릭터는 13일치 주행 기록을 가진 "알"로 시작한다. 첫 주행을 마치고 주차하면 2주가 채워져 운전 성향(차BTI)대로 부화하고, 정차 중 한 번 "알에서 깨어났어요" 카드가 뜬다.
   - 처음 실행하면 정차 중에 한 번 "내 차와 비슷한 차급"(세단·SUV·소형·나중에)을 묻는 창이 뜬다. 하나를 고르거나 "나중에"를 누르면 데모가 이어진다. 고르지 않으면 승용 중형(흰색)이 기본 캐릭터다.
   - 정차 화면은 상태 타일(엔진오일·타이어·제동·오늘 주행)과 정비 기준 줄이 보이고, 타일을 누르면 상세 카드가 열린다.
   - 왼쪽 메뉴로 이어서 볼 수 있다 (자동으로 열리지 않는다):
     - "차량 상태" → 정차 화면 리포트
     - "소모품" → 소모품 카드 ("정비 완료 기록"으로 꾸미기 보상이 열리고 정비 수첩에 도장이 찍힌다. 교체 시기가 된 소모품만, 같은 날 한 번)
     - "정비 수첩" → 날짜 찍힌 도장과 관리 등급 (데모에서는 엔진오일·공조 필터·와이퍼를 기록하면 "단골 관리")
     - "월간 점검표" → 월간 건강검진 결과지
     - "내 차 설정" → 3D 차고 (정차 중에만. 내 차·색·재질·바퀴·조명·액세서리·튜닝·파츠 색)
   - 건강검진 결과지를 자동으로 띄우려면 명령 끝에 `--ez checkup true`를 붙인다.
   - 다른 시나리오: `--es scenario commute | tire_warning | long_drive | traffic_jam | parked`
   - 꾸미기만 보려면 `--es scenario commute --ei speed 60` (30초 뒤 주차 리포트, 소모품 카드의 "정비 완료 기록"으로 보상 항목이 열린다)
   - 실차(또는 Virtual Console) 데이터로 실행: `--es source pleos`. Vehicle SDK의 기어·누적 주행거리, AAOS 주행 제한만 읽는다. 차속은 퍼미션 승인 범위를 확인한 뒤 PT 단계에서 연결한다(코드는 준비됨, 지금은 0으로 둔다). 경고등·공기압 수치는 PRIVATE 퍼미션이라 읽지 않으므로, 건강검진·긴급 화면은 시나리오 모드에서 확인한다.

## 3. 테스트 환경 · 필요 설정

| 항목 | 내용 |
| --- | --- |
| 에뮬레이터 | Pleos Connect Emulator v2.1.0 (시스템 이미지 API 34), 2560×1440 |
| 빌드 | JDK 17, Gradle 8.10.2 (wrapper), AGP 8.8.2, Kotlin 2.0.0, compileSdk 35, minSdk 28 |
| SDK | Pleos Vehicle SDK 2.3.2 (Maven: `https://nexus-playground.pleos.ai/repository/maven-releases/`) |
| 퍼미션 | `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` (주행 기록 유지), `pleos.car.permission.CAR_DRIVE` (기어·누적 주행거리, 신청 없이 쓰는 Vehicle State SDK의 공개 퍼미션). `PRIVATE_*` 퍼미션과 차속 퍼미션은 선언하지 않는다 |
| 필요 설정 | 없음. API 키 불필요 |
| 단위 테스트 | `./gradlew testDebugUnitTest` (205개, 11/12 기준) |

Windows에서 에뮬레이터를 쓰려면 "Windows 하이퍼바이저 플랫폼"을 켜야 한다.

## 4. APK 위치

`apk/SpaceCat_ethos_release.apk` (release 빌드, 서명됨). 용량이 크면 GitHub Releases 링크로 대체.

## 구조

```
app/src/main/java/com/naechamon/
├── data/      차량 데이터 (VehicleDataSource: 시나리오 재생, Pleos 실차 데이터 소스), 꾸미기 저장
├── domain/    순수 Kotlin: 화면 모드, 운전 점수, 소모품, 차BTI·가혹 조건, 건강검진, 꾸미기 규칙
└── ui/        Jetpack Compose 화면 (캐릭터, 리포트, 긴급, 건강검진, 3D 차고 WebView)
app/src/main/assets/garage/  3D 차고 페이지(three.js r128, MIT)와 차종별 메시 7종
```

안전 원칙: 주행 중에는 캐릭터 표정만 표시하고 조작 UI가 없다. 리포트·건강검진은 정차(P단 + 조작 제한 해제) 중에만. 안전 경고등이 켜지면 캐릭터 연출을 멈추고 단순 안내 화면으로 전환한다. 정비 문구는 "점검을 권장해요"까지만. 시나리오 재생 중에도 실제 차가 주행 제한 중이면 주행 화면만 보인다.

## 오픈소스 고지

| 이름 | 쓰는 곳 | 라이선스 |
| --- | --- | --- |
| three.js r128 (`three.min.js`, `OrbitControls.js`) | 3D 차고 페이지 | MIT (`app/src/main/assets/garage/THREE_LICENSE.txt`) |
| AndroidX, Jetpack Compose, Kotlin, kotlinx.coroutines, kotlinx.serialization | 앱 전반 (시나리오 JSON 읽기 포함) | Apache License 2.0 |
| Pleos Vehicle SDK 2.3.2 | 실차 데이터 소스 | Pleos Playground 제공 SDK (이용 약관에 따름) |

앱 안에 넣은 글꼴 파일은 없다. 캐릭터와 아이콘 그림은 직접 만들었다.
