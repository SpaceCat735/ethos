# 차량 데이터 확인 결과 (이슈 #1)

2026-10-28, Pleos 공식 문서(document.pleos.ai) 기준. 기획안 4장 데이터 표를 검증한 결과다.

## 핵심 요약

1. **차량 데이터는 두 통로로 받는다.**
   - **AAOS 표준 Car API** (`CarPropertyManager`): 속도, 기어, 연료량 같은 표준 데이터. Pleos는 표준에 있는 데이터는 따로 제공하지 않는다.
   - **Pleos Vehicle SDK** (`ai.pleos.playground:Vehicle:2.3.2`): 연비, 주행거리, 경고등(telltale), 오일 상태 등 제조사 전용 데이터.
   - 따라서 `PleosVehicleDataSource`는 두 통로를 함께 쓴다.
2. **차종마다 지원 여부가 다르다.** 차종 이름으로 분기하지 말고 SDK는 `checkXXXCapabilities`, AAOS는 `CarPropertyManager`로 실제 지원 여부를 확인해야 한다. 미지원 항목은 캐릭터 표현을 끄거나 시나리오 모드로 대체한다.
3. **진단 8개 시스템은 "경고등(telltale)"으로 받을 수 있다.** 별도 진단 API는 없고, 계기판 경고등 상태를 읽는 방식이다.
4. **에뮬레이터 v2.1.0에서 LLM SDK 지원이 종료됐다** (에뮬레이터 릴리스 노트). → **결정: 미리 써 둔 상황별 대사 + TTS SDK로 대체** (TTS SDK는 전 차종 지원).

## 항목별 결과

| 기획 데이터 | 받는 곳 | API / 속성 | 판정 |
| --- | --- | --- | --- |
| 속도 | AAOS | `PERF_VEHICLE_SPEED` (m/s) | 제공 (표준) |
| 가감속 패턴 | 직접 계산 | 속도 변화량으로 계산 + Vehicle SDK `Brake.getCurrentBrakePedalStatus` | 계산으로 대체 |
| 기어 상태(P단) | AAOS | `GEAR_SELECTION` / `CURRENT_GEAR` | 제공 (표준) |
| 연비·전비 | Vehicle SDK | `Fuel`: 순간·현재·누적·주유 후 연비 (`getInstantaneousFuelEconomy` 등) | 제공 (차종별 확인) |
| 연료 잔량 | Vehicle SDK / AAOS | `Fuel.getFuelPercent`, `FUEL_LEVEL` · EV는 `EvBattery` | 제공 |
| 누적 주행거리 | Vehicle SDK | `Odometer.getCurrentDistanceDriven` | 제공 |
| 주행 거리·시간 (트립) | Vehicle SDK | `Odometer.getTripCurrentDrivingDistance`, `getTripCurrentDrivingTime` 등 | 제공 |
| 연속 운전 시간 | Vehicle SDK | 트립 주행 시간으로 계산 | 계산으로 대체 |
| 주행 구간·시간대 | FusedLocation SDK | 위치(위도·경도·방향) | 차종별 확인 (`isSupported`) |
| 타이어 공기압 경고 | Vehicle SDK | `Tire.getLowTirePressure` (바퀴별), 경고등 `TPMS` | 제공 |
| 타이어 공기압 수치 | AAOS | `TIRE_PRESSURE` (kPa) | 표준 속성, 차종별 확인 |
| 냉각수 온도 수치 | AAOS | `ENGINE_COOLANT_TEMP` (℃) | 표준 속성, 차종별 확인 |
| 냉각수 과열 경고 | Vehicle SDK | 경고등 `ENGINE_TEMPERATURE` | 제공 |
| 엔진오일 상태 | Vehicle SDK | `Safety.getEngineOilLevelState`, 경고등 `ENGINE_OIL_PRESSURE` | 제공 (기획엔 없던 데이터) |
| 브레이크액·변속기 오일 | Vehicle SDK | `Safety.getBrakeFluidLevelState`, `getTransmissionOilLevelState` | 제공 (기획엔 없던 데이터) |
| 에어백 | Vehicle SDK | `Safety.hasSeatAirbagEnabled`, `hasSeatAirbagBeenDeployed`, 경고등 `AIRBAG` | 제공 |

## 진단 8개 시스템 → 경고등 매핑

| 진단 시스템 (기획 3-3) | 경고등 (`TelltaleType`) | 비고 |
| --- | --- | --- |
| 엔진 제어 (심장) | `ENGINE` | |
| 변속 제어 (허리) | 해당 경고등 없음 | `Safety.getTransmissionOilLevelState`로 대체 검토 |
| 제동·전자식 주차 브레이크 (다리) | `ABS`, `ELECTRONIC_PARKING_BRAKE` | |
| 전동식 조향 (팔) | `MDPS` | |
| 타이어 공기압 (발) | `TPMS` | |
| 12V 배터리 (기력) | `LOW_VOLT_BATTERY` | |
| 운전자 보조 (눈) | `FCA`, `LKA` 등 | 여러 경고등을 묶어서 판정 |
| 에어백 (갑옷) | `AIRBAG` | |

경고등 읽기: `Safety.getTelltalesInfo`, 변화 구독: `Safety.registerTelltales`. 필요 권한은 [Vehicle SDK 권한 문서](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/Vehicle/vehicle-permission)에서 API별로 확인한다 (샘플 앱 기준 `pleos.car.permission.CAR_SAFETY`, `CAR_SPEED`, `CAR_POWERTRAIN`, `CAR_MILEAGE`, `CAR_ENERGY` 등).

## 안전·운전 제한 관련 (이슈 #7에 반영)

- 운전 중 사용 가능 여부는 **`CarUxRestrictions`** 로 확인해야 한다. 기어(P단)만 보는 것보다 이 값을 기준으로 삼는 것이 공식 권장이다.
- DO(Distraction Optimized)를 선언하지 않은 앱은 주행 중 사용 불가. 에토스는 표정 표시 때문에 DO 선언이 필요하다 (뼈대에 반영됨).
- 운전 중에는 게임·장식용 반복 애니메이션 유도 금지. 운전 중 표정 변화는 짧고 정적으로.
- 앱 카테고리 후보: **Mobility Services** ("주행과 밀접한 차량 관리 서비스"는 운전 중 제한적으로 허용될 수 있음) 또는 Lifestyle.

## 에뮬레이터에서 값 바꾸기

Pleos Connect Virtual Console(`adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088/)
- Motion: 기어, 브레이크, 조향각, 주행거리
- Safety: 경고등 켜기/끄기/깜빡임
- Energy: 주행 가능 거리, 배터리
- 공기압 수치·냉각수 온도를 바꿀 수 있는지는 에뮬레이터에서 직접 확인 필요 (#2)

## 아직 확인 못 한 것

- API별 상세 페이지(반환 타입, 단위, 필요 권한)는 문서 사이트가 화면에서 그리는 방식이라 자동으로 읽지 못했다. 구현할 때 브라우저로 확인한다.
- 차종별 실제 지원 표는 공식 문서에 없다. 실행 중 `checkXXXCapabilities`로만 알 수 있다.
- LLM SDK가 실차에서도 종료됐는지, 에뮬레이터에서만 빠진 것인지.

## 출처

- [SDK API 정책 (AAOS 표준 우선)](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/pleos-connect-sdk-api)
- [API 호환성](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability)
- [Vehicle SDK API 레퍼런스](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/Vehicle/intro)
- [운전 중 주의 분산 가이드](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline)
- [지역별 운전 제한 정책](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy)
- [앱 카테고리](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/app-category)
- [에뮬레이터 v2.1.0 릴리스 노트](https://document.pleos.ai/en/docs/pleos-only/release-note/emulator-261015)
- [Virtual Console 가이드](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)
