# 차 색상 팔레트 (car.py와 garage 내보내기가 같이 읽는다)
# 값: (한국어 이름, sRGB hex, 마감). 마감 gloss/metallic/pearl/matte 는 car.py에서 재질 수치로 바뀐다.
# 현대차 양산 색 계열을 바탕으로 한 기본 세트 + 확장 팔레트. 상표 색 이름은 쓰지 않고 색을 설명하는 이름을 쓴다.
PALETTE = {
    # ---- 기본 ----
    "white":        ("화이트",       "F3F4F2", "gloss"),
    "cream_pearl":  ("크림 화이트 펄",  "EFE8DA", "pearl"),
    "silver":       ("실버",         "B9BCC0", "metallic"),
    "cyber_gray":   ("라이트 그레이",     "6E7378", "metallic"),
    "amazon_gray":  ("딥 그레이",         "585D5C", "metallic"),
    "eco_gray":     ("그레이 펄",    "8E9499", "pearl"),
    "black_pearl":  ("블랙 펄",    "0E1013", "pearl"),
    "lucid_blue":   ("블루 펄",    "3F6FB8", "pearl"),
    "teal_green":   ("틸 그린",    "1F5F62", "pearl"),
    "mirage_green": ("세이지 그린",       "4F6A4A", "metallic"),
    "ultimate_red": ("레드",       "A3141C", "metallic"),
    "gravity_gold": ("골드 매트","8F8470", "matte"),
    "nocturne_gray":("다크 그레이 매트",  "4A4D52", "matte"),
    "forest":       ("딥 포레스트",       "1E3A2E", "pearl"),
    # ---- 확장 팔레트 ----
    "sunset_orange":("선셋 오렌지",       "D9622B", "gloss"),
    "sun_yellow":   ("선샤인 옐로",       "E8C23A", "gloss"),
    "sky_blue":     ("스카이 블루",       "86B8E0", "gloss"),
    "mint":         ("민트",              "7FC8B0", "gloss"),
    "lavender":     ("라벤더",            "A89CC8", "pearl"),
    "rose":         ("로즈 핑크",         "C8788C", "pearl"),
    "burgundy":     ("버건디",            "5A1A2C", "metallic"),
    "olive":        ("올리브",            "6F7A3E", "matte"),
    "sand":         ("샌드 베이지",       "CBB89A", "gloss"),
    "copper":       ("코퍼 메탈릭",       "9A5A3A", "metallic"),
    "deep_purple":  ("딥 퍼플",           "3B2A5E", "pearl"),
    "ivory_matte":  ("아이보리 매트",     "E4DDCB", "matte"),
    "navy":         ("미드나이트 네이비", "142040", "pearl"),
    "graphite":     ("그라파이트",        "2A2D32", "gloss"),
}
# 마감별 재질: (metallic, roughness, coat, coat_roughness)
FINISH = {
    "gloss":    (0.10, 0.40, 1.0, 0.03),
    "metallic": (0.55, 0.32, 1.0, 0.03),
    "pearl":    (0.25, 0.30, 1.0, 0.02),
    "matte":    (0.20, 0.70, 0.0, 0.30),
}
def srgb_to_linear(hexstr):
    out = []
    for i in (0, 2, 4):
        c = int(hexstr[i:i + 2], 16) / 255.0
        out.append(c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4)
    return tuple(out)
