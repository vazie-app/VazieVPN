package app.vazie.vpn.widget

import app.vazie.vpn.core.designsystem.glance.VazieWidgetSizes
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** The sizes a launcher is told about, against the sizes the layouts were drawn for. */
class WidgetSizeDeclarationTest {

    @Test
    fun `no widget may be shrunk below the smallest layout it has`() {
        WIDGETS.forEach { (widget, smallest) ->
            listOf("xml", "xml-v31").forEach { folder ->
                val info = File("src/main/res/$folder/${widget}_info.xml").readText()
                val width = info.dp("minResizeWidth")
                val height = info.dp("minResizeHeight")
                assertTrue(
                    width >= smallest.width.value,
                    "$folder/$widget can be shrunk to ${width}dp wide, below its ${smallest.width}",
                )
                assertTrue(
                    height >= smallest.height.value,
                    "$folder/$widget can be shrunk to ${height}dp tall, below its ${smallest.height}",
                )
            }
        }
    }

    @Test
    fun `a widget asks for at least the size its layout was drawn for`() {
        WIDGETS.forEach { (widget, smallest) ->
            val info = File("src/main/res/xml/${widget}_info.xml").readText()
            assertTrue(info.dp("minWidth") >= smallest.width.value, "$widget minWidth")
            assertTrue(info.dp("minHeight") >= smallest.height.value, "$widget minHeight")
        }
    }

    @Test
    fun `nothing polls, because state is pushed`() {
        // A widget that woke the device every half hour to redraw the same "Not protected" would
        // spend battery to learn nothing. State arrives from VazieWidgetPublisher.
        WIDGETS.keys.forEach { widget ->
            listOf("xml", "xml-v31").forEach { folder ->
                val info = File("src/main/res/$folder/${widget}_info.xml").readText()
                assertTrue(
                    info.contains("android:updatePeriodMillis=\"0\""),
                    "$folder/$widget polls on a timer",
                )
            }
        }
    }

    /** A widget has a composition for the largest cell its own metadata lets somebody drag it to. */
    @Test
    fun `a widget is drawn for the largest cell its metadata offers`() {
        LARGEST.forEach { (widget, largest) ->
            val info = File("src/main/res/xml-v31/${widget}_info.xml").readText()
            listOf(
                "maxResizeWidth" to largest.width.value,
                "maxResizeHeight" to largest.height.value,
            ).forEach { (attribute, drawn) ->
                val permitted = info.dp(attribute)
                assertTrue(
                    drawn <= permitted,
                    "$widget is drawn for $drawn dp, which its $attribute of $permitted dp forbids",
                )
                assertTrue(
                    drawn >= permitted * COVERAGE,
                    "$widget lets a launcher make it $permitted dp and has nothing drawn above " +
                        "$drawn dp, so that cell gets a smaller widget stretched across it",
                )
            }
        }
    }

    private fun String.dp(attribute: String): Float =
        Regex("""android:$attribute="(\d+)dp"""").find(this)
            ?.groupValues
            ?.get(1)
            ?.toFloat()
            ?: error("$attribute is not declared")

    private companion object {
        /** Each widget against the smallest size its `SizeMode.Responsive` set prepares a layout for. */
        val WIDGETS = mapOf(
            "widget_quick_connect" to VazieWidgetSizes.squareMin,
            "widget_dashboard" to VazieWidgetSizes.wideMin,
        )

        /** Each widget against the largest size its `SizeMode.Responsive` set prepares a layout for. */
        val LARGEST = mapOf(
            "widget_quick_connect" to VazieWidgetSizes.squareRoomy,
            "widget_dashboard" to VazieWidgetSizes.wideRoomy,
        )

        /** How close the largest declared layout has to come to the largest permitted cell. */
        const val COVERAGE = 0.9f
    }
}
