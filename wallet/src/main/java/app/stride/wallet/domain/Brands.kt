package app.stride.wallet.domain

import app.stride.wallet.model.CardFormat
import app.stride.wallet.model.Receipt
import java.util.Locale

/** What a receipt was for; guessed from the shop (or fuel on it), changeable per receipt. Names are stored. */
enum class Category(val label: String, val color: Long) {
    FUEL("Fuel", 0xFFFF9F0A),
    GROCERIES("Groceries", 0xFF30D158),
    FOOD("Food & drinks", 0xFFFF6B6B),
    TECH("Tech", 0xFF0A84FF),
    HOME("Home & garden", 0xFFAC8E68),
    CLOTHES("Clothes & shoes", 0xFFBF5AF2),
    SPORT("Sport", 0xFF64D2FF),
    HEALTH("Pharmacy & beauty", 0xFFFF375F),
    KIDS("Kids", 0xFFFFD60A),
    PETS("Pets", 0xFF66D4CF),
    BOOKS("Books", 0xFF5E5CE6),
    OTHER("Other", 0xFF8E8E93),
}

/** A shop chain: the short name people use, its website (for the logo), its color and what it sells. */
data class Brand(val name: String, val domain: String?, val color: Long, val category: Category)

/** A loyalty card program: its color, logo and the code its tills read. */
data class CardBrand(val name: String, val domain: String?, val color: Long, val format: CardFormat?)

/**
 * Serbian shop chains and loyalty programs (pure, unit tested). Receipts carry the company's legal name
 * ("Društvo za istraživanje … Naftna industrija Srbije A.D. Novi Sad"); this turns it into "NIS" with a logo,
 * a color and a category, like Mani does.
 */
object Brands {
    private class Rule(pattern: String, val brand: Brand) { val re = Regex(pattern) }

    private fun b(pattern: String, name: String, domain: String?, color: Long, c: Category) = Rule(pattern, Brand(name, domain, color, c))

    private val RULES = listOf(
        b("""NAFTNA INDUSTRIJA SRBIJE|\bNIS\b|NIS PETROL""", "NIS", "nis.rs", 0xFF0057A8, Category.FUEL),
        b("""LUKOIL""", "Lukoil", "lukoil.rs", 0xFFE2231A, Category.FUEL),
        b("""\bOMV\b""", "OMV", "omv.rs", 0xFF0A3D8F, Category.FUEL),
        b("""\bMOL\b""", "MOL", "mol.rs", 0xFF1E8A3C, Category.FUEL),
        b("""\bCORAL\b""", "Coral", null, 0xFFE5007D, Category.FUEL),
        b("""GAZPROM""", "Gazprom", "gazprom-petroleum.rs", 0xFF0079C2, Category.FUEL),
        b("""\bEKO SERBIA\b|\bEKO\b""", "EKO", "ekoserbia.com", 0xFFFFC20E, Category.FUEL),
        b("""\bLIDL\b""", "Lidl", "lidl.rs", 0xFF0050AA, Category.GROCERIES),
        b("""\bMAXI\b|DELHAIZE""", "Maxi", "maxi.rs", 0xFFE30613, Category.GROCERIES),
        b("""\bIDEA\b|MERCATOR""", "Idea", "idea.rs", 0xFFE30613, Category.GROCERIES),
        b("""\bRODA\b""", "Roda", "roda.rs", 0xFF00843D, Category.GROCERIES),
        b("""UNIVEREXPORT""", "Univerexport", "univerexport.rs", 0xFF00843D, Category.GROCERIES),
        b("""\bTEMPO\b""", "Tempo", null, 0xFFE30613, Category.GROCERIES),
        b("""METRO CASH|\bMETRO\b""", "Metro", "metro.rs", 0xFF003D7C, Category.GROCERIES),
        b("""\bAMAN\b""", "Aman", "aman.co.rs", 0xFFE30613, Category.GROCERIES),
        b("""GIGATRON""", "Gigatron", "gigatron.rs", 0xFFE30613, Category.TECH),
        b("""TEHNOMANIJA""", "Tehnomanija", "tehnomanija.rs", 0xFFE30613, Category.TECH),
        b("""DR TECHNO""", "Dr Techno", "drtechno.rs", 0xFF0A84FF, Category.TECH),
        b("""WIN ?WIN""", "WinWin", "winwin.rs", 0xFFE30613, Category.TECH),
        b("""\bEMMI\b""", "Emmi", "emmi.rs", 0xFF0A84FF, Category.TECH),
        b("""\bJYSK\b""", "JYSK", "jysk.rs", 0xFF143C8A, Category.HOME),
        b("""\bOKOV\b""", "Okov", "okov.rs", 0xFFE30613, Category.HOME),
        b("""\bIKEA\b""", "IKEA", "ikea.com", 0xFF0058A3, Category.HOME),
        b("""KOLOSEUM""", "Koloseum", null, 0xFFAC8E68, Category.HOME),
        b("""DECATHLON""", "Decathlon", "decathlon.rs", 0xFF0082C3, Category.SPORT),
        b("""SPORT ?VISION""", "Sport Vision", "sportvision.rs", 0xFF111111, Category.SPORT),
        b("""\bBUZZ\b""", "Buzz", "buzzsneakers.rs", 0xFF111111, Category.SPORT),
        b("""SPORTINA""", "Sportina", "sportina.rs", 0xFF7B5EA7, Category.CLOTHES),
        b("""OFFICE[ -]?SHOES""", "Office Shoes", "officeshoes.rs", 0xFF111111, Category.CLOTHES),
        b("""DEICHMANN""", "Deichmann", "deichmann.com", 0xFF00843D, Category.CLOTHES),
        b("""\bZARA\b|INDITEX""", "Zara", "zara.com", 0xFF111111, Category.CLOTHES),
        b("""H ?& ?M\b""", "H&M", "hm.com", 0xFFE30613, Category.CLOTHES),
        b("""DEXY CO""", "Dexy Co Kids", "dexy.co.rs", 0xFF66D4CF, Category.KIDS),
        b("""\bDM\b|DM-DROGERIE""", "dm", "dm.rs", 0xFF223E8F, Category.HEALTH),
        b("""\bLILLY\b""", "Lilly", "lilly.rs", 0xFFE6007E, Category.HEALTH),
        b("""\bBENU\b""", "Benu", "benu.rs", 0xFF00843D, Category.HEALTH),
        b("""JANKOVI[CĆ]""", "Apoteka Janković", "apotekajankovic.rs", 0xFF00843D, Category.HEALTH),
        b("""PET CENTAR""", "Pet Centar", "pet-centar.rs", 0xFFE30613, Category.PETS),
        b("""PREMIUM PET""", "Premium Pet", null, 0xFF8B5A3C, Category.PETS),
        b("""\bLAGUNA\b""", "Laguna", "laguna.rs", 0xFFC8102E, Category.BOOKS),
        b("""VULKAN""", "Vulkan", "knjizare-vulkan.rs", 0xFFE2231A, Category.BOOKS),
        b("""\bDELFI\b""", "Delfi", "delfi.rs", 0xFF0A84FF, Category.BOOKS),
        b("""MC ?DONALD""", "McDonald's", "mcdonalds.rs", 0xFFFFC72C, Category.FOOD),
        b("""\bKFC\b""", "KFC", "kfc.rs", 0xFFE4002B, Category.FOOD),
    )

    /** Words that point at a category when the chain isn't known. */
    private val WORDS = listOf(
        Regex("""^BS |BENZINSK|PUMPA|\bSES\b""") to Category.FUEL,
        Regex("""APOTEK|DROGERI|PARFIM""") to Category.HEALTH,
        Regex("""PEKAR|RESTORAN|KAFE|CAFF?E|PICERIJ|GRIL|BAR\b|POSLASTI""") to Category.FOOD,
        Regex("""MARKET|SUPERMARKET|HIPERMARKET|MESAR|PIJAC""") to Category.GROCERIES,
        Regex("""KNJI[ZŽ]AR""") to Category.BOOKS,
        Regex("""PET SHOP|VETERIN""") to Category.PETS,
        Regex("""ZLATAR|NAKIT""") to Category.OTHER,
    )

    private fun up(s: String?) = (FiscalParse.latin(s) ?: "").uppercase(Locale.ROOT)

    /** The chain behind a company / shop name, or null. */
    fun find(text: String?): Brand? {
        val t = up(text)
        if (t.isBlank()) return null
        return RULES.firstOrNull { it.re.containsMatchIn(t) }?.brand
    }

    fun of(r: Receipt): Brand? = find(r.company) ?: find(r.shop)

    /** The category: the user's choice, else the chain's, fuel on the receipt, or a telling word in the names. */
    fun category(r: Receipt): Category {
        r.category?.let { c -> Category.entries.firstOrNull { it.name == c }?.let { return it } }
        of(r)?.let { return it.category }
        if (ReceiptStats.fuel(r) != null) return Category.FUEL
        val names = listOf(up(r.company), up(shopName(r.shop)))
        return WORDS.firstOrNull { (re, _) -> names.any { re.containsMatchIn(it) } }?.second ?: Category.OTHER
    }

    /** "1037044-Bs Novi Sad 1" → "Bs Novi Sad 1" (the tax office's shop code dropped). */
    fun shopName(shop: String?): String? = shop?.replace(Regex("""^\d+\s*-\s*"""), "")?.trim()?.ifEmpty { null }

    private val LEGAL = Regex(
        """\b(D\.?\s?O\.?\s?O\.?|A\.?\s?D\.?|K\.?\s?D\.?|PR|EXPORT-IMPORT|DRU[ŠS]TVO SA OGRANI[ČC]ENOM ODGOVORNO[ŠS][ĆC]U)\b\.?""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A readable name for a company that isn't a known chain: the part before the first comma, legal forms
     * dropped ("Dexy Co Kids Doo" → "Dexy Co Kids"). Long "Društvo za …" names fall back to the shop.
     */
    fun shortCompany(company: String?, shop: String?): String? {
        val c = company?.substringBefore(',')?.replace(LEGAL, " ")?.replace(Regex("""\s+"""), " ")?.trim()?.trim('-', ' ')
        val long = c == null || c.length > 32 || Regex("""^(DRU[ŠS]TVO|PREDUZE[ĆC]E|PRIVREDNO)""", RegexOption.IGNORE_CASE).containsMatchIn(c)
        return if (!long) c?.ifEmpty { null } else shopName(shop) ?: c
    }

    /** What to call a receipt in lists: the chain ("NIS"), else a short company name, else the shop. */
    fun title(r: Receipt): String = of(r)?.name ?: shortCompany(r.company, r.shop) ?: shopName(r.shop) ?: "Receipt"

    /** Two letters for a logo-less shop: "Dexy Co Kids" → "DC". */
    fun initials(name: String): String =
        name.split(' ', '-', '&').filter { it.isNotBlank() && it.first().isLetterOrDigit() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

    /** A logo image for a website (Google's favicon service, no key). */
    fun logoUrl(domain: String): String = "https://www.google.com/s2/favicons?domain=$domain&sz=128"

    // ---------------------------------------------------------------- loyalty cards

    private class CardRule(pattern: String, val brand: CardBrand) { val re = Regex(pattern) }

    private val CARDS = listOf(
        CardRule("""SUPER ?KARTICA""", CardBrand("SuperKartica", "superkartica.rs", 0xFF1D3FD6, CardFormat.QR_CODE)),
        CardRule("""LIDL""", CardBrand("Lidl Plus", "lidl.rs", 0xFF0050AA, CardFormat.QR_CODE)),
        CardRule("""MAXI""", CardBrand("Moj Maxi", "maxi.rs", 0xFFE30613, CardFormat.EAN_13)),
        CardRule("""VULKAN""", CardBrand("Vulkan klub", "knjizare-vulkan.rs", 0xFFE2231A, null)),
        CardRule("""LAGUNA""", CardBrand("Laguna klub čitalaca", "laguna.rs", 0xFFC8102E, null)),
        CardRule("""METRO""", CardBrand("Metro", "metro.rs", 0xFFFFD60A, null)),
        CardRule("""PREMIUM PET""", CardBrand("Premium Pet", null, 0xFF8B5A3C, null)),
        CardRule("""PET CENTAR""", CardBrand("Pet Centar", "pet-centar.rs", 0xFFE30613, null)),
        CardRule("""JANKOVI[CĆ]""", CardBrand("Apoteka Janković", "apotekajankovic.rs", 0xFFF2B33D, null)),
        CardRule("""SPORTINA""", CardBrand("Sportina", "sportina.rs", 0xFF7B5EA7, null)),
        CardRule("""\bDM\b|ACTIVE BEAUTY""", CardBrand("dm", "dm.rs", 0xFF223E8F, null)),
        CardRule("""LILLY""", CardBrand("Lilly", "lilly.rs", 0xFFE6007E, null)),
        CardRule("""\bNIS\b|SA NAMA""", CardBrand("NIS Sa nama", "nis.rs", 0xFF0057A8, null)),
        CardRule("""\bIDEA\b""", CardBrand("Idea", "idea.rs", 0xFFE30613, null)),
        CardRule("""UNIVEREXPORT""", CardBrand("Univerexport", "univerexport.rs", 0xFF00843D, null)),
        CardRule("""DECATHLON""", CardBrand("Decathlon", "decathlon.rs", 0xFF0082C3, null)),
        CardRule("""GIGATRON""", CardBrand("Gigatron", "gigatron.rs", 0xFFE30613, null)),
        CardRule("""SPORT ?VISION""", CardBrand("Sport Vision", "sportvision.rs", 0xFF111111, null)),
        CardRule("""\bIKEA\b""", CardBrand("IKEA Family", "ikea.com", 0xFF0058A3, null)),
        CardRule("""LUKOIL""", CardBrand("Lukoil", "lukoil.rs", 0xFFE2231A, null)),
        CardRule("""\bOMV\b""", CardBrand("OMV", "omv.rs", 0xFF0A3D8F, null)),
        CardRule("""\bMOL\b""", CardBrand("MOL Move", "mol.rs", 0xFF1E8A3C, null)),
        CardRule("""BENU""", CardBrand("Benu", "benu.rs", 0xFF00843D, null)),
    )

    /** The loyalty program a card's name points at, or null. */
    fun card(name: String): CardBrand? {
        val t = up(name)
        if (t.isBlank()) return null
        return CARDS.firstOrNull { it.re.containsMatchIn(t) }?.brand
    }
}
