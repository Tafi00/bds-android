package vn.futaland.app.features.properties

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.futaland.app.core.network.JSONValue

/**
 * Locks the shared project-website mapping (bds-clone project-website.ts) and
 * the relative image resolution that thumbnails rely on.
 */
class PropertyFormattersTest {

    private fun access(json: String) = JSONValue.parse(json)

    @Test
    fun `explicit websiteUrl wins over name matching`() {
        assertEquals(
            "https://example.com",
            PropertyFormatters.projectWebsiteUrl(access("""{"name":"FUTA Kim An","websiteUrl":"https://example.com"}""")),
        )
        assertEquals(
            "https://futakiman.vn/",
            PropertyFormatters.projectWebsiteUrl(access("""{"name":"FUTA Kim An","websiteUrl":"  "}""")),
        )
    }

    @Test
    fun `project names map to their marketing sites`() {
        assertEquals(
            "https://futakiman.vn/",
            PropertyFormatters.projectWebsiteUrl(access("""{"displayName":"FUTA Kim An"}""")),
        )
        assertEquals(
            "https://futakimphat.com.vn/",
            PropertyFormatters.projectWebsiteUrl(access("""{"name":"FUTA Kim Phát"}""")),
        )
        assertEquals(
            "https://futaresidence.vn/",
            PropertyFormatters.projectWebsiteUrl(access("""{"name":"Đà Nẵng Times Square"}""")),
        )
        assertEquals(
            "https://futaresidence.vn/",
            PropertyFormatters.projectWebsiteUrl(access("""{"id":"dnts-01"}""")),
        )
        assertEquals(
            "https://www.futaland.vn",
            PropertyFormatters.projectWebsiteUrl(access("""{"name":"Hilton Mũi Né"}""")),
        )
        assertEquals(
            "https://www.futaland.vn",
            PropertyFormatters.projectWebsiteUrl(access("""{}""")),
        )
    }

    @Test
    fun `relative image paths gain the public web origin`() {
        assertEquals(
            "https://bds.futaland.vn/images/projects/a.png",
            PropertyFormatters.resolveImageUrl("/images/projects/a.png"),
        )
        assertEquals(
            "https://cdn.example.com/x.png",
            PropertyFormatters.resolveImageUrl("https://cdn.example.com/x.png"),
        )
        assertEquals("", PropertyFormatters.resolveImageUrl(""))
        assertEquals("", PropertyFormatters.resolveImageUrl(" null "))
    }

    @Test
    fun `hilton project falls back to its own banner`() {
        assertEquals(
            "https://bds.futaland.vn/images/figma-data/projects/exact/hilton-mui-ne.png",
            PropertyFormatters.resolveImage(access("""{"projectName":"Hilton Mũi Né"}""")),
        )
        assertEquals(
            "https://bds.futaland.vn/images/figma-data/projects/exact/times-square.png",
            PropertyFormatters.resolveImage(access("""{"projectName":"Times Square"}""")),
        )
    }

    @Test
    fun `project banner resolves relative mobile banner first`() {
        assertEquals(
            "https://bds.futaland.vn/images/m.png",
            PropertyFormatters.resolveProjectBanner(
                access("""{"bannerImageMobile":"/images/m.png","bannerImage":"/images/b.png"}"""),
            ),
        )
        assertEquals(
            "https://bds.futaland.vn/images/b.png",
            PropertyFormatters.resolveProjectBanner(access("""{"bannerImage":"/images/b.png"}""")),
        )
    }
}
