package com.naechamon.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naechamon.R
import com.naechamon.domain.CareBook
import com.naechamon.domain.CareGrade
import com.naechamon.domain.CareStamp
import com.naechamon.domain.CharacterAppearance
import com.naechamon.domain.CharacterExpression
import com.naechamon.domain.CharacterMark
import com.naechamon.domain.CharacterStage
import com.naechamon.domain.Consumable
import com.naechamon.domain.DisplayMode
import com.naechamon.domain.DrivingEvent
import com.naechamon.domain.DrivingEventType
import com.naechamon.domain.EmergencyType
import com.naechamon.domain.GreetingType
import com.naechamon.domain.HealthCheckup
import com.naechamon.domain.HealthCheckupBuilder
import com.naechamon.domain.ParkedReport
import com.naechamon.domain.PraiseLevel
import com.naechamon.domain.ParkedReportBuilder
import com.naechamon.domain.ServiceRecord
import com.naechamon.domain.StarterShape
import com.naechamon.domain.TripSummary
import com.naechamon.domain.VehicleProfile
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.cos
import kotlin.math.sin

// 색은 Theme.kt(낮/밤 두 벌)에서만 온다. 긴급 화면은 EmergencyScreen에서 따로 정한다

// 앱 창은 차량 화면 오른쪽 약 2/3(약 1700×1184dp)이거나, 앱 2개를 띄우면 세로로 긴 반쪽(약 840×1184dp)이다.
// 모든 크기는 앱 창의 짧은 변(AppWindow.unit)에 비례해 정한다.
// 모양은 플레오스 기본 앱(차량 관리·설정)과 같은 틀: 제목 바 + 왼쪽 메뉴 목록 + 오른쪽 내용. 1px 선으로만 나누고 카드는 쓰지 않는다.
// 두뇌 층이 정한 화면 모드·캐릭터 모습·리포트만 받아서 그린다.
@Composable
fun MainScreen(
    mode: DisplayMode,
    appearance: CharacterAppearance,
    todayDistanceKm: Double,
    report: ParkedReport,
    checkup: HealthCheckup,
    greeting: GreetingType,
    onServiceDone: (Consumable) -> Unit = {},
    // 데모용: 시작부터 월간 점검표를 연다 (정차 중일 때만 보인다)
    openCheckupAtStart: Boolean = false,
    // 내 차 설정(3D 차고) 화면. 없으면 메뉴 항목도 없다 (미리보기 등)
    // starter: 첫 실행 차급 고르기에서 고른 차급(있으면 그 차를 바로 찍어 저장하고 닫는 모드), null = 보통 차고
    garage: (@Composable (starter: StarterShape?, onClose: () -> Unit) -> Unit)? = null,
    // 첫 실행 차급 고르기를 아직 안 했으면 true. 정차 중에만 패널을 띄운다
    askStarterShape: Boolean = false,
    // 고른 차급 (null = 나중에). 저장은 부른 쪽이 한다
    onStarterShape: (StarterShape?) -> Unit = {},
    // 정비 수첩 (관리 등급·도장). 두뇌 층이 정한 값만 그린다
    careBook: CareBook = CareBook(emptyList()),
    // 방금 찍힌 도장과 (오른 경우) 새 등급. 정차 중에만 한 번 연출하고 onStampShown으로 지운다
    stampFlash: Pair<CareStamp, CareGrade?>? = null,
    onStampShown: () -> Unit = {},
) {
    var showCheckup by rememberSaveable { mutableStateOf(openCheckupAtStart) }
    var showGarage by rememberSaveable { mutableStateOf(false) }
    var garageStarter by rememberSaveable { mutableStateOf<StarterShape?>(null) }
    // 주행·긴급으로 바뀌면 차고 화면을 닫는다. 다시 정차해도 저절로 열리지 않게
    LaunchedEffect(mode) { if (mode != DisplayMode.Parked) { showGarage = false; garageStarter = null } }
    val openGarage: (() -> Unit)? = garage?.let { { garageStarter = null; showGarage = true } }
    val pickStarter: (StarterShape?) -> Unit = { choice ->
        onStarterShape(choice)
        // 차급을 골랐으면 차고를 자동 저장 모드로 열어 고른 차의 정지 그림을 찍는다. "나중에"는 기본 그림 그대로
        if (choice != null && garage != null) {
            garageStarter = choice
            showGarage = true
        }
    }
    var lastStage by remember { mutableStateOf(appearance.stage) }
    var showHatch by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(appearance.stage) {
        if (lastStage == CharacterStage.EGG && appearance.stage == CharacterStage.CAR) showHatch = true
        lastStage = appearance.stage
    }
    EthosTheme {
        when (mode) {
            is DisplayMode.Emergency -> EmergencyScreen(mode.types)
            DisplayMode.Driving -> DrivingScreen(appearance, todayDistanceKm)
            // 월간 점검표는 정차 중에만. 주행으로 바뀌면 이 분기를 타지 않으므로 자동으로 사라진다
            DisplayMode.Parked -> when {
                // 3D 차고는 정차 중에만. 주행으로 바뀌면 이 분기를 타지 않으므로 WebView도 바로 사라진다
                showGarage && garage != null -> garage(garageStarter) { showGarage = false; garageStarter = null }
                showCheckup -> HealthCheckupScreen(checkup, appearance, todayDistanceKm, onClose = { showCheckup = false })
                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    ParkedScreen(appearance, todayDistanceKm, report, greeting, careBook, onServiceDone, onOpenCheckup = { showCheckup = true }, onOpenGarage = openGarage)
                    val unit = appWindow().unit
                    // 도장·부화 연출은 정차 분기 안에만 있으므로 주행으로 바뀌면 바로 사라진다
                    stampFlash?.let { (stamp, gradeUp) -> StampToast(stamp, gradeUp, unit, onDone = onStampShown) }
                    if (showHatch && stampFlash == null) HatchCard(report.carBti, unit, onClose = { showHatch = false })
                    // 첫 실행 차급 고르기. 정차 분기 안에만 있으므로 주행으로 바뀌면 사라지고, 아직 안 골랐으면 다음 정차 때 다시 뜬다
                    if (askStarterShape) StarterShapePanel(onPick = pickStarter)
                }
            }
        }
    }
}

// 운전 중: 제목 바(버튼 없음) + 가운데에 캐릭터 정지 그림만. 글자·메뉴·애니메이션 없음
// 여기에는 parkedIdle·idleGlance를 쓰지 않는다 (안전 규칙: 주행 중 애니메이션 금지)
@Composable
private fun DrivingScreen(appearance: CharacterAppearance, todayDistanceKm: Double) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(ethos.bg)) {
        val h = appWindow().unit
        EthosTitleBar(todayDistanceKm, h)
        CharacterFace(
            appearance = appearance,
            modifier = Modifier
                .align(Alignment.Center)
                // 제목 바만큼 아래로 내려 눈에 가운데로 보이게 한다
                .padding(top = h * 0.07f)
                .size(h * 0.62f),
        )
    }
}

// 첫 실행 차급 고르기 패널 (정차 중에만). 아래 화면 위에 덮고, 뒤쪽 터치는 막는다. 애니메이션 없음.
// 1px 선 상자 + 제목·설명 + 큰 버튼 4개(세단·SUV·소형·나중에)
@Composable
private fun StarterShapePanel(onPick: (StarterShape?) -> Unit) {
    val c = ethos
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg.copy(alpha = 0.88f))
            // 패널 밖을 눌러도 아래 화면이 반응하지 않게 (닫히지도 않는다. "나중에"로 닫는다)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        val h = appWindow().unit
        val stroke = maxOf(1.dp, h * 0.0017f)
        Column(
            modifier = Modifier
                .width(minOf(maxWidth * 0.9f, h * 1.2f))
                .background(c.surface)
                .border(1.dp, c.line)
                .padding(h * 0.045f),
            verticalArrangement = Arrangement.spacedBy(h * 0.016f),
        ) {
            Text(
                text = stringResource(R.string.starter_shape_title),
                color = c.ink,
                fontSize = (h.value * 0.038f).sp,
                lineHeight = (h.value * 0.038f * LINE_HEIGHT).sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.starter_shape_desc),
                color = c.inkSoft,
                fontSize = (h.value * 0.024f).sp,
                lineHeight = (h.value * 0.024f * LINE_HEIGHT).sp,
            )
            Spacer(Modifier.height(h * 0.02f))
            Row(horizontalArrangement = Arrangement.spacedBy(h * 0.02f), modifier = Modifier.fillMaxWidth()) {
                val choices = listOf(
                    StarterShape.SEDAN to R.string.starter_shape_sedan,
                    StarterShape.SUV to R.string.starter_shape_suv,
                    StarterShape.COMPACT to R.string.starter_shape_compact,
                    null to R.string.starter_shape_later,
                )
                choices.forEach { (choice, labelRes) ->
                    val isLater = choice == null
                    Text(
                        text = stringResource(labelRes),
                        color = if (isLater) c.inkSoft else c.ink,
                        fontSize = (h.value * 0.028f).sp,
                        fontWeight = if (isLater) FontWeight.Normal else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .then(if (isLater) Modifier else Modifier.border(stroke, c.ink))
                            .clickable { onPick(choice) }
                            .padding(vertical = h * 0.03f),
                    )
                }
            }
        }
    }
}

// 정차 화면의 내용 페이지. GREETING은 시동 직후(아직 안 달림) 인사. 월간 점검표·내 차 설정은 별도 화면이라 여기 없다
private enum class ParkedPage { GREETING, STATUS, CONSUMABLES, CAREBOOK }

// 주차 중: 제목 바 + 메뉴(가로 창은 왼쪽 목록, 세로 창은 위쪽 탭) + 내용.
// 차량 상태(타일 격자 + 캐릭터 한 줄) ↔ 소모품(행 목록). 정차 중에만 조작 가능. 캐릭터는 나타날 때 한 번 깡총.
@Composable
private fun ParkedScreen(
    appearance: CharacterAppearance,
    todayDistanceKm: Double,
    report: ParkedReport,
    greeting: GreetingType,
    careBook: CareBook,
    onServiceDone: (Consumable) -> Unit,
    onOpenCheckup: () -> Unit,
    onOpenGarage: (() -> Unit)?,
) {
    // 시동 직후엔 인사만(기획안 시나리오 A 1번). 메뉴를 누르면 그 페이지로, 주행이 끝나 리포트가 생기면 차량 상태로
    var page by rememberSaveable { mutableStateOf(if (report.isBeforeTrip) ParkedPage.GREETING else ParkedPage.STATUS) }
    LaunchedEffect(report.isBeforeTrip) { if (!report.isBeforeTrip && page == ParkedPage.GREETING) page = ParkedPage.STATUS }
    val onSelect: (NavItem) -> Unit = { item ->
        when (item) {
            NavItem.STATUS -> page = ParkedPage.STATUS
            NavItem.CONSUMABLES -> page = ParkedPage.CONSUMABLES
            NavItem.CAREBOOK -> page = ParkedPage.CAREBOOK
            NavItem.CHECKUP -> onOpenCheckup()
            NavItem.GARAGE -> onOpenGarage?.invoke()
        }
    }
    val selected = when (page) {
        ParkedPage.GREETING -> null
        ParkedPage.STATUS -> NavItem.STATUS
        ParkedPage.CONSUMABLES -> NavItem.CONSUMABLES
        ParkedPage.CAREBOOK -> NavItem.CAREBOOK
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(ethos.bg)) {
        val window = appWindow()
        val h = window.unit
        val windowWidth = maxWidth
        val content = @Composable { modifier: Modifier ->
            Box(modifier) {
                when (page) {
                    ParkedPage.GREETING -> GreetingPane(appearance, greeting, report.worstConsumable?.consumable, h)
                    ParkedPage.STATUS -> StatusPane(appearance, todayDistanceKm, report, careBook.grade, h, window.isPortrait, windowWidth, onOpenConsumables = { page = ParkedPage.CONSUMABLES })
                    ParkedPage.CONSUMABLES -> ParkedReportPanel(report, h, onServiceDone, Modifier.fillMaxSize(), compact = window.isPortrait)
                    ParkedPage.CAREBOOK -> CareBookPane(careBook, h)
                }
            }
        }
        Column(modifier = Modifier.fillMaxSize()) {
            EthosTitleBar(todayDistanceKm, h)
            if (window.isPortrait) {
                // 세로 창(내비 옆 반쪽): 메뉴를 위쪽 가로 탭 줄로
                NavTabs(selected, h, onOpenGarage != null, onSelect)
                content(Modifier.fillMaxWidth().weight(1f))
            } else {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    NavMenu(selected, h, windowWidth * 0.235f, onOpenGarage != null, onSelect)
                    content(Modifier.fillMaxHeight().weight(1f))
                }
            }
        }
    }
}

// 시동 직후: 캐릭터가 가운데서 한 줄 인사. 아직 리포트가 없다. 나타날 때 한 번 깡총
@Composable
// urgent: 가장 급한 소모품 (배고픔 인사에 그 소모품 이름을 넣는다. 늘 엔진오일이라고 하지 않게)
private fun GreetingPane(appearance: CharacterAppearance, greeting: GreetingType, urgent: Consumable?, h: Dp) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CharacterFace(idleGlance(appearance), Modifier.size(h * 0.45f).parkedIdle(h * 0.05f))
        Text(
            text = if (greeting == GreetingType.HUNGRY) stringResource(R.string.greeting_hungry, stringResource((urgent ?: Consumable.ENGINE_OIL).labelRes()))
            else stringResource(greeting.sayRes()),
            color = ethos.ink,
            fontSize = (h.value * 0.03f).sp,
            lineHeight = (h.value * 0.03f * LINE_HEIGHT).sp,
            textAlign = TextAlign.Center,
            style = LocalTextStyle.current.copy(lineBreak = LineBreak.Heading),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = h * 0.02f),
        )
        Text(
            text = stringResource(R.string.greeting_hint),
            color = ethos.inkSoft,
            fontSize = (h.value * 0.02f).sp,
            modifier = Modifier.padding(top = h * 0.012f),
        )
    }
}

// 차량 상태: 타일 격자(왼쪽) + 캐릭터 한 칸(오른쪽, 창 너비의 1/3). 세로 창은 캐릭터 줄을 위에, 격자를 아래에
@Composable
private fun StatusPane(
    appearance: CharacterAppearance,
    todayDistanceKm: Double,
    report: ParkedReport,
    careGrade: CareGrade,
    h: Dp,
    portrait: Boolean,
    windowWidth: Dp,
    onOpenConsumables: () -> Unit,
) {
    if (portrait) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = h * 0.04f, vertical = h * 0.03f),
                horizontalArrangement = Arrangement.spacedBy(h * 0.04f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CharacterFace(idleGlance(appearance), Modifier.size(h * 0.3f).parkedIdle(h * 0.04f))
                Column(modifier = Modifier.weight(1f)) {
                    ParkedSay(appearance, report.worstConsumable?.consumable, h, TextAlign.Start)
                    report.todayPraise?.let { PraiseLine(it, h, TextAlign.Start) }
                    CareGradeLine(careGrade, h, TextAlign.Start)
                    CharacterSub(h, TextAlign.Start)
                }
            }
            HorizontalDivider(color = ethos.line, thickness = 1.dp)
            StatusTiles(report, todayDistanceKm, h, onOpenConsumables, Modifier.fillMaxWidth().weight(1f))
        }
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            StatusTiles(report, todayDistanceKm, h, onOpenConsumables, Modifier.fillMaxHeight().weight(1f))
            Column(
                modifier = Modifier.fillMaxHeight().width(windowWidth * 0.33f).padding(h * 0.034f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CharacterFace(idleGlance(appearance), Modifier.size(h * 0.37f).parkedIdle(h * 0.05f))
                ParkedSay(appearance, report.worstConsumable?.consumable, h, TextAlign.Center, Modifier.padding(top = h * 0.008f))
                report.todayPraise?.let { PraiseLine(it, h, TextAlign.Center) }
                CareGradeLine(careGrade, h, TextAlign.Center)
                CharacterSub(h, TextAlign.Center)
            }
        }
    }
}

// 주차 화면의 캐릭터 한 줄 대사
@Composable
private fun ParkedSay(appearance: CharacterAppearance, urgent: Consumable?, h: Dp, align: TextAlign, modifier: Modifier = Modifier) {
    Text(
        text = if (appearance.expression == CharacterExpression.HUNGRY) stringResource(R.string.say_parked_hungry, stringResource((urgent ?: Consumable.ENGINE_OIL).labelRes()))
        else stringResource(appearance.expression.parkedSayRes()),
        color = ethos.ink,
        fontSize = (h.value * 0.025f).sp,
        lineHeight = (h.value * 0.025f * LINE_HEIGHT).sp,
        textAlign = align,
        style = LocalTextStyle.current.copy(lineBreak = LineBreak.Heading),
        fontWeight = FontWeight.Medium,
        modifier = modifier,
    )
}

// 오늘 주행 칭찬 한 줄. 차량 상태 화면(정차 중)에만 나온다
@Composable
private fun PraiseLine(praise: PraiseLevel, h: Dp, align: TextAlign) {
    Text(
        text = stringResource(if (praise == PraiseLevel.GREAT) R.string.praise_line_great else R.string.praise_line_rough),
        color = ethos.ink,
        fontSize = (h.value * 0.021f).sp,
        lineHeight = (h.value * 0.021f * LINE_HEIGHT).sp,
        textAlign = align,
        modifier = Modifier.padding(top = h * 0.008f),
    )
}

// 대사 아래 작은 회색 보조 줄
@Composable
private fun CharacterSub(h: Dp, align: TextAlign) {
    Text(
        text = stringResource(R.string.char_sub),
        color = ethos.inkSoft,
        fontSize = (h.value * 0.02f).sp,
        lineHeight = (h.value * 0.02f * LINE_HEIGHT).sp,
        textAlign = align,
        modifier = Modifier.padding(top = h * 0.008f),
    )
}

// ---------- 플레오스 기본 앱 틀: 제목 바·메뉴·선 아이콘 (같은 패키지의 다른 화면도 쓴다) ----------

// 글자 크기를 창에 비례해 키우므로 줄 간격도 같이 준다
internal const val LINE_HEIGHT = 1.3f

// 제목 바: 왼쪽 정사각 아이콘 상자(글자색 배경) + 앱 이름, 오른쪽 "오늘 주행 N km"(숫자만 진하게). 아래 1px 선.
// onBack이 있으면 아이콘 상자 대신 ‹ 화살표와 화면 이름 (월간 점검표 등)
@Composable
internal fun EthosTitleBar(
    todayDistanceKm: Double,
    h: Dp,
    title: String = stringResource(R.string.app_name),
    onBack: (() -> Unit)? = null,
) {
    val c = ethos
    val height = h * 0.085f
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(height).padding(horizontal = h * 0.027f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val boxSize = h * 0.054f
            if (onBack != null) {
                Box(
                    modifier = Modifier.size(boxSize).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { LineIcon(IconKind.BACK, h * 0.034f, c.ink) }
            } else {
                Box(modifier = Modifier.size(boxSize).background(c.ink), contentAlignment = Alignment.Center) {
                    LineIcon(IconKind.CAR, h * 0.034f, c.bg, strokeRatio = 0.065f)
                }
            }
            Spacer(Modifier.width(h * 0.017f))
            Text(text = title, color = c.ink, fontSize = (h.value * 0.034f).sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            val km = formatKm(todayDistanceKm)
            val full = stringResource(R.string.status_today_distance, km)
            val start = full.indexOf(km)
            Text(
                text = buildAnnotatedString {
                    if (start < 0) {
                        append(full)
                    } else {
                        append(full.substring(0, start))
                        withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.Medium)) { append(full.substring(start)) }
                    }
                },
                color = c.inkSoft,
                fontSize = (h.value * 0.025f).sp,
            )
        }
        HorizontalDivider(color = c.line, thickness = 1.dp)
    }
}

// 왼쪽 메뉴 항목. 월간 점검표·내 차 설정은 다른 화면을 연다
private enum class NavItem(@StringRes val labelRes: Int, val icon: IconKind) {
    STATUS(R.string.nav_status, IconKind.LIST),
    CONSUMABLES(R.string.nav_consumables, IconKind.DROP),
    CAREBOOK(R.string.nav_carebook, IconKind.BOOK),
    CHECKUP(R.string.nav_checkup, IconKind.HEART),
    GARAGE(R.string.nav_garage, IconKind.CAR),
}

private fun navItems(showGarage: Boolean): List<NavItem> =
    if (showGarage) NavItem.entries else NavItem.entries.filter { it != NavItem.GARAGE }

// 가로 창: 왼쪽 세로 메뉴 목록. 선택된 줄은 글자색 배경 + 배경색 글자(반전). 오른쪽 1px 선
@Composable
private fun NavMenu(selected: NavItem?, h: Dp, width: Dp, showGarage: Boolean, onSelect: (NavItem) -> Unit) {
    Row(modifier = Modifier.fillMaxHeight()) {
        Column(modifier = Modifier.fillMaxHeight().width(width)) {
            navItems(showGarage).forEach { item ->
                NavRow(item, item == selected, h, Modifier.fillMaxWidth(), onSelect)
            }
        }
        VerticalDivider(color = ethos.line, thickness = 1.dp)
    }
}

// 세로 창: 메뉴를 위쪽 가로 탭 줄로. 기능은 가로 창과 같다 (Pleos 심사: 화면 전환 시 기능 동등)
@Composable
private fun NavTabs(selected: NavItem?, h: Dp, showGarage: Boolean, onSelect: (NavItem) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            navItems(showGarage).forEach { item ->
                NavRow(item, item == selected, h, Modifier.weight(1f), onSelect)
            }
        }
        HorizontalDivider(color = ethos.line, thickness = 1.dp)
    }
}

@Composable
private fun NavRow(item: NavItem, selected: Boolean, h: Dp, modifier: Modifier, onSelect: (NavItem) -> Unit) {
    val c = ethos
    val fg = if (selected) c.bg else c.ink
    Row(
        modifier = modifier
            .height(h * 0.085f)
            .background(if (selected) c.ink else Color.Transparent)
            .clickable { onSelect(item) }
            .padding(horizontal = h * 0.027f),
        horizontalArrangement = Arrangement.spacedBy(h * 0.017f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(item.icon, h * 0.034f, fg)
        Text(
            text = stringResource(item.labelRes),
            color = fg,
            fontSize = (h.value * 0.027f).sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

// 단순한 선 아이콘 (그림 파일 없이 Canvas로). 40칸 기준 좌표
internal enum class IconKind { CAR, LIST, DROP, HEART, TIRE, BRAKE, ROUTE, DOC, ARROW, BACK, BOOK }

@Composable
internal fun LineIcon(kind: IconKind, size: Dp, color: Color, modifier: Modifier = Modifier, strokeRatio: Float = 0.06f) {
    Canvas(modifier = modifier.size(size)) {
        val u = this.size.minDimension / 40f
        val stroke = Stroke(width = this.size.minDimension * strokeRatio, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawIcon(kind, u, stroke, color)
    }
}

private fun DrawScope.drawIcon(kind: IconKind, u: Float, stroke: Stroke, color: Color) {
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, p(x1, y1), p(x2, y2), stroke.width, StrokeCap.Round)
    fun ring(cx: Float, cy: Float, r: Float) = drawCircle(color, r * u, p(cx, cy), style = stroke)
    when (kind) {
        IconKind.CAR -> {
            val body = Path().apply {
                moveTo(6f * u, 24f * u); lineTo(9f * u, 15f * u); lineTo(31f * u, 15f * u); lineTo(34f * u, 24f * u)
                lineTo(34f * u, 33f * u); lineTo(6f * u, 33f * u); close()
            }
            drawPath(body, color, style = stroke)
            ring(12f, 29f, 3f); ring(28f, 29f, 3f)
            val roof = Path().apply { moveTo(9f * u, 15f * u); lineTo(11f * u, 10f * u); lineTo(29f * u, 10f * u); lineTo(31f * u, 15f * u) }
            drawPath(roof, color, style = stroke)
        }
        IconKind.BOOK -> {
            drawRoundRect(color, p(8f, 6f), Size(24f * u, 28f * u), CornerRadius(2f * u), style = stroke)
            line(13f, 6f, 13f, 34f)
            ring(23f, 20f, 5f)
        }
        IconKind.LIST -> {
            drawRoundRect(color, p(6f, 8f), Size(28f * u, 24f * u), CornerRadius(2f * u), style = stroke)
            line(12f, 18f, 28f, 18f); line(12f, 24f, 22f, 24f)
        }
        IconKind.DROP -> {
            val drop = Path().apply {
                moveTo(20f * u, 6f * u)
                cubicTo(20f * u, 6f * u, 11f * u, 16f * u, 11f * u, 23f * u)
                arcTo(Rect(p(11f, 14f), p(29f, 32f)), 180f, -180f, false)
                cubicTo(29f * u, 16f * u, 20f * u, 6f * u, 20f * u, 6f * u)
                close()
            }
            drawPath(drop, color, style = stroke)
        }
        IconKind.HEART -> {
            val heart = Path().apply {
                moveTo(20f * u, 34f * u)
                cubicTo(20f * u, 34f * u, 4f * u, 24f * u, 4f * u, 14f * u)
                cubicTo(4f * u, 8f * u, 8.8f * u, 4.8f * u, 12.8f * u, 4.8f * u)
                cubicTo(16.8f * u, 4.8f * u, 20f * u, 8f * u, 20f * u, 12f * u)
                cubicTo(20f * u, 8f * u, 23.2f * u, 4.8f * u, 27.2f * u, 4.8f * u)
                cubicTo(31.2f * u, 4.8f * u, 36f * u, 8f * u, 36f * u, 14f * u)
                cubicTo(36f * u, 24f * u, 20f * u, 34f * u, 20f * u, 34f * u)
                close()
            }
            drawPath(heart, color, style = stroke)
        }
        IconKind.TIRE -> {
            ring(20f, 20f, 14f); ring(20f, 20f, 5.5f)
            line(20f, 6f, 20f, 14.5f); line(20f, 25.5f, 20f, 34f); line(6f, 20f, 14.5f, 20f); line(25.5f, 20f, 34f, 20f)
        }
        IconKind.BRAKE -> {
            ring(20f, 20f, 14f); ring(20f, 20f, 7f)
            // 바깥 테에서 안쪽으로 짧은 빗금 두 개 (오른쪽 위·왼쪽 아래)
            listOf(-50f, 130f).forEach { deg ->
                val a = Math.toRadians(deg.toDouble())
                val (dx, dy) = cos(a).toFloat() to sin(a).toFloat()
                line(20f + 14f * dx, 20f + 14f * dy, 20f + 9f * dx, 20f + 9f * dy)
            }
        }
        IconKind.ROUTE -> {
            val route = Path().apply {
                moveTo(6f * u, 31f * u)
                cubicTo(11f * u, 18.5f * u, 15.5f * u, 18.5f * u, 20f * u, 24.5f * u)
                cubicTo(24.5f * u, 31f * u, 29f * u, 31f * u, 34f * u, 18.5f * u)
            }
            drawPath(route, color, style = stroke)
            ring(6f, 31f, 2.3f); ring(34f, 18.5f, 2.3f)
        }
        IconKind.DOC -> {
            drawRoundRect(color, p(6f, 8f), Size(28f * u, 24f * u), CornerRadius(1.5f * u), style = stroke)
            line(12f, 15.5f, 28f, 15.5f); line(12f, 21.5f, 28f, 21.5f); line(12f, 27.5f, 21.5f, 27.5f)
        }
        IconKind.ARROW -> {
            line(8f, 20f, 31f, 20f)
            val head = Path().apply { moveTo(21.5f * u, 10.5f * u); lineTo(31f * u, 20f * u); lineTo(21.5f * u, 29.5f * u) }
            drawPath(head, color, style = stroke)
        }
        IconKind.BACK -> {
            val chevron = Path().apply { moveTo(24f * u, 8f * u); lineTo(13f * u, 20f * u); lineTo(24f * u, 32f * u) }
            drawPath(chevron, color, style = stroke)
        }
    }
}

// 소수 첫째 자리까지 (예: 12.4)
private fun formatKm(km: Double): String = String.format(java.util.Locale.KOREA, "%.1f", km)

@StringRes
private fun GreetingType.sayRes(): Int = when (this) {
    GreetingType.EGG -> R.string.greeting_egg
    GreetingType.HUNGRY -> R.string.greeting_hungry
    GreetingType.MORNING -> R.string.greeting_morning
    GreetingType.DAY -> R.string.greeting_day
    GreetingType.EVENING -> R.string.greeting_evening
}

@StringRes
private fun CharacterExpression.parkedSayRes(): Int = when (this) {
    CharacterExpression.NORMAL -> R.string.say_parked_normal
    CharacterExpression.HAPPY -> R.string.say_parked_happy
    CharacterExpression.SAD -> R.string.say_parked_sad
    CharacterExpression.HURT -> R.string.say_parked_hurt
    CharacterExpression.SLEEPY -> R.string.say_parked_sleepy
    CharacterExpression.HUNGRY -> R.string.say_parked_hungry
    CharacterExpression.BORED -> R.string.say_parked_bored
    CharacterExpression.PROUD -> R.string.say_parked_proud
}

// 미리보기는 앱 창 크기(약 1700×1184) 기준
private fun car(expression: CharacterExpression, marks: Set<CharacterMark> = emptySet()) =
    CharacterAppearance(CharacterStage.CAR, expression, marks)

private val previewReport = ParkedReportBuilder.build(
    trip = TripSummary(
        distanceKm = 12.4,
        events = listOf(DrivingEvent(DrivingEventType.HARD_STOP, 242_000L)),
        mood = 72,
    ),
    profile = VehicleProfile(
        odometerKm = 42_300,
        serviceRecords = mapOf(
            Consumable.ENGINE_OIL to ServiceRecord(33_200, LocalDate.of(2026, 5, 2)),
            Consumable.AC_FILTER to ServiceRecord(28_000, LocalDate.of(2025, 11, 10)),
            Consumable.TIRE to ServiceRecord(5_000, LocalDate.of(2024, 3, 15)),
        ),
        ratedEfficiency = 12.5,
        totalExperience = 310.0,
    ),
    today = LocalDate.of(2026, 11, 1),
)

private val previewCheckup = HealthCheckupBuilder.build(emptySet(), previewReport.consumables, YearMonth.of(2026, 10))

@Preview(widthDp = 1700, heightDp = 1184)
@Composable
private fun DrivingNormalPreview() {
    MainScreen(DisplayMode.Driving, car(CharacterExpression.NORMAL), 12.4, previewReport, previewCheckup, GreetingType.DAY)
}

@Preview(widthDp = 1700, heightDp = 1184)
@Composable
private fun DrivingSadPreview() {
    MainScreen(DisplayMode.Driving, car(CharacterExpression.SAD), 12.4, previewReport, previewCheckup, GreetingType.DAY)
}

// 세로 창(내비 옆 반쪽) 미리보기. 에뮬레이터 실측 841×1210dp (전체 창은 1682×1210dp)
@Preview(widthDp = 841, heightDp = 1210)
@Composable
private fun PortraitDrivingPreview() {
    MainScreen(DisplayMode.Driving, car(CharacterExpression.NORMAL), 12.4, previewReport, previewCheckup, GreetingType.DAY)
}

@Preview(widthDp = 841, heightDp = 1210)
@Composable
private fun PortraitParkedPreview() {
    MainScreen(DisplayMode.Parked, car(CharacterExpression.HUNGRY, setOf(CharacterMark.WORN_SHOES)), 12.4, previewReport, previewCheckup, GreetingType.DAY)
}

@Preview(widthDp = 841, heightDp = 1210)
@Composable
private fun PortraitCheckupPreview() {
    MainScreen(DisplayMode.Parked, car(CharacterExpression.NORMAL), 12.4, previewReport, previewCheckup, GreetingType.DAY, openCheckupAtStart = true)
}

@Preview(widthDp = 1700, heightDp = 1184)
@Composable
private fun DrivingBoredPreview() {
    MainScreen(DisplayMode.Driving, car(CharacterExpression.BORED), 12.4, previewReport, previewCheckup, GreetingType.DAY)
}

@Preview(widthDp = 1700, heightDp = 1184)
@Composable
private fun DrivingProudPreview() {
    MainScreen(DisplayMode.Driving, car(CharacterExpression.PROUD), 12.4, previewReport, previewCheckup, GreetingType.DAY)
}

@Preview(widthDp = 1700, heightDp = 1184)
@Composable
private fun DrivingHurtWithMarksPreview() {
    MainScreen(DisplayMode.Driving, car(CharacterExpression.HURT, setOf(CharacterMark.SWEAT, CharacterMark.FOOT_CAST)), 12.4, previewReport, previewCheckup, GreetingType.DAY)
}

@Preview(widthDp = 1700, heightDp = 1184)
@Composable
private fun DrivingEggPreview() {
    MainScreen(DisplayMode.Driving, CharacterAppearance(CharacterStage.EGG, CharacterExpression.NORMAL), 3.2, previewReport, previewCheckup, GreetingType.DAY)
}

@Preview(widthDp = 1700, heightDp = 1184)
@Composable
private fun ParkedHappyPreview() {
    MainScreen(DisplayMode.Parked, car(CharacterExpression.HAPPY), 12.4, previewReport, previewCheckup, GreetingType.DAY)
}

@Preview(widthDp = 1700, heightDp = 1184)
@Composable
private fun ParkedHungryWornShoesPreview() {
    MainScreen(DisplayMode.Parked, car(CharacterExpression.HUNGRY, setOf(CharacterMark.WORN_SHOES)), 12.4, previewReport, previewCheckup, GreetingType.DAY)
}

@Preview(widthDp = 1700, heightDp = 1184)
@Composable
private fun StartGreetingPreview() {
    val beforeTrip = ParkedReportBuilder.build(TripSummary(0.0, emptyList(), 50), VehicleProfile(42_300, emptyMap()), LocalDate.of(2026, 11, 1))
    MainScreen(DisplayMode.Parked, car(CharacterExpression.NORMAL), 0.0, beforeTrip, previewCheckup, GreetingType.MORNING)
}

@Preview(widthDp = 1700, heightDp = 1184)
@Composable
private fun ParkedCheckupPreview() {
    MainScreen(DisplayMode.Parked, car(CharacterExpression.NORMAL), 12.4, previewReport, previewCheckup, GreetingType.DAY, openCheckupAtStart = true)
}

@Preview(widthDp = 1700, heightDp = 1184)
@Composable
private fun EmergencyPreview() {
    MainScreen(DisplayMode.Emergency(setOf(EmergencyType.ENGINE)), car(CharacterExpression.NORMAL), 12.4, previewReport, previewCheckup, GreetingType.DAY)
}
