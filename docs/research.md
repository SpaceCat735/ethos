# 탐색 결과 정리 (2026-10-28)

기획안 구체화를 위해 공모전 조건, Pleos SDK 현황, 정비 기준을 조사한 결과입니다.
**공모전 공식 홈페이지는 직접 열지 못해, 공식 출처를 인용한 외부 정리 문서로 확인한 항목이 있습니다.** 접수양식(DOCX)과 공지는 반드시 홈페이지에서 직접 확인하세요.

---

## 1. 공모전 일정

| 날짜 | 일정 | 비고 |
| --- | --- | --- |
| 10/21 ~ 11/15 23:59 | 참가 접수 | 이메일 제출 |
| 10/28 오후 (10/26로 표기된 공고도 있음) | 온라인 설명회 | 홈페이지 공지 확인 |
| 11/23 | 1차 결과 발표 | 20팀 선발 |
| **12/3 ~ 12/4** | **오프라인 PT 심사** | **동작하는 앱 시연 필수** (에뮬레이터 화면 인정). 현대차 강남대로사옥 UX스튜디오 서울 |
| 12/18 | 최종 결과 발표 | 10팀 = 수상 10자리 |
| 2027년 2월 | 행사 전시·시상 | 최종 순위 발표, 참석 의무 |

**시사점**
- 1차 발표에서 PT까지 10일뿐이다. **11/15까지 핵심 화면이 동작하는 수준**이어야 한다.
- 순위는 PT 결과물로 결정되며, PT 이후 추가 개발은 순위에 반영되지 않는다.

## 2. 시상 구조 (일반 부문 기준)

| 상격 | 상금 | 일반 부문 |
| --- | --- | --- |
| 대상 | 1,000만 원 | 1팀 |
| 최우수상 | 600만 원 | 1팀 |
| 우수상 | 400만 원 | 2팀 |
| 아이디어 특별상 | 150만 원 | 부문 통합 2팀 |
| 숏폼 챌린지 | 50만 원 | 별도 1팀 (본상 가산점 없음) |

회사 소속 참가자는 일반 부문이다.

## 3. 심사 기준 (1차 서류)

| 항목 | 보는 것 | 에토스 대응 |
| --- | --- | --- |
| 차량특화성 | 모바일 대비 차량 환경의 고유 가치, 주행·정차·차량 기능과의 결합 | 차량 데이터 없이는 성립 불가, 가혹 조건 판정 → 정비 주기 연동 |
| 창의성 | 차별성, Pain Point 정의와 해결 방식 | 숫자·체크 표시를 캐릭터 상태로 번역 |
| 기술 활용성 | Pleos SDK/API가 핵심 경험에 기여하는 정도 | Vehicle SDK(Odometer, Brake, Safety, Tire, Fuel) 중심 |
| 사업성 | 타깃, 예상 MAU, Retention Rate, 확장 계획 | 기획안 6장에 추가 |
| 가산점 | 시연 영상, GitHub(소스·APK) | 둘 다 제출 |

세부 배점은 비공개. 2차 PT 기준은 추후 안내 예정.

## 4. 접수양식(기획서) 구조

「02 앱 프로젝트 소개/심사 자료」 항목:

1. 앱 한 줄 소개
2. 기획 배경
3. 앱 주요 기능 및 차량 특화 요소
4. 주요 사용 시나리오
5. 개발 계획 (Pleos Connect 구현 범위, SDK/API 사용계획)
6. 기대효과 및 발전 가능성 (타깃 고객 분석, 예상 MAU, Retention Rate, 향후 확대 계획)
7. 참고자료 (선택, 가산점)
8. 숏폼 링크 (선택, 가산점 없음)

분량 제한 없음. `docs/proposal.md`는 이 순서로 작성한다.

## 5. 제출 조건 체크리스트

- [ ] 이메일: appdevchallenge@hyundai-partners.com
- [ ] 제목: `[차량용 앱 개발 공모전] 팀명(개인은 성명)_대표자명`
- [ ] 팀원 전원 CC, 대표자·팀원 전원 개별 동의서 서명·날짜
- [ ] **심사 자료 익명 작성**: 이름·연락처·이메일·소속 금지. 링크·첨부·GitHub에도 실명·계정정보 노출 주의
- [ ] GitHub: 소스 + APK 함께
- [ ] APK: `apk/팀명_앱이름_release.apk` (release 빌드), 용량 초과 시 Releases 링크
- [ ] README 필수 4항목: 앱 이름·한 줄 소개 / 실행 방법 / 테스트 환경·필요 설정 / APK 위치
- [ ] Private 저장소면: 심사용 계정 초대는 필요 없음. 접수 메일에 저장소 주소와 최종 커밋 번호를 적고, 11/16까지 Public으로 전환해 11/23까지 유지 (운영사무국 회신 2026-11-11. 안 하면 개발 산출물 가산점 없음)
- [ ] 수정 제출 시 제목 앞 `[수정제출]`, 같은 이메일에서 전체 재제출. 마감 전 마지막 제출본으로 심사

## 6. Pleos SDK 현황

### 6-1. 사용 가능 상태 (Playground 문서 기준)

| SDK | 상태 | 에토스 영향 |
| --- | --- | --- |
| **Vehicle State** | 신청 없이 사용 가능 | 핵심 데이터 원천 |
| **NaviHelper** | 신청 없이 사용 가능 | 휴게소 경유지 추가 등 (내비 탑재 차량만 완전 호환) |
| Vehicle Control | 준비 중, 신청 불가 | 사용 안 함 |
| FusedLocation | 준비 중, 신청 불가 | 위치 기반 차BTI 판정 제외 |
| TextToSpeech / SpeechToText | 준비 중, 신청 불가 | **캐릭터 음성 안내 보류** |
| LLM (Gleo AI) | 현재 문서 목록에 없음 | **AI 대사 생성 제외 → 미리 작성한 대사 세트** |

### 6-2. Vehicle SDK 도메인 (SDK 2.3.2, 19개)

Brake, Car info, Display, Door, Driving mode, EV battery, Fuel, HVAC, Light, Odometer, Safety, Seat, Side mirror, Steering wheel, Tire, Toll, Turn signal, Window, Wiper

| 도메인 | 내용 | 에토스 활용 |
| --- | --- | --- |
| Odometer | 총 주행거리, 트립 거리·시간, 단위 | 소모품 주기, 차BTI(트립 길이·평균속도) |
| Brake | 브레이크 페달, 주차 브레이크 상태 | 정지·출발 빈도 → 가혹 조건 판정, 운전 습관 |
| Safety | 차량 경고(Warning) 정보 | 차량 건강검진 (제공 경고 종류 **미확인**) |
| Tire | 타이어 상태 | 발 상태 표현 (공기압 수치 제공 여부 **미확인**) |
| Fuel / EV battery | 연료·배터리 정보 | 경험치, 절약 리포트 (세부 **미확인**) |
| Car info | 제조사, 연식 등 | 차종별 정비 주기 선택 |

- **Gear는 샘플 카탈로그에 없다.** 정차/주행 판단은 AAOS `CarUxRestrictions`(주행 제한 상태)로 한다.
- 속도·가속도 전용 도메인은 보이지 않는다. AAOS `CarPropertyManager` 차량 속성 사용 가능 여부는 에뮬레이터에서 확인 필요. SDK에 없는 특수 속성은 기술지원팀 검토 요청이 필요하다.
- 차종마다 지원 범위가 달라 각 도메인의 `checkXXXCapabilities`로 확인해야 한다. 모델명으로 분기하지 말 것.
- 승인되지 않은 퍼미션을 선언하면 앱 심사에서 탈락한다.

### 6-3. 참고: Fleet API 경고등 목록 (우리가 쓰는 SDK 아님)

유럽 전용 서버 API인 Fleet/Vehicle Data API에는 에어백, ABS, EBD, EPS, EPB, ESC, 타이어 공기압(바퀴별 LOW), 브레이크액, 워셔액, 엔진오일 레벨, 헤드램프 등 경고등이 정의되어 있다. Vehicle SDK의 Safety 도메인이 이와 비슷한 항목을 줄 가능성이 있으나 **확인 전까지는 후보로만** 본다.

### 6-4. 개발 환경 (공모전 안내 기준)

- Java 또는 Kotlin, **최소 Android API 28**, Gradle 8.0 이상
- Pleos Connect Emulator 2.1.0+ (CRN 불필요), 시스템 이미지 API 34, RAM 4GB, Mac ARM64 이미지 제공
- 에뮬레이터 화면 2560 x 1440
- 공식 샘플: https://github.com/Pleos-Playground/pleos-sample-vehicle-app (JDK 17, Vehicle SDK `ai.pleos.playground:Vehicle:2.3.2`)
- Virtual Console에서 기어(P/R/N/D), 브레이크 페달, 주행거리, 조향각, 배터리 SOC, 경고등 등을 가상 조작 가능 → 데모 연출에 사용

## 7. 주행 중 정책

- `CarUxRestrictions`로 주행 제한 상태를 확인하고 제한된 기능의 UI를 줄이거나 끈다.
- 주행 중 움직이는 이미지·애니메이션 최소화, 장식적·반복적 시각 요소 지양.
- **게임·웹서핑처럼 주의를 분산시키는 활동을 유도하지 말 것.**
- 앱 카테고리: Maps/Navigation, Mobility Services, Lifestyle, Entertainment, Business, Music/Audio, Kids, Social/Communication.
- **Mobility Services** 카테고리는 "주행과 밀접한 차량 관리 서비스"를 주행 중 제한적으로 허용받을 수 있다.

**결론**: 에토스는 "캐릭터 게임"이 아니라 **Mobility Services 카테고리의 차량 관리 앱**으로 포지셔닝한다. 육성·진화 연출은 정차 중에만, 주행 중에는 정적인 상태 아이콘만 둔다.

## 8. 정비 주기 기준

현대·기아 취급설명서의 정기 점검표는 **일반 조건**과 **가혹 조건**을 구분한다. 가혹 조건 예: 짧은 거리 반복 주행, 과다한 공회전, 교통 체증 구역 주행, 잦은 고속 주행 및 급가감속, 정지와 출발의 잦은 반복 등.

| 항목 | 예시 주기 (차종별 상이) |
| --- | --- |
| 엔진오일·필터 | 일반 10,000~15,000km 또는 12개월(투싼 2025 가솔린 10,000km, 쏘나타 2024는 15,000km) / 가혹 7,500km 또는 6개월 (모델에 따라 5,000km). 앱은 10,000km를 쓴다 |
| 공조장치 에어필터 | 15,000km 또는 12개월 |
| 타이어 위치 교환 | 10,000km 또는 12개월 |
| 브레이크액 | 50,000km |
| 냉각수 | 최초 200,000km 또는 10년 |

**시사점**: 주행 데이터(트립 길이, 평균속도, 정지·출발 빈도)로 **가혹 조건 여부를 판정해 정비 주기를 자동 조정**할 수 있다. 차BTI(재미)와 정비 기준(알맹이)을 직접 연결하는 핵심 근거.

※ 앱에는 차종별 공식 주기표를 넣고, "취급설명서 기준, 정비소 확인 권장" 문구를 함께 표시한다.

## 9. 권리·기타

- 출품작 IP는 참가자 소유. 주최 측은 통상실시권, 결과 발표일부터 4개월간 우선협상권을 가질 수 있음.
- 표절·타 공모전 수상작은 심사 제외. 오픈소스는 라이선스 준수 및 출처 표기.
- 상금은 대표자에게 일괄 지급.

## 10. 설명회·운영국에 확인할 질문

1. 준비 중인 SDK(TTS·STT·위치)의 공모전 기간 내 개방 계획이 있는가? 없으면 AAOS 표준 API로 대체해도 되는가?
2. Safety 도메인이 제공하는 경고 종류, 속도 데이터 접근 방법
3. PT용 에뮬레이터·SDK 버전, 개인 노트북 사용 여부
4. (해결, 운영사무국 회신 2026-11-11) 심사용 계정 초대 불필요. GitHub 계정명·커밋 작성자의 익명화는 요구하지 않으며 실명이 보여도 감점·실격 없음
5. 2027년 2월 필수 참석 날짜·인원

## 11. 경쟁·유사 서비스 조사 (2026-11-01)

웹 검색 위주로 조사했다. 앱스토어·커뮤니티 원문은 직접 보지 못했으므로 "못 찾음"은 "없음"의 증거가 아니다.

| 사례 | 누가 | 하는 일 | 겹치는 정도 |
| --- | --- | --- | --- |
| 모비몬(MobiMon) | 현대모비스 사내 해커톤 2026 우수작 | 차량 정보를 캐릭터로 표현. 세부 기능 비공개 | **높음** |
| 모비딕 / 카 드 메이커 | 같은 해커톤 우승작 / 우수작 | AI 차량 진단 / 운전 기록을 소장 카드로 | 중간 |
| Ford EcoGuide, Honda Eco Assist, Nissan Leaf 나무 | 자동차 회사 (2010년대) | 에코 운전 시 계기판의 잎·나무가 자람 | 중간 |
| NIO NOMI | NIO | 대시보드 위 얼굴 달린 AI 동반자 | 중간 |
| 티맵 운전점수, 마이현대 드라이빙 인사이트, 보험 텔레매틱스 | 티맵·현대차·보험사 | 운전 점수·할인. 캐릭터 없음 | 중하 |
| 마이클, 그냥 차고 | 정비·차계부 앱 | 소모품 교체 알림 | 낮음 |
| HondaDog (2013) | 혼다 | 강아지 아바타 드라이빙 앱 | 낮음 |

- Pleos App Market(2026-09, 18종)에는 차량 관리·운전 습관 앱이 없다.
- 공모전 참가자의 공개 아이디어 글은 찾지 못했다 (접수 기간 중).
- 현대차·기아 24H 코드 레이스 수상작은 '창밖동화', '테일싱크', '아기 꿀잠 드라이브' 등 가족·동승자 콘텐츠가 많았다.
- 차량 내 게임화는 주의 분산을 일으킨다는 연구가 있다 (Kreußlein & Koch, 2016). 정차 중에만 연출하는 원칙이 근거가 된다.

**판단**: 운전 점수, 에코 운전의 식물 표현, 소모품 알림, 차 안의 캐릭터는 각각 흔하다. 차BTI 판정·진화, 경고를 신체 부위로 보여 주는 건강검진표, 소모품을 배고픔·피로로 표현, 가혹 조건 기반 정비 주기 조정을 차량 화면 안에서 한 세트로 묶은 상용 앱은 찾지 못했다.

**대응**: (1) 기획서 첫 문장은 캐릭터가 아니라 "내 운전에 맞는 정비 주기"로 시작한다. (2) "모비몬과 무엇이 다른가"를 한 줄로 준비한다: 캐릭터는 입구이고 결과는 정비 주기와 서비스센터 방문이다. (3) 가족 요소(칭찬 카드, 동승자 모드)를 얹는다. (4) 제출 전 앱스토어에서 "차량 키우기", "car pet"으로 직접 검색한다.

출처: 현대모비스 해커톤 https://www.koreancenter.or.kr/news/articleView.html?idxno=1430776 · 24H 코드 레이스 https://www.hyundaimotorgroup.com/ko/news/hyundai-motor-company-kia-24h-code-race-hackathon · Pleos 앱마켓 https://www.fnnews.com/news/202609070949054267 · Honda/Ford https://jalopnik.com/5094111/2010-honda-insight-ecological-drive-assist-system-grows-leaves-gets-other-features · NIO NOMI https://www.nio.com/blog/history-behind-nomi · 게임화 주의분산 https://link.springer.com/chapter/10.1007%252F978-3-319-40355-7_31

## 12. 아직 안 한 조사

- 국내 Pleos Connect 탑재 차종 판매량 (예상 MAU 근거)
- 운전자 Pain Point 근거 (커뮤니티·설문)

## 출처

- 공모전 분석 정리 (공식 공고·FAQ·접수양식 인용): https://chanoo.github.io/
- 공모전 공고: https://linkareer.com/activity/358198
- Pleos 프로젝트 생성·SDK 신청: https://document.pleos.ai/en/docs/pleos-only/create-my-project
- API 호환성: https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability
- 주행 안전 설계: https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline
- 앱 카테고리: https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/app-category
- Vehicle SDK 소개: https://document.pleos.ai/en/api-reference/connect-sdk-pleos/Vehicle/intro
- Vehicle SDK 샘플: https://github.com/Pleos-Playground/pleos-sample-vehicle-app
- Fleet API 경고등: https://pleos.ai/playground/resources/en/api-reference/fleet-api/fleet-api-webhook/webhook-event-type/vehicle-event
- 현대차 취급설명서 정기 점검: https://ownersmanual.hyundai.com/full_webhelp/us4/2026/ko_kr/id7b77f1c5ffe.html
- 기아 취급설명서 정기 점검: https://ownersmanual.kia.com/docview/webhelp/doc/6a46998e-2906-4b86-a45a-457bd8660d99/topics/t01113.html
