package app.stride.wallet.domain

import app.stride.wallet.model.CardFormat
import java.util.Locale

/** The motif drawn on a card face, after the program's own card (drawn by Hive, no artwork copied). */
enum class CardStyle { PLAIN, DOTS, STRIPES, CROSS, X, WAVES, RINGS, GRID, SHINE }

/**
 * A loyalty program: name, website (for the logo), the two colors of its card, the motif on it, whether the card
 * is light (dark text) and, where known, the code its tills read.
 */
data class CardBrand(
    val name: String,
    val domain: String?,
    val color: Long,
    val color2: Long = color,
    val style: CardStyle = CardStyle.PLAIN,
    val light: Boolean = false,
    val format: CardFormat? = null,
)

/**
 * Serbian loyalty programs (pure, unit tested): the ones Mani knows, each with a card face in its own colors and
 * motif. Matched on the card's name; the more specific patterns come first.
 */
object CardBrands {
    private class Rule(pattern: String, val brand: CardBrand) { val re = Regex(pattern) }

    private fun c(
        pattern: String, name: String, domain: String?, color: Long, color2: Long = color,
        style: CardStyle = CardStyle.PLAIN, light: Boolean = false, format: CardFormat? = null,
    ) = Rule(pattern, CardBrand(name, domain, color, color2, style, light, format))

    private val RULES = listOf(
        // Groceries and drugstores
        c("""SUPER ?KARTICA""", "SuperKartica", "superkartica.rs", 0xFF1D3FD6, 0xFF0B1F8F, CardStyle.GRID, format = CardFormat.QR_CODE),
        c("""LIDL""", "Lidl Plus", "lidl.rs", 0xFFF4F6FA, 0xFFDCE6F5, CardStyle.DOTS, light = true, format = CardFormat.QR_CODE),
        c("""DR\.? ?MAX""", "Dr. Max", "drmax.rs", 0xFFE2231A, 0xFFB5121B, CardStyle.WAVES),
        c("""\bMAXI\b""", "Moj Maxi", "maxi.rs", 0xFFF5F5F5, 0xFFE9E9E9, CardStyle.PLAIN, light = true, format = CardFormat.EAN_13),
        c("""\bDIS\b""", "DIS Naša kartica", "dis.rs", 0xFFE30613, 0xFF9E0B14, CardStyle.STRIPES),
        c("""\bIDEA\b""", "Idea", "idea.rs", 0xFFE30613, 0xFFB0101A, CardStyle.STRIPES),
        c("""\bVERO\b""", "Vero klub", "vero.rs", 0xFFE30613, 0xFF0E8A3E, CardStyle.STRIPES),
        c("""UNIVEREXPORT""", "Univerexport bonus kartica", "univerexport.rs", 0xFFF2F7EC, 0xFFD9ECC6, CardStyle.DOTS, light = true),
        c("""METRO""", "Metro klub prijatelja", "metro.rs", 0xFFFFE600, 0xFF003D7C, CardStyle.STRIPES),
        c("""\bDM\b|MOJ ?DM""", "moj dm", "dm.rs", 0xFFFFE9A6, 0xFFC9E7F5, CardStyle.WAVES, light = true),
        c("""LILLY""", "Lilly Drogerie", "lilly.rs", 0xFF1EA0E6, 0xFF0B79C4, CardStyle.DOTS),
        c("""BENU""", "Benu Plus", "benu.rs", 0xFFE5007E, 0xFFB0005F, CardStyle.CROSS),
        c("""CVEJI[CĆ]""", "Cvejić Apoteka", "apotekacvejic.rs", 0xFF00843D, 0xFF005A2A, CardStyle.CROSS),
        c("""FILLY""", "Apoteka Filly Farm", "fillyfarm.rs", 0xFF5DB534, 0xFF2E8B1E, CardStyle.CROSS),
        c("""GALEN""", "Galen Pharm", "galenpharm.rs", 0xFF5B3E91, 0xFF3A2766, CardStyle.CROSS),
        c("""JANKOVI[CĆ]""", "Apoteka Janković", "apotekajankovic.rs", 0xFFF2B33D, 0xFFE08E12, CardStyle.CROSS),
        c("""BODY ?SHOP""", "The Body Shop Love Your Body", "thebodyshop.com", 0xFF004236, 0xFF0B2620, CardStyle.WAVES),
        c("""L'?OCCITANE""", "L'Occitane", "loccitane.com", 0xFFE3A21A, 0xFF9C6B0E, CardStyle.SHINE),
        c("""GOLDEN BEAUTY""", "Golden Beauty Shop", null, 0xFFF4C7C3, 0xFFE9A9A3, CardStyle.SHINE, light = true),
        // Books, toys, games
        c("""VULKAN""", "Vulkan klub", "knjizare-vulkan.rs", 0xFFE2231A, 0xFFA3141B, CardStyle.X),
        c("""LAGUNA""", "Laguna klub čitalaca", "laguna.rs", 0xFFC8102E, 0xFF8E0B20, CardStyle.WAVES),
        c("""CICIBAN""", "Ciciban klub", "ciciban.rs", 0xFFE31E3C, 0xFFB01430, CardStyle.DOTS),
        c("""GAMES""", "GameS Privilege", "games.rs", 0xFFF7C600, 0xFF1F5FD6, CardStyle.GRID),
        c("""GAME ?CENTAR""", "Game Centar Klan", "gamecentar.rs", 0xFF2A2A2A, 0xFFF08A1C, CardStyle.GRID),
        c("""CRAFT""", "Crafter Craftica", "crafter.rs", 0xFFF08A1C, 0xFFD45F0B, CardStyle.STRIPES),
        c("""ĐAK|\bDAK\b|\bSPOT\b""", "Đak & Spot", null, 0xFFD7262E, 0xFF5A0E12, CardStyle.SHINE),
        // Clothes, shoes, sport
        c("""SPORT ?& ?BONUS|SPORTANDBONUS""", "Sport&Bonus", null, 0xFF3A3A3A, 0xFF161616, CardStyle.SHINE),
        c("""SPORT ?VISION""", "Sport Vision", "sportvision.rs", 0xFF2A2A2A, 0xFF111111, CardStyle.PLAIN),
        c("""SPORTINA""", "Sportina club", "sportina.rs", 0xFFC9B8E8, 0xFF7B5EA7, CardStyle.GRID),
        c("""TREF""", "Tref Sport VIP klub", "trefsport.com", 0xFF3A3A3A, 0xFF1C1C1C, CardStyle.STRIPES),
        c("""UNDER ARMOUR|KVANTUM""", "Under Armour Kvantum Plus", "underarmour.com", 0xFF2A2A2A, 0xFF0E0E0E, CardStyle.GRID),
        c("""OFFICE ?SHOES""", "Office Shoes", "officeshoes.rs", 0xFFD8B46A, 0xFFA7843E, CardStyle.SHINE, light = true),
        c("""SHOESTAR""", "Shoestar", "shoestar.rs", 0xFF1F1F1F, 0xFF0A0A0A, CardStyle.SHINE),
        c("""PLANIKA""", "Planika club", "planika.rs", 0xFFD9DCE1, 0xFFAEB3BA, CardStyle.SHINE, light = true),
        c("""LINDEX""", "Lindex More", "lindex.com", 0xFF111111, 0xFF000000, CardStyle.RINGS),
        c("""TERRANOVA""", "Terranova WE", "terranovastyle.com", 0xFF1A1A1A, 0xFF3A2A1A, CardStyle.PLAIN),
        c("""H ?& ?M""", "H&M Member", "hm.com", 0xFFF2EDE4, 0xFFE4DCCD, CardStyle.PLAIN, light = true),
        c("""PEEK""", "Peek&Cloppenburg", "peek-cloppenburg.rs", 0xFF0A2A5E, 0xFF051737, CardStyle.PLAIN),
        c("""\bOVS\b""", "OVS", "ovsfashion.com", 0xFF0B1B3A, 0xFF050D1E, CardStyle.PLAIN),
        c("""ORSAY""", "Orsay club", "orsay.com", 0xFF111111, 0xFF2A2A2A, CardStyle.PLAIN),
        c("""P\.? ?S\.? ?FASHION""", "P.S. Fashion Premium Club", "psfashion.rs", 0xFF2A2A2A, 0xFF141414, CardStyle.SHINE),
        c("""EXTREME INTIMO""", "Extreme Intimo", "extremeintimo.com", 0xFF2A2323, 0xFF140F0F, CardStyle.SHINE),
        c("""MANUAL ?CO""", "Manual Co", null, 0xFF8B4A2B, 0xFF4A2414, CardStyle.RINGS),
        // Home, tech, optics
        c("""GIGATRON""", "Gigatron baš tvoja", "gigatron.rs", 0xFF141414, 0xFFF5C400, CardStyle.SHINE),
        c("""IKEA""", "IKEA Family", "ikea.com", 0xFF0058A3, 0xFFFFDB00, CardStyle.STRIPES),
        c("""EMMEZETA""", "Emmezeta klub", "emmezeta.rs", 0xFF8B1A7E, 0xFF5A0F52, CardStyle.RINGS),
        c("""SIMPO""", "Simpo", "simpo.rs", 0xFFA51E22, 0xFF6E1215, CardStyle.STRIPES),
        c("""LESNINA|XXXL""", "Lesnina XXXL", "lesnina.rs", 0xFFE30613, 0xFFFFE500, CardStyle.STRIPES),
        c("""WOBY""", "Woby kartica", "wobyhaus.co.rs", 0xFF0B5DB5, 0xFF073C78, CardStyle.GRID),
        c("""OKOV""", "OKOV posto kartica", "okov.rs", 0xFFF5F7FA, 0xFFDDE6F2, CardStyle.WAVES, light = true),
        c("""FORMA IDEALE""", "Forma Ideale", "formaideale.rs", 0xFF1F4CA1, 0xFF12306B, CardStyle.STRIPES),
        c("""OPTIPLAZA""", "Optiplaza klub", "optiplaza.rs", 0xFF2A2A2A, 0xFF161616, CardStyle.PLAIN),
        c("""SANI ?OPTIK""", "Sani Optik", "sanioptik.rs", 0xFF7A8288, 0xFF545A5F, CardStyle.RINGS),
        // Fuel
        c("""\bMOL\b""", "MOL Move", "mol.rs", 0xFFF5F5F5, 0xFFC9A227, CardStyle.SHINE, light = true),
        c("""OMV""", "OMV MyStation", "omv.rs", 0xFF0A3D8F, 0xFF06275C, CardStyle.PLAIN),
        c("""LUKOIL""", "Lukoil", "lukoil.rs", 0xFFF5F5F5, 0xFFE2231A, CardStyle.X, light = true),
        c("""KNEZ ?PETROL""", "Knez Petrol", "knezpetrol.com", 0xFFD7262E, 0xFFEDEDED, CardStyle.WAVES),
        c("""\bPETROL\b""", "Petrol zlatna kartica", "petrol.rs", 0xFFE9CF7A, 0xFFC09A3E, CardStyle.SHINE, light = true),
        c("""\bNIS\b|SA NAMA""", "NIS Sa nama", "nis.rs", 0xFF0057A8, 0xFFE2231A, CardStyle.WAVES),
        // Malls, food, the rest
        c("""ADA ?MALL""", "Ada Mall Loyalty Club", "adamall.rs", 0xFF6A2C91, 0xFF1FB5A8, CardStyle.WAVES),
        c("""GALERIJA""", "Galerija Beograd", "galerijabelgrade.com", 0xFFF5F5F5, 0xFFE3E3E3, CardStyle.RINGS, light = true),
        c("""MC ?DONALD""", "My McDonald's Rewards", "mcdonalds.rs", 0xFFFFFFFF, 0xFFFFC72C, CardStyle.PLAIN, light = true),
        c("""SRBIJA ?VOZ|SRB ?PLUS""", "Srbija Voz SRB PLUS", "srbijavoz.rs", 0xFF00539F, 0xFFE2231A, CardStyle.STRIPES),
        c("""AKSA""", "Aksa", null, 0xFF1EA0E6, 0xFFE5007E, CardStyle.DOTS),
        c("""BAZEN""", "Kartica za bazen", null, 0xFF1E3F8B, 0xFF4FA3E0, CardStyle.WAVES),
        c("""PENZION""", "Penzionerska kartica", null, 0xFFF0F2F5, 0xFF1E4C9A, CardStyle.STRIPES, light = true),
        c("""ZDRAVSTVEN""", "Kartica zdravstvenog osiguranja", null, 0xFFE4E8DF, 0xFFBFC9B5, CardStyle.WAVES, light = true),
    )

    private fun up(s: String) = (FiscalParse.latin(s) ?: s).uppercase(Locale.ROOT)

    /** The program a card's name points at, or null. */
    fun find(name: String): CardBrand? {
        val t = up(name)
        if (t.isBlank()) return null
        return RULES.firstOrNull { it.re.containsMatchIn(t) }?.brand
    }

    /** Every program, for the "pick your card" list when adding one. */
    val all: List<CardBrand> get() = RULES.map { it.brand }
}
