package app.stride.wallet.domain

import app.stride.wallet.model.CardFormat
import app.stride.wallet.model.Receipt
import app.stride.wallet.model.ReceiptItem
import app.stride.wallet.model.ReceiptStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandsAndImportTest {
    private fun r(company: String?, shop: String?, items: List<ReceiptItem> = emptyList()) =
        Receipt("x", "", ReceiptStatus.OK, company = company, shop = shop, items = items)

    private val nis = "Društvo Za Istraživanje, Proizvodnju, Preradu, Distribuciju I Promet Nafte I Naftnih Derivata I Istraživanje I Proizvodnju Prirodnog Gasa Naftna Industrija Srbije A.D. Novi Sad"

    @Test fun chains() {
        assertEquals("NIS", Brands.title(r(nis, "1037044-Bs Novi Sad 1")))
        assertEquals(Category.FUEL, Brands.category(r(nis, "1037044-Bs Novi Sad 1")))
        assertEquals("Lukoil", Brands.title(r("Društvo Sa Ograničenom Odgovornošću Za Promet Naftnih Derivata Lukoil Srbija Beograd", "1046451-Bs Kisačka")))
        assertEquals("OMV", Brands.title(r("Privredno Društvo Za Trgovinu I Usluge Omv Srbija Doo Beograd (Novi Beograd)", "1083555-Ogranak Bs Novi Sad 2")))
        assertEquals("MOL", Brands.title(r("Preduzeće Za Trgovinu Naftom I Gasom Mol Serbia Društvo Sa Ograničenom Odgovornošću Beograd", "1168465-Ses Višnjica")))
        assertEquals("Gigatron", Brands.title(r("Gigatron D.O.O,Export-Import", "1101464-Gigatron Prodavnica G29")))
        assertEquals(Category.TECH, Brands.category(r("Gigatron D.O.O,Export-Import", null)))
        assertEquals("Decathlon", Brands.title(r("Decathlon D.O.O.", "1212067-Decathlon Novi Sad")))
        assertEquals("Office Shoes", Brands.title(r("Office-Shoes Doo Za Proizvodnju I Trgovinu Subotica", null)))
        assertEquals("Lidl", Brands.title(r("Lidl Srbija Kd Nova Pazova", "1153143-Prodavnica Br. 0174")))
        assertEquals("JYSK", Brands.title(r("Jysk Doo Beograd, Beograd (Zemun)", null)))
        assertEquals("Dexy Co Kids", Brands.title(r("Dexy Co Kids Doo", "1027426-Dexy Co Kids Doo- Prodavnica 39")))
        // Not a known chain: a short company name, or the shop when the company name is long.
        assertEquals("Pekara Kod Mike", Brands.title(r("Pekara Kod Mike Pr", "1-Pekara")))
        assertEquals(Category.FOOD, Brands.category(r("Pekara Kod Mike Pr", "1-Pekara")))
        assertEquals("Wedding Centar Zlatara As Ns1", Brands.title(r("Viktor Sarka Pr Proizvodnja Nakita I Srodnih Predmeta Wedding Centar Zlatara As Novi Sad", "1058514-Wedding Centar Zlatara As Ns1")))
        assertEquals(Category.FUEL, Brands.category(r("Nepoznata Firma", "1-Bs Neka", listOf(ReceiptItem("EVRO DIZEL", 30.0, 200.0, 6000.0, "L")))))
        assertEquals("Fuel", Category.valueOf("FUEL").label)
        assertEquals(Category.HEALTH, Brands.category(r("x", null).copy(category = "HEALTH")))
        assertEquals("DC", Brands.initials("Dexy Co Kids"))
    }

    @Test fun cards() {
        assertEquals(CardFormat.QR_CODE, Brands.card("SuperKartica")!!.format)
        assertEquals(CardFormat.QR_CODE, Brands.card("Lidl Plus")!!.format)
        assertEquals(CardFormat.EAN_13, Brands.card("moj Maxi")!!.format)
        assertEquals("Laguna klub čitalaca", Brands.card("Laguna klub čitalaca")!!.name)
        assertNull(Brands.card("Neka kartica"))
    }

    private val csv = """
        Tip,Datum i vreme,Naziv kompanije,Ime prodajnog mesta,Ukupna cena,Grupa,Korisnik,ID Korisnika
        Fiskalni račun,23.05.2026. 18:45:05,Lidl Srbija Kd Nova Pazova,1153143-Prodavnica Br. 0174,17565.23,-,Petar Petrović,1
        Fiskalni račun,30.06.2025. 13:55:23,"Dr Techno Vb Društvo Sa Ograničenom Odgovornošću, Novi Sad",1275572-Panonija,153640.00,-,Petar Petrović,1
        Fiskalni račun,22.05.2026. 15:53:24,Preduzeće Za Trgovinu Naftom I Gasom Mol Serbia Društvo Sa Ograničenom Odgovornošću Beograd,1168465-Ses Višnjica,2999.22,Moja Firma,Petar Petrović,1
        Fiskalni račun,nije datum,X,Y,1.00,-,Petar Petrović,1
    """.trimIndent()

    @Test fun maniCsv() {
        assertTrue(ManiImport.looksLikeMani(csv))
        var n = 0
        val res = ManiImport.parse(csv, emptyList(), { "r${n++}" }, "2026-10-26T08:00:00Z")!!
        assertEquals(3, res.receipts.size)
        assertEquals(1, res.skipped)
        val lidl = res.receipts[0]
        assertEquals("2026-05-23T18:45:05", lidl.time)
        assertEquals(17565.23, lidl.total!!, 1e-9)
        assertEquals("1153143-Prodavnica Br. 0174", lidl.shop)
        assertNull(lidl.buyerTin)
        assertEquals("mani", lidl.source)
        assertEquals("Dr Techno Vb Društvo Sa Ograničenom Odgovornošću, Novi Sad", res.receipts[1].company)
        assertEquals("group:Moja Firma", res.receipts[2].buyerTin)
        assertEquals(listOf("Moja Firma"), res.companies.map { it.name })
        // Importing again adds nothing.
        val again = ManiImport.parse(csv, res.receipts, { "z" }, "now")!!
        assertEquals(0, again.receipts.size)
        assertEquals(3, again.duplicates)
        assertNull(ManiImport.parse("a,b\n1,2", emptyList(), { "q" }, "now"))
    }
}

class GroupingTest {
    private fun r(id: String, time: String, total: Double, company: String) =
        Receipt(id, "", ReceiptStatus.OK, company = company, total = total, time = time)

    @Test fun weeksShopsCategories() {
        val rs = listOf(
            r("a", "2026-10-08T10:10:03", 20.01, "Omv Srbija Doo"),
            r("b", "2026-10-12T20:58:39", 3001.50, "Naftna Industrija Srbije A.D. Novi Sad"),
            r("c", "2026-10-13T14:46:47", 20891.0, "Decathlon D.O.O."),
            r("d", "2026-10-19T08:56:24", 3001.50, "Naftna Industrija Srbije A.D. Novi Sad"),
            r("e", "2026-09-30T14:05:29", 8677.70, "Lukoil Srbija"),
        )
        val w = ReceiptStats.weeks(rs, java.time.YearMonth.of(2026, 10))
        assertEquals(listOf("01–07", "08–14", "15–21", "22–28", "29–31"), w.map { it.first })
        assertEquals(23912.51, w[1].second, 1e-6)
        assertEquals(3001.50, w[2].second, 1e-6)
        val oct = ReceiptStats.inMonth(rs, java.time.YearMonth.of(2026, 10))
        assertEquals(listOf("Decathlon", "NIS", "OMV"), ReceiptStats.byShop(oct).map { it.first })
        assertEquals(2, ReceiptStats.byShop(oct)[1].second)
        assertEquals(listOf(Category.SPORT, Category.FUEL), ReceiptStats.byCategory(oct).map { it.first })
        assertEquals(1, ReceiptStats.search(rs, "nis").count { it.id == "b" })
    }
}
